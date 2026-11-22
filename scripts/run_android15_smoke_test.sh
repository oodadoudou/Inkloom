#!/usr/bin/env bash
set -euo pipefail

INKLOOM_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INKLOOM_ADB="$INKLOOM_PROJECT_DIR/.android-sdk/platform-tools/adb"
INKLOOM_JAVA_ROOT="$INKLOOM_PROJECT_DIR/.toolchains/jdk-17.0.16+8/Contents/Home"

export JAVA_HOME="$INKLOOM_JAVA_ROOT"
export ANDROID_SDK_ROOT="$INKLOOM_PROJECT_DIR/.android-sdk"
export ANDROID_AVD_HOME="$INKLOOM_PROJECT_DIR/.android-avd"

if [[ ! -x "$INKLOOM_ADB" ]]; then
  echo "adb is missing from $INKLOOM_ADB" >&2
  exit 1
fi

echo "Waiting for an Android device..."
"$INKLOOM_ADB" wait-for-device

INKLOOM_BOOT_DEADLINE=$((SECONDS + 300))
until [[ "$("$INKLOOM_ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  if (( SECONDS >= INKLOOM_BOOT_DEADLINE )); then
    echo "Android did not finish booting within five minutes." >&2
    exit 1
  fi
  sleep 2
done

cd "$INKLOOM_PROJECT_DIR"
./scripts/stage_user_brush_corpus.sh
./gradlew connectedDebugAndroidTest
./scripts/install_debug_apk.sh

"$INKLOOM_ADB" shell am force-stop app.inkloom.debug
INKLOOM_START_RESULT="$("$INKLOOM_ADB" shell am start -W -n app.inkloom.debug/app.inkloom.MainActivity)"
printf '%s\n' "$INKLOOM_START_RESULT"

if ! printf '%s\n' "$INKLOOM_START_RESULT" | grep -Fq "Status: ok"; then
  echo "Inkloom did not report a successful cold start." >&2
  exit 1
fi

INKLOOM_PID="$("$INKLOOM_ADB" shell pidof app.inkloom.debug | tr -d '\r')"
if [[ -z "$INKLOOM_PID" ]]; then
  echo "Inkloom is not running after launch." >&2
  exit 1
fi

# Capture first: grep -m1 closing the pipe early would fail the script under pipefail.
INKLOOM_PACKAGE_DUMP="$("$INKLOOM_ADB" shell dumpsys package app.inkloom.debug | tr -d '\r')"
INKLOOM_VERSION="$(grep -m1 'versionName=' <<<"$INKLOOM_PACKAGE_DUMP" | xargs)"
echo "Android 15 smoke test passed: PID $INKLOOM_PID, $INKLOOM_VERSION"
