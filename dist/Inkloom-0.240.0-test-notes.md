# Inkloom 0.240.0 Debug

Source: `ec825aa6`. Includes everything in 0.239.0.

## Changes

- Colour chips (foreground/background at the top right) open only the Color Wheel. Tapping the chip you are already editing hides the wheel again. Palettes, Mix and Nearby open from Actions › Views › Views…, and then sit as tabs beside the wheel.
- Panels dock like Clip Studio Paint palettes:
  - Drag a tab to move or detach that panel; tap a tab to bring it forward. Tabs share the title row, so none hides behind scrolling.
  - Drag a panel near the left, right or bottom side of the canvas to dock it to that side of the window.
  - Drop it on another panel's title bar (or the middle of that panel) to stack it as a tab; on the outer part of a panel to place it beside, above or below.
  - Red highlights show exactly where it will go before you let go. Dropping a tab back on its own group only reorders it.
- Panels can be made narrower before their content turns into icons.
- The Views list is tall enough to show every view on the tablet without scrolling.
- Painting workspace: colour wheel above layers on the right (no longer all four colour tabs).

## Validation

- JVM: 1,199 tests, zero failures/errors, eight skipped.
- Android 15: panel, colour-window and launch suites (92 tests) plus the other panel and view tests passed on this change.
- Lint: zero errors, 116 existing warnings. APK v2 signature, version 240, install and cold start passed; crash buffer empty.

## Boundaries

To check on the MovinkPad (DEVICE_PENDING): docking by finger and pen, red drop indicators, the narrower panels and the colour chips. Docks do not yet collapse to icons (CSP's « and ‹), and palettes cannot yet be minimised to their title; those come next. Source commits are local and have not been pushed. Drive delivery checks the four files in the local Google Drive Desktop sync folder. Earlier deliveries are kept until this build is approved.
