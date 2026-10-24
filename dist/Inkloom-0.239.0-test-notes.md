# Inkloom 0.239.0 Debug

Source: `5a03f4b1`. Everything in 0.238.0, plus the ML Kit privacy fix; use this build instead of 0.238.0.

## Changes

- Privacy: ML Kit text recognition no longer queues or uploads usage logs, and the app no longer has the INTERNET or network-state permission (OCR and super resolution run fully on the device).

- 3D book preview of a notebook (notebook card menu, or Actions › Notebook › 3D book preview…): cover and page block, pages that bend as they turn, left/right binding, side/saddle stitch, cover and first-page options; tap a page's outer side or the arrows, drag to turn the book, pinch to zoom, double-tap to close/open, go to a page.
- Filter › Effect: Glow, Inflate and Drop shadow (as in CSP 5.1) and Glitch.
- Views: Navigator (page thumbnail with a red frame for the window; tap/drag to move, zoom, rotate, flip view, Fit, 100%) and Sub view (reference pictures beside the canvas, pan/zoom/flip, eyedropper sets the drawing colour; pictures stay out of notebooks). Older saved workspaces gain these views in closed windows instead of being reset.
- Blend › Copy Stamp brush: pen-tap the source (or Tool property › Choose source), then paint the copy; Aligned keeps the offset between strokes; selection and transparency lock apply; one undo per stroke.
- Colour management: Gamut warning while soft proofing (colours the proof profile cannot reproduce show grey; preview only).
- Text objects: font (Android families or imported TTF/OTF/TTC, licence shown, missing fonts fall back to the default) and letter spacing; double-click a text object to open its properties.

## Validation

- JVM: 1,199 tests, zero failures/errors, eight skipped.
- Android 15: full suite of 942 tests; 940 passed, and the two intermittent classes passed 13/13 on rerun.
- Lint: zero errors, 116 existing warnings. Diff whitespace check passed.
- After the privacy merge: privacy, offline OCR, super resolution, launch, book preview and Sub view tests, 68/68.
- APK v2 signature, version 239, install and cold start (under 1 s) passed; crash buffer empty; no network permission.

## Boundaries

On the MovinkPad (DEVICE_PENDING): pen feel of Copy Stamp, book-preview page turns, Navigator dragging, Sub view pinch, and the new filters' speed on large layers. Copy Stamp samples only the current layer; imported fonts are not packed into Inkloom package exports. Source commits are local and have not been pushed. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. Earlier deliveries are kept until this build is approved.
