# Inkloom 0.244.0 Debug

Source: `3df7bed4`. Includes everything in 0.243.0.

## Changes

- Animation timeline: Export video (MP4, H.264) next to Export animated GIF, at 480 / 720 / 1080 px wide; every frame at the folder's frame rate.

## Validation

Full Android suite on 0.243.0 source (953 tests; colour tests updated for the wheel-only chip; one timing flake passed on rerun); animation suites; JVM 1,232 (8 skipped); lint 0 errors / 116 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING: MP4 on the MovinkPad's hardware encoder. Not pushed.
