#!/usr/bin/env bash
set -euo pipefail

INKLOOM_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="$INKLOOM_PROJECT_DIR/.android-sdk/platform-tools/adb"
APK="$INKLOOM_PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "Build the debug APK first: ./gradlew assembleDebug" >&2
  exit 1
fi

"$ADB" wait-for-device
"$ADB" install -r "$APK"
"$ADB" shell am start -n app.inkloom/.MainActivity
