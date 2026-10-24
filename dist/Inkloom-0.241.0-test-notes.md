# Inkloom 0.241.0 Debug

Source: `206a8321`. Includes everything in 0.240.0.

## Changes

- Interface size (Settings > General): Automatic measures the screen (about 75% on the MovinkPad Pro 14 at 2x), or choose 60-120%. Applies to every window, dialog and menu.
- Palette docks (CSP): each dock column has a header with > (icons) and >> (hide). Icon docks open palettes as pop-ups beside their icon; tap outside or Back closes them. A hidden dock is a thin strip that shows it again.
- Floating windows shown by themselves can be minimised to their title bar.
- Default windows open in free space instead of on top of each other, stay at their side of a wider workspace, and keep clear of the page navigator.
- Docks keep their own width; only the canvas changes when the window is resized.
- Brush source editor: Trim to content, Make square, 3x3 tiling check, and built-in original textures (6) and tips (4).
- Filter presets record their algorithm version.

## Validation

JVM 1,223 (8 skipped); Android 15 targeted suites listed in the JSON; lint 0 errors / 116 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING: interface size on the MovinkPad, dock pop-ups by finger and pen. Not pushed.
