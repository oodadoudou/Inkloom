# Inkloom 0.248.0 Debug

Source: `844e5ad0`. Includes everything in 0.247.0.

## Changes

- Clear a layer: layer menu > Clear, Delete with nothing selected, or a three-finger scrub.
- Procreate gestures (Settings > Gestures): touch and hold for the eyedropper with a sampling ring, four-finger tap for full screen, quick pinch to fit, hold two or three fingers to keep undoing or redoing. Three-finger redo now works with real finger jitter.
- Notes: lasso > Convert to text; handwriting is indexed for search on the device.
- Settings > About: privacy note and open-source notices.
- Release build: shrunk, arm64-only, signed from keystore.properties (60 MB).
- README rewritten.

## Validation

JVM 1,252 (8 skipped); full Android 15 suite 972/973 (one test-side wait fixed); release-build instrumented runs; lint 0 errors / 114 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
