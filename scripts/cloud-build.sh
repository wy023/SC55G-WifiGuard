#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: JDK 17 is required." >&2
  exit 2
fi

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "ERROR: JDK 17 is required; detected Java ${JAVA_MAJOR:-unknown}." >&2
  exit 2
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle 8.9 is required. The GitHub workflow installs it automatically." >&2
  exit 2
fi

echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Gradle: $(gradle --version | sed -n '3p')"

gradle --no-daemon --stacktrace clean testDebugUnitTest assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: Expected APK was not produced: $APK" >&2
  exit 3
fi

mkdir -p dist
OUTPUT="dist/SC55G-WifiGuard-v1.0.0-debug.apk"
cp "$APK" "$OUTPUT"
sha256sum "$OUTPUT" > "$OUTPUT.sha256"

# A valid APK is a ZIP and must contain both AndroidManifest.xml and classes.dex.
unzip -tq "$OUTPUT"
unzip -Z1 "$OUTPUT" | grep -qx 'AndroidManifest.xml'
unzip -Z1 "$OUTPUT" | grep -qx 'classes.dex'

ls -lh "$OUTPUT" "$OUTPUT.sha256"
echo "Cloud build completed successfully."
