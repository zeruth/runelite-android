#!/usr/bin/env bash
# Usage: [LIBS=dir] run.sh <injected-client.jar> <out-dir>   (needs nix + curl)
# Fetches asm + r8 into $LIBS, compiles the real transform sources + Harness.kt, then writes
# <out-dir>/client-baseline-dex.jar (no lock fix) and client-fixed-dex.jar (with it).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
JAVA_SRC=$HERE/../../android/src/androidMain/java/net/runelite/mp
SRC=$HERE/../../android/src/androidMain/kotlin/net/runelite/mp/client
IN=$1
OUT=$2
LIBS=${LIBS:-$OUT/libs}
B=$OUT/build
mkdir -p "$LIBS" "$B" "$OUT"
for a in asm asm-tree asm-commons; do
  [ -f "$LIBS/$a-9.6.jar" ] || curl -sfL -o "$LIBS/$a-9.6.jar" "https://repo1.maven.org/maven2/org/ow2/asm/$a/9.6/$a-9.6.jar"
done
[ -f "$LIBS/r8-9.5.28.jar" ] || curl -sfL -o "$LIBS/r8-9.5.28.jar" https://maven.google.com/com/android/tools/r8/9.5.28/r8-9.5.28.jar
CP=$(ls "$LIBS"/asm*.jar "$LIBS"/r8-*.jar | tr '\n' ':')
NX() { nix --extra-experimental-features "nix-command flakes" "$@"; }
KT=$(NX build nixpkgs#kotlin --no-link --print-out-paths)
JDK=$(NX build nixpkgs#jdk17 --no-link --print-out-paths)
export PATH=$JDK/bin:$KT/bin:$PATH
mkdir -p "$B/stub"
javac -cp "$CP" -d "$B/stub" \
  "$HERE/stub/android/util/Log.java" \
  "$JAVA_SRC/compat/jdk/JdkRetargets.java" \
  "$JAVA_SRC/compat/JdkRetargeter.java" \
  "$JAVA_SRC/client/SyntheticLambdas.java"
kotlinc -nowarn -cp "$CP$B/stub" -d "$B/classes" \
  "$SRC/ClientTransforms.kt" "$SRC/FixLockVerificationTask.kt" "$HERE/Harness.kt" "$HERE/TestLockStructure.kt"
RUNCP="$B/classes:$B/stub:$CP$KT/lib/kotlin-stdlib.jar"
java -Xverify:all -cp "$RUNCP" TestLockStructureKt "$B/work-test"
java -cp "$RUNCP" HarnessKt "$IN" "$OUT/client-baseline-dex.jar" false "$B/work-base"
java -cp "$RUNCP" HarnessKt "$IN" "$OUT/client-fixed-dex.jar" true "$B/work-fix"
