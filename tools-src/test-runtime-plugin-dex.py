#!/usr/bin/env python3
"""Link a desktop-compiled plugin against the exact APK using its compiler assets."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


REPO = Path(__file__).resolve().parent.parent
SHADOW_SOURCES = REPO / "android-awt" / "src" / "main" / "java"
SHADOW_ROOTS = ("java", "javax", "sun", "com/sun")
RETARGET_SOURCES = REPO / "android" / "src" / "androidMain" / "java" / "net" / "runelite" / "mp" / "compat"


def gradle_jar(group, artifact):
    """The newest cached Gradle jar of an artifact (no sources/javadoc), as the app resolved it."""
    root = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / group / artifact
    def version_key(path):
        return [int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", path.parent.parent.name)]
    jars = sorted((jar for jar in root.glob(f"*/*/{artifact}-*.jar")
                   if not jar.name.endswith(("-sources.jar", "-javadoc.jar"))), key=version_key)
    if not jars:
        raise SystemExit(f"{group}:{artifact} is not in the Gradle cache; pass --{artifact} or build the app once")
    return jars[-1]


def default_java_home():
    """JAVA_HOME, else the JDK whose javac is on PATH."""
    if os.environ.get("JAVA_HOME"):
        return Path(os.environ["JAVA_HOME"])
    javac = shutil.which("javac")
    if not javac:
        raise SystemExit("No JDK: pass --java-home or set JAVA_HOME")
    return Path(javac).resolve().parent.parent


def default_sdk():
    """ANDROID_HOME / ANDROID_SDK_ROOT, else sdk.dir from local.properties (read only)."""
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return Path(os.environ[name])
    properties = REPO / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line[len("sdk.dir="):].replace("\\:", ":").replace("\\\\", "\\"))
    return None


def default_android_jar(sdk):
    """The android.jar of the newest installed platform, or None."""
    if sdk is None:
        return None
    def level(jar):
        return [int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", jar.parent.name)]
    jars = sorted((sdk / "platforms").glob("android-*/android.jar"), key=level)
    return jars[-1] if jars else None


def default_apk():
    """The newest APK the app's debug or release assembly left behind."""
    apks = [apk for kind in ("debug", "release")
            for apk in (REPO / "android" / "build" / "outputs" / "apk" / kind).glob("*.apk")]
    return max(apks, key=lambda apk: apk.stat().st_mtime) if apks else None


def default_serial(adb):
    """The first attached, booted emulator; the probe never targets a phone."""
    try:
        listing = subprocess.run([str(adb), "devices"], capture_output=True, text=True, timeout=30).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    for line in listing.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device" and re.fullmatch(r"emulator-\d+", parts[0]):
            return parts[0]
    return None


def check_host_api(host_api):
    """Every top-level shadow class android-awt compiles must be in host-api.jar, the library
    the on-device D8 resolves hub plugins against; a class missing there dexes into a call the
    APK cannot link. Inner classes follow their outer source file, so sources are the oracle."""
    with zipfile.ZipFile(host_api) as archive:
        classes = {name for name in archive.namelist() if name.endswith(".class")}
    missing = []
    for source in sorted(SHADOW_SOURCES.rglob("*.java")):
        relative = source.relative_to(SHADOW_SOURCES).as_posix()
        if relative.startswith(tuple(root + "/" for root in SHADOW_ROOTS)) and source.stem != "package-info":
            if relative[:-len(".java")] + ".class" not in classes:
                missing.append(relative)
    outside = sorted(name for name in classes if not name.startswith(tuple(root + "/" for root in SHADOW_ROOTS)))
    print(f"[host-api.jar] {len(classes)} classes; missing shadow classes: {len(missing)}; outside the shadow: {len(outside)}")
    for name in missing + outside:
        print("  " + name)
    if missing or outside:
        raise SystemExit("host-api.jar does not match the shadow surface")


