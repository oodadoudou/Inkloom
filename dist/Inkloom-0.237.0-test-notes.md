# Inkloom 0.237.0 Debug

Source: `07adeadd`. Includes all changes since 0.236.0.

## Changes

- Vector layers: draw vector lines, edit and erase them (whole line, up to crossings or by area), transform them with a selection, copy as SVG.
- Rulers on layers: ruler tool (straight, curve, figure, guide, perspective), snapping, and the ruler menu: make a selection from a ruler, draw along it, turn a vector line into a ruler, show or delete rulers, link them to the layer, and limit them to all layers, the folder or the current layer. Perspective rulers can be fixed or turned into guides.
- Transform aligns by text pixels or by vector paths.
- Obtain screen color, inside the app and from other apps (Actions > Canvas, asks for Android's screen-capture permission each time).
- Comic frame border folders: create, split and merge frames with the frame tool; gutters are settings.
- Hold the pen at the end of a stroke to get a straight line, curve, arc, circle or ellipse, then drag its handles to adjust it.
- Shading assist (10 presets) and colorize from hint colours, both without AI (Actions > Canvas).
- Super resolution when enlarging the canvas (Real-ESRGAN anime model on the device).
- 3D drawing figure layers: pose the mannequin, camera, proportions, shaded / outline / silhouette, perspective ruler from the camera.
- Animation folders with a timeline (frame rate, frames per cel, order, playback preview), registered as animation materials and placed on any page.
- Also: CSP's Effect filters, fill / gradient / tone layers, convert layer (grey, monochrome, lower resolution, tone), match colours to a reference layer, file objects, image and page template materials, filter preset thumbnails.

## Validation

- JVM: 1,171 tests, zero failures/errors, eight skipped.
- Android 15: full suite of 933 tests; 930 passed. The materials failure was a test-isolation gap (fixed); the two other failures passed on rerun with their whole classes.
- After the telemetry fix: privacy, super resolution, canvas sizing and launch tests passed (66 tests).
- Lint: zero errors, 116 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 237, install and cold start (under 1 s) passed; crash buffer empty.

## Boundaries

The APK is 184 MB: ONNX Runtime's native library is included for arm64 and x86_64, uncompressed. A release build splits by device type. Screen colour from other apps, super resolution speed, the held-shape gesture and the animation timeline need checking on the MovinkPad (DEVICE_PENDING). The animation timeline window must be closed to draw on a cel. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. Earlier deliveries are kept until this build is approved.
