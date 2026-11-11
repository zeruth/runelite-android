#!/usr/bin/env python3
"""Link a desktop-compiled plugin against the exact APK using its compiler assets."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile


SHADOW_SOURCES = Path(__file__).resolve().parent.parent / "android-awt" / "src" / "main" / "java"
SHADOW_ROOTS = ("java", "javax", "sun", "com/sun")


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
    parser = argparse.ArgumentParser()
    for name in ("java-home", "sdk", "r8", "apk", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        raise SystemExit("This probe only targets an emulator.")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    suffix = ".exe" if os.name == "nt" else ""
    java = args.java_home / "bin"
    adb = args.sdk / "platform-tools" / ("adb" + suffix)
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
    remote = "/data/local/tmp/runtime-plugin-contracts"
    run("prepare emulator fixture", adb, "-s", args.serial, "shell", "mkdir", "-p", remote)
    for local, name in ((args.apk, "host.apk"), (out / "old.jar", "old.jar"), (out / "aligned.jar", "aligned.jar")):
        run("push " + name, adb, "-s", args.serial, "push", local, remote + "/" + name)
    for name, negative in (("old.jar", True), ("aligned.jar", False)):
        result = run("link " + name, adb, "-s", args.serial, "shell",
                     f"CLASSPATH={remote}/host.apk:{remote}/{name}", "dalvikvm", "TestRuntimeHttpPlugin",
                     expected_failure=negative)
        if not negative and "PASS:" not in result:
            raise SystemExit("Missing fixture success marker")
    run("remove temporary host copy", adb, "-s", args.serial, "shell", "rm", remote + "/host.apk")


if __name__ == "__main__":
    main()
