#!/usr/bin/env bash
set -euo pipefail

INKLOOM_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INKLOOM_ADB="$INKLOOM_PROJECT_DIR/.android-sdk/platform-tools/adb"
INKLOOM_BRUSH_ROOT="${INKLOOM_USER_BRUSH_ROOT:-/Users/doudouda/Downloads/2}"
INKLOOM_DEVICE_ROOT="/data/local/tmp/inkloom-real-brushes"

if [[ ! -x "$INKLOOM_ADB" ]]; then
  echo "adb is missing from $INKLOOM_ADB" >&2
  exit 1
fi

if [[ ! -d "$INKLOOM_BRUSH_ROOT" ]]; then
  echo "LOCAL_FIXTURE_REQUIRED: user brush root is missing: $INKLOOM_BRUSH_ROOT"
  exit 0
fi

"$INKLOOM_ADB" shell rm -rf "$INKLOOM_DEVICE_ROOT"
"$INKLOOM_ADB" shell mkdir -p "$INKLOOM_DEVICE_ROOT"

stage_smallest() {
  local extension="$1"
  local limit="${2:-1}"
  local candidates
  local count=0
  candidates="$(mktemp)"
  find "$INKLOOM_BRUSH_ROOT" -type f -iname "*.$extension" -exec stat -f $'%z\t%N' {} \; | sort -n | head -n "$limit" > "$candidates"
  if [[ ! -s "$candidates" ]]; then
    echo "LOCAL_FIXTURE_REQUIRED: no .$extension brush exists under $INKLOOM_BRUSH_ROOT"
    rm -f "$candidates"
    return
  fi
  while IFS=$'\t' read -r size source; do
    count=$((count + 1))
    local target="corpus.$extension"
    if (( count > 1 )); then target="corpus-$count.$extension"; fi
    "$INKLOOM_ADB" push "$source" "$INKLOOM_DEVICE_ROOT/$target"
    echo "Staged .$extension corpus representative $count ($size bytes): $source"
  done < "$candidates"
  rm -f "$candidates"
}

# The local corpus currently contains 22 source containers. Stage every supported container so a
# green compatibility run cannot hide behind one convenient representative from each format.
stage_smallest brush 100
stage_smallest brushset 100
stage_smallest abr 100
stage_smallest sut 100

# The picker-to-raster UI gate asserts three known presets from this user-owned set.
# Publish the bytes as a named shell fixture; the instrumentation test registers its own
# temporary Downloads item through MediaStore so Android's picker can enumerate it.
INKLOOM_UI_BRUSHSET="$(find "$INKLOOM_BRUSH_ROOT" -type f -name '吹石笔刷2025.brushset' -print -quit)"
if [[ -n "$INKLOOM_UI_BRUSHSET" ]]; then
  "$INKLOOM_ADB" shell mkdir -p /data/local/tmp/inkloom-ui-brushset
  "$INKLOOM_ADB" push "$INKLOOM_UI_BRUSHSET" /data/local/tmp/inkloom-ui-brushset/Inkloom-P0-Test.brushset
else
  echo "LOCAL_FIXTURE_REQUIRED: 吹石笔刷2025.brushset is missing under $INKLOOM_BRUSH_ROOT"
fi
