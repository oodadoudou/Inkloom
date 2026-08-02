#!/usr/bin/env bash
set -euo pipefail

INKLOOM_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_SDK_ROOT="$INKLOOM_PROJECT_DIR/.android-sdk"
export ANDROID_AVD_HOME="$INKLOOM_PROJECT_DIR/.android-avd"

EMULATOR_ARGS=(
  -avd inkloom_api35_tablet
  -no-boot-anim
)

if [[ "${1:-}" == "--headless" ]]; then
  EMULATOR_ARGS+=( -no-window -no-audio -gpu swiftshader_indirect )
else
  EMULATOR_ARGS+=( -gpu host )
fi

exec "$ANDROID_SDK_ROOT/emulator/emulator" "${EMULATOR_ARGS[@]}"
