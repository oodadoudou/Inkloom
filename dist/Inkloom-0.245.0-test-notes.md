# Inkloom 0.245.0 Debug

Source: `dd71b518`. Includes everything in 0.244.0.

## Changes

- The tool rail with its size and opacity bar, snapped to an edge, now sits flush along that whole edge like a docked palette (square, no gap), in its own column; the page fits beside it.
- Opening a note no longer shifts the page once the toolbar is measured.
- The Brushes window no longer moves over the page because of the page navigator corner.

## Validation

Full Android suite plus fixes (see JSON); JVM 1,233 (8 skipped); lint 0 errors / 116 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
