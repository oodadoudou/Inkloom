#!/usr/bin/env bash
set -euo pipefail

INKLOOM_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INKLOOM_SDK_ROOT="$INKLOOM_PROJECT_DIR/.android-sdk"
INKLOOM_JAVA_ROOT="$INKLOOM_PROJECT_DIR/.toolchains/jdk-17.0.16+8/Contents/Home"
INKLOOM_AVD_ROOT="$INKLOOM_PROJECT_DIR/.android-avd"
INKLOOM_AVD_NAME="inkloom_api35_tablet"

export JAVA_HOME="$INKLOOM_JAVA_ROOT"
export ANDROID_SDK_ROOT="$INKLOOM_SDK_ROOT"
export ANDROID_AVD_HOME="$INKLOOM_AVD_ROOT"

SDK_MANAGER="$INKLOOM_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVD_MANAGER="$INKLOOM_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"

if [[ ! -x "$SDK_MANAGER" || ! -x "$AVD_MANAGER" ]]; then
  echo "Inkloom Android command-line tools are missing from $INKLOOM_SDK_ROOT" >&2
  exit 1
fi

mkdir -p "$INKLOOM_AVD_ROOT"
"$SDK_MANAGER" "emulator" "system-images;android-35;default;arm64-v8a"

if ! "$INKLOOM_SDK_ROOT/emulator/emulator" -list-avds | grep -Fxq "$INKLOOM_AVD_NAME"; then
  printf 'no\n' | "$AVD_MANAGER" create avd \
    --force \
    --name "$INKLOOM_AVD_NAME" \
    --package "system-images;android-35;default;arm64-v8a" \
    --device "pixel_tablet"
fi

echo "Android 15 tablet AVD is ready: $INKLOOM_AVD_NAME"
echo "Start it with: $INKLOOM_PROJECT_DIR/scripts/start_android15_emulator.sh"
