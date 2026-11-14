#!/bin/bash

set -e -x

CACHEDIR="$HOME/.cache/runelite"
mkdir -p "${CACHEDIR}"
GLSLANG_ARCHIVE="${CACHEDIR}/glslang.zip"
GLSLANG_DIR="${CACHEDIR}/glslang"
GLSLANG_RELEASE='https://github.com/KhronosGroup/glslang/releases/download/8.13.3743/glslang-master-linux-Release.zip'
GLSLANG_CHECKSUM='d02b22d35ba7bc786a115fbc90ff2caef7f1cd99d87ab053e3dba8efda5b405a'

if [ ! -f "${GLSLANG_ARCHIVE}" ] || [ ! -d "${GLSLANG_DIR}" ] || ! echo "${GLSLANG_CHECKSUM} ${GLSLANG_ARCHIVE}" | sha256sum -c -; then
  wget -O "${GLSLANG_ARCHIVE}" "${GLSLANG_RELEASE}"
  echo "${GLSLANG_CHECKSUM} ${GLSLANG_ARCHIVE}" | sha256sum -c
  unzip -o -q "${GLSLANG_ARCHIVE}" -d "${GLSLANG_DIR}"
fi

export ORG_GRADLE_PROJECT_glslangPath="$GLSLANG_DIR/bin/glslangValidator"
python3 tools-src/run-awt-contracts.py
./gradlew --build-cache ':android-awt:awtSurfaceCheck'
./gradlew --build-cache ':android:verifyReleasePluginApi'

# Hub compatibility gate (docs/hub-compat.md). The release build above leaves the
# android-awt and app javac output behind; the client and api jars are asked for
# explicitly because the app may consume them as class directories. CI.yml restores
# build/hub-cache, so only new or updated plugin jars are downloaded.
./gradlew --build-cache ':client:jar' ':runelite-api:jar'
python3 tools-src/hub-compat-scan.py --refresh
python3 tools-src/hub-compat-issues.py --check
