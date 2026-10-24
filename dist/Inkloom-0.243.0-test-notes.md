# Inkloom 0.243.0 Debug

Source: `b20ffd95`. Includes everything in 0.242.0.

## Changes

- Animation timeline: Export animated GIF (480 / 720 / 1080 px wide), one frame per cel held for its duration, looping; no onion skin in the file.
- Onion skin now also shows cels drawn with vector, shape or text objects.

## Validation

JVM 1,229 (8 skipped); animation, rendering and launch Android suites 77/77; real save-dialog export checked; lint 0 errors / 116 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
