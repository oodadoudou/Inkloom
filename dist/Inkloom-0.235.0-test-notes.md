# Inkloom 0.235.0 Debug

Source: `abf27010d85560af3d9e5c19bb76b2fecf95f9cb`. Includes the 0.233.0 and 0.234.0 changes.

## Changes

- Swipe to turn pages: when the finger is not drawing, a quick, mostly horizontal one-finger flick at fit-to-page zoom turns to the next page (flick left) or the previous page (flick right). Slow or diagonal drags still move the canvas, and the small pan of a recognised swipe is undone. The last page does not add a page.
- Never turns: pen or eraser contact, palms, a finger touch within 0.7 s of the pen, the move/rotate tools or the pen hand button, zoomed-in or rotated views, and two- or three-finger gestures.
- Settings > Swipe to turn pages (on by default); included in settings backups.

## Validation

- JVM: 1,046 tests, zero failures/errors, eight skipped.
- Android 15: PageSwipeWindowTest 3/3. 169 input-sensitive tests: 167 passed, and both failures passed on rerun. No full-suite run on this build.
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 235, install and cold start passed; crash buffer empty; flicks turned pages on this APK.

## Boundaries

Swipe thresholds were tuned with injected touches on the emulator; real hand feel on the MovinkPad (speed, palm, accidental turns while resting a hand) is DEVICE_PENDING. Source commits are local and have not been pushed. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. Earlier 0.232.0–0.234.0 deliveries are kept until this build is approved.
