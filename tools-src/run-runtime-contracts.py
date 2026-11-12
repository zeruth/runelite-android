#!/usr/bin/env python3
"""Thin wrapper over run-awt-contracts.py for the HTTP server/client, TLS, management and ProcessHandle contracts,
then the JdkRetargets shim contracts (TestJdkShimContracts: each shim against the JDK member it replaces).

Those contracts carry a ``// host-contract`` header and are discovered by the runner; this wrapper only
keeps the optional H2 probe, which needs a pinned H2 jar and a source outside android-awt.  Android-only
focus, clipboard and AudioTrack probes are TestRuntimeCoreContracts and TestAudioLineContracts; they must
additionally run on an emulator/device.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

CONTRACTS = ("TestHttpServerContracts", "TestJavaHttpContracts", "TestJavaHttpTlsContracts", "TestManagementContracts",
             "TestProcessHandleContracts")
SHIM_SOURCES = "android/src/androidMain/java/net/runelite/mp/compat/jdk"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--output", type=Path, help="also write the runner's output to this file")
    parser.add_argument("--h2-jar", type=Path, help="Pinned H2 runtime JAR for the actual JDBC timeout probe")
    parser.add_argument("--shims-only", action="store_true",
                        help="run only the JdkRetargets shim contracts, not the shadow runner")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text("", encoding="utf-8")
    # The shim contracts are independent of the shadow patching, so a shadow failure is
    # reported at the end rather than hiding their result.
    failed = 0
    if not args.shims_only:
        command = [sys.executable, str(repo / "tools-src/run-awt-contracts.py")]
        if args.java_home:
            command += ["--java-home", str(args.java_home)]
        for name in CONTRACTS:
            command += ["--only", name]
        result = subprocess.run(command, text=True, encoding="utf-8", errors="replace", stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        print(result.stdout, end="", flush=True)
        if args.output:
            args.output.write_text(result.stdout, encoding="utf-8")
        failed = result.returncode

    java = Path(args.java_home) / "bin" if args.java_home else None
    suffix = ".exe" if os.name == "nt" else ""

    def executable(name):
        found = str(java / (name + suffix)) if java else shutil.which(name)
        if not found or not Path(found).exists():
            raise SystemExit(f"{name} unavailable; set --java-home to JDK 11+")
        return found

    def run(label, name, *arguments):
        probe = subprocess.run([executable(name), *map(str, arguments)], cwd=repo, text=True, encoding="utf-8",
                               errors="replace", stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
        message = f"[{label}] exit={probe.returncode}\n{probe.stdout}"
        print(message, end="", flush=True)
        if args.output:
            with args.output.open("a", encoding="utf-8") as handle:
                handle.write(message)
        if probe.returncode:
            raise SystemExit(probe.returncode)

    # The JdkRetargets shims run on ART at API 26, so they compile against the JDK 8 API; the
    # contract itself needs the JDK 11 members it compares them with.
    with tempfile.TemporaryDirectory(prefix="jdk-shims-") as directory:
        shims = Path(directory) / "shims"
        tests = Path(directory) / "tests"
        shims.mkdir()
        tests.mkdir()
        sources = sorted((repo / SHIM_SOURCES).glob("*.java"))
        run("compile JdkRetargets shims (--release 8)", "javac", "-encoding", "UTF-8", "--release", "8",
            "-Xlint:-options", "-d", shims, *sources)
        run("compile shim contracts", "javac", "-encoding", "UTF-8", "-cp", shims, "-d", tests,
            repo / "tools-src/TestJdkShimContracts.java")
        run("shim contracts", "java", "-cp", str(tests) + os.pathsep + str(shims), "TestJdkShimContracts")
    if failed:
        raise SystemExit(failed)
    if not args.h2_jar or args.shims_only:
        return

    with tempfile.TemporaryDirectory(prefix="runtime-contracts-") as directory:
        h2 = Path(directory) / "h2"
        h2.mkdir()
        h2_jar = Path(directory) / "h2-runtime.jar"
        shutil.copyfile(args.h2_jar, h2_jar)
        run("compile H2 contracts", "javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", h2_jar, "-d", h2,
            repo / "android/src/androidMain/java/org/h2/jdbcx/JdbcDataSource.java", repo / "tools-src/TestH2Contracts.java")
        run("H2 contracts", "java", "-cp", str(h2) + os.pathsep + str(h2_jar), "TestH2Contracts")


if __name__ == "__main__":
    main()
