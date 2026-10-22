# Inkloom 0.236.0 Debug

Source: `e45ac7bee86e2da375cad792a7f99062b1b11f9a`. Includes all changes since 0.235.0.

## Changes

- Compact CSP/Photoshop-style dialogs everywhere: slim title bar, dense rows, right-aligned buttons, sized to the window (tool-window width on the tablet, nearly full width on narrow screens). Menus, text fields, check boxes, radio buttons and chips are compact too.
- Image filter window docks at the side so the canvas preview stays visible; settings are label / slider / number rows, grouped as in CSP's Filter menu.
- New filters: Blur, Strong blur, Smoothing, Perlin noise, and the Distort family (Pinch, Curved surface, Wave, Twirl, Convert to panorama, Geometric distortion, Polar coordinates, ZigZag, Fish-eye lens).
- Tonal correction (Actions > Canvas): Brightness/Contrast, Hue/Saturation/Luminosity, Levels, Curves, Color Balance, Invert, Posterize, Threshold and Gradient Map applied to the layer's pixels, or to a flattened copy of what is visible.
- Canvas: Rotate 90° either way or 180°, flip horizontally or vertically (permanent), Flip view horizontally (view only, with an indicator), Fit canvas to selection, Clear undo history, and paper colour in Canvas & resolution.
- Built-in gradient presets (Clip Studio Paint's standard set).
- Earlier local work since 0.235.0: duplicated controls removed, sharing/export of notebooks and pages, continuous vertical page view, Painting workspace, control labels and page-turn cue.

## Validation

- JVM: 1,086 tests, zero failures/errors, eight skipped.
- Android 15: full suite of 906 tests on the compact-dialog source; the three layout-audit failures were fixed and that class passed 26/26; the other two failures passed on rerun. 65 targeted tests after the final changes passed (one fixture corrected).
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 236, install and cold start passed; crash buffer empty.

## Boundaries

Filter speed, pen input with the side-docked filter window and the dialog sizes need checking on the MovinkPad (DEVICE_PENDING). Source commits are local and have not been pushed. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. Earlier deliveries are kept until this build is approved.
