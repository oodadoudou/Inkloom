# Inkloom 0.249.0 Debug

Source: `f7f7a2e0`. Includes everything in 0.248.0.

## Changes

- Fix: selection drags and magic-wand taps were cancelled whenever the screen refreshed mid-gesture; the first wand tap from no selection was always lost.
- Selection bar in three rows: tools and combine modes; the current tool's options in place; actions only while something is selected (Deselect, Invert, Feather, Expand/shrink, Fill, Delete, Copy to layer, Cut to layer, Transform). Rare commands sit in "…" in three groups.
- Magic wand: named and on the rail; New replaces; Contiguous on/off; Close gap; Sample All / Layer / Reference; tolerance slider in the bar.
- Clone stamp on the rail beside Blend.
- Pages panel: one Add page button with a menu instead of four small icons.

## Validation

JVM 1,256 (8 skipped); Android 15 targeted 188/188 and 53/53 after cleanup; lint 0 errors / 114 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