def main():
    # Every input has a default, so the bare command runs: the host retargeting fixture always,
    # the APK/D8/emulator half whenever an APK, D8 and a booted emulator are found. A missing
    # device input is reported as SKIP; --require-device turns that into a failure.
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", type=Path, help="JDK 11+ (default: JAVA_HOME, else javac on PATH)")
    parser.add_argument("--output", type=Path, default=REPO / "build" / "runtime-plugin-dex")
    parser.add_argument("--sdk", type=Path, help="Android SDK (default: ANDROID_HOME, else local.properties)")
    parser.add_argument("--r8", type=Path, help="R8/D8 jar (default: Gradle cache)")
    parser.add_argument("--apk", type=Path, help="app APK (default: newest under android/build/outputs/apk)")
    parser.add_argument("--serial", help="emulator serial (default: the first booted emulator)")
    parser.add_argument("--asm", type=Path, help="ASM jar for the host retargeter (default: Gradle cache)")
    parser.add_argument("--asm-commons", type=Path, help="ASM commons jar (default: Gradle cache)")
    parser.add_argument("--host-only", action="store_true",
                        help="run only the host retargeting fixture (no APK, D8 or emulator)")
    parser.add_argument("--require-device", action="store_true",
                        help="fail instead of skipping when the APK, D8 or emulator cannot be found")
    args = parser.parse_args()
    asm = args.asm or gradle_jar("org.ow2.asm", "asm")
    asm_commons = args.asm_commons or gradle_jar("org.ow2.asm", "asm-commons")
    suffix = ".exe" if os.name == "nt" else ""
    java_home = args.java_home or default_java_home()
    java = java_home / "bin"
    skip_device = None
    if not args.host_only:
        sdk = args.sdk or default_sdk()
        adb = sdk / "platform-tools" / ("adb" + suffix) if sdk else None
        if args.r8 is None:
            try:
                args.r8 = gradle_jar("com.android.tools", "r8")
            except SystemExit:
                pass
        args.apk = args.apk or default_apk()
        if args.serial is None and adb is not None and adb.is_file():
            args.serial = default_serial(adb)
        absent = [name for name, value in (("sdk", adb if adb is not None and adb.is_file() else None),
                                           ("r8", args.r8), ("apk", args.apk), ("serial", args.serial))
                  if value is None]
        if absent:
            skip_device = "no " + ", no ".join(absent) + " found"
            if args.require_device:
                parser.error(skip_device + "; pass --" + ", --".join(absent))
        elif not re.fullmatch(r"emulator-\d+", args.serial):
            raise SystemExit("This probe only targets an emulator.")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    logs = []

    def run(label, *command, expected_failure=False):
        result = subprocess.run(list(map(str, command)), capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=300 if label.startswith("push ") else 120)
        message = f"[{label}] exit={result.returncode}\n{result.stdout}{result.stderr}"
        print(message, end="", flush=True)
        logs.append(message)
        (out / "results.log").write_text("\n".join(logs), encoding="utf-8")
        if expected_failure:
            if result.returncode == 0 or "NoSuchMethodError" not in result.stdout + result.stderr:
                raise SystemExit("Expected the old conversion to expose the Flow ABI mismatch")
        elif result.returncode:
            raise SystemExit(result.returncode)
        return result.stdout + result.stderr

    # Retargeting fixtures: the app's own JdkRetargeter, compiled on the host, rewrites a
    # desktop-compiled plugin the way PluginDexer does before D8; the driver checks the bytecode,
    # the emulator runs it against the APK's shims.
    tool_classes = out / "retarget-tool"
    fixture_classes = out / "retarget-classes"
    tool_classes.mkdir(exist_ok=True)
    fixture_classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, (asm, asm_commons)))
    run("compile JdkRetargeter", java / ("javac" + suffix), "--release", "11", "-encoding", "UTF-8",
        "-cp", classpath, "-d", tool_classes, RETARGET_SOURCES / "JdkRetargeter.java",
        *sorted((RETARGET_SOURCES / "jdk").glob("*.java")), Path(__file__).resolve().parent / "TestRuntimeRetarget.java")
    run("compile retarget fixture", java / ("javac" + suffix), "--release", "11", "-encoding", "UTF-8",
        "-d", fixture_classes, Path(__file__).resolve().parent / "TestRuntimeRetargetPlugin.java")
    run("package retarget fixture", java / ("jar" + suffix), "cf", out / "retarget-plugin.jar", "-C", fixture_classes, ".")
    result = run("retarget fixture", java / ("java" + suffix), "-Xverify:all",
                 "-cp", os.pathsep.join((str(tool_classes), classpath)), "TestRuntimeRetarget",
                 out / "retarget-plugin.jar", out / "retarget-rewritten.jar")
    if "PASS:" not in result:
        raise SystemExit("Missing retarget success marker")
    # Host link run: the rewritten plugin against the shims, the desktop regex engine and
    # DesktopRegexStrings compiled from the app's sources, so every retargeted call site resolves
    # to a real member on the JVM before the emulator half checks it on ART.
    # android.jar is compile-only: Pattern names android.icu for CANON_EQ, which the fixture
    # never sets, so nothing from it loads on the JVM.
    android_jar = default_android_jar(args.sdk or default_sdk())
    if android_jar is None:
        raise SystemExit("No android.jar under <sdk>/platforms; pass --sdk")
    runtime_classes = out / "retarget-runtime"
    runtime_classes.mkdir(exist_ok=True)
    app_java = RETARGET_SOURCES.parent
    run("compile retarget runtime", java / ("javac" + suffix), "-encoding", "UTF-8", "-nowarn",
        "-cp", os.pathsep.join(map(str, (tool_classes, android_jar))), "-d", runtime_classes,
        app_java / "plugins" / "DesktopRegexStrings.java",
        *sorted((RETARGET_SOURCES / "regex").glob("*.java")))
    result = run("retarget host link", java / ("java" + suffix), "-Xverify:all",
                 "-cp", os.pathsep.join(map(str, (tool_classes, runtime_classes, out / "retarget-rewritten.jar"))),
                 "TestRuntimeRetargetPlugin")
    if "PASS:" not in result:
        raise SystemExit("Missing host link success marker")
    if args.host_only:
        return
    if skip_device:
        message = f"SKIP: APK/D8/emulator half ({skip_device}); host retargeting fixture passed\n"
        print(message, end="", flush=True)
        logs.append(message)
        (out / "results.log").write_text("\n".join(logs), encoding="utf-8")
        return

    with zipfile.ZipFile(args.apk) as archive:
        manifest = dict(line.split("=", 1) for line in archive.read("assets/runtime-dex/inputs.properties")
                        .decode().splitlines() if "=" in line)
        for name in ("desugar.json", "java-api.jar", "host-api.jar"):
            data = archive.read("assets/runtime-dex/" + name)
            if hashlib.sha256(data).hexdigest() != manifest["sha256." + name]:
                raise SystemExit("Compiler asset hash mismatch: " + name)
            (out / name).write_bytes(data)
    check_host_api(out / "host-api.jar")
    (out / "apk-sha256.txt").write_text(hashlib.sha256(args.apk.read_bytes()).hexdigest() + "\n")
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    run("compile desktop plugin", java / ("javac" + suffix), "--release", "11", "-d", classes,
        Path(__file__).resolve().parent / "TestRuntimeHttpPlugin.java")
    run("package JVM fixture", java / ("jar" + suffix), "cf", out / "plugin.jar", "-C", classes, ".")
    compiler = [java / ("java" + suffix), "-cp", args.r8, "com.android.tools.r8.D8",
                "--release", "--min-api", "26"]
    run("old D8 conversion", *compiler, "--output", out / "old.jar", out / "plugin.jar")
    run("aligned D8 conversion", *compiler, "--desugared-lib", out / "desugar.json",
        "--lib", out / "java-api.jar", "--classpath", out / "host-api.jar",
        "--output", out / "aligned.jar", out / "plugin.jar")
    run("retarget D8 conversion", *compiler, "--desugared-lib", out / "desugar.json",
        "--lib", out / "java-api.jar", "--classpath", out / "host-api.jar",
        "--output", out / "retarget.jar", out / "retarget-rewritten.jar")

    remote = "/data/local/tmp/runtime-plugin-contracts"
    run("prepare emulator fixture", adb, "-s", args.serial, "shell", "mkdir", "-p", remote)
    for local, name in ((args.apk, "host.apk"), (out / "old.jar", "old.jar"), (out / "aligned.jar", "aligned.jar"),
                        (out / "retarget.jar", "retarget.jar")):
        run("push " + name, adb, "-s", args.serial, "push", local, remote + "/" + name)
    for name, main_class, negative in (("old.jar", "TestRuntimeHttpPlugin", True),
                                       ("aligned.jar", "TestRuntimeHttpPlugin", False),
                                       ("retarget.jar", "TestRuntimeRetargetPlugin", False)):
        result = run("link " + name, adb, "-s", args.serial, "shell",
                     f"CLASSPATH={remote}/host.apk:{remote}/{name}", "dalvikvm", main_class,
                     expected_failure=negative)
        if not negative and "PASS:" not in result:
            raise SystemExit("Missing fixture success marker")
    run("remove temporary host copy", adb, "-s", args.serial, "shell", "rm", remote + "/host.apk")


if __name__ == "__main__":
    main()
