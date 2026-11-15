#!/bin/bash
# Plugin Hub compatibility gate (docs/hub-compat.md), run by CI.yml's hub-compat job.
# Needs only the host classes the scan resolves against, not the APK: android-awt and app
# javac output, the client and api jars (asked for explicitly because the app may consume
# them as class directories), and the release runtime libraries in the gradle cache.

set -e -x

./gradlew --build-cache \
  ':android-awt:compileReleaseJavaWithJavac' \
  ':android:compileReleaseJavaWithJavac' \
  ':android:resolveHubHostLibraries' \
  ':client:jar' ':runelite-api:jar'

# CI.yml restores build/hub-cache, so only new or updated plugin jars are downloaded;
# --prune drops jars and scan results the current manifest no longer lists, keeping the
# saved cache at one hub's worth.
python3 tools-src/hub-compat-scan.py --refresh --prune
python3 tools-src/hub-compat-issues.py --check
