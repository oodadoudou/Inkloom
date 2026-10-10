# Inkloom 0.232.0 Debug

Source: `014e30ae0471898a341a0174da26b0cda0154210`.

## Changes

- Brush Studio reflows its header at narrow widths, switches between parameters and the drawing pad in short windows, and preserves the normal tablet columns. Selected categories have readable contrast in the gray theme.
- Brush source tip selectors and pressure presets wrap instead of hiding the last choices.
- Shortcut search, quick-slot naming, link insertion and filter preset naming remain usable above the keyboard.
- Cover cropping scrolls its content while keeping actions reachable. Preview geometry uses one loaded-image snapshot, avoiding a state mismatch during image loading.
- Long brush import paths and damaged-backup recovery details scroll without pushing Stop, Recover or Cancel out of reach.
- Added window audits for object properties, image paper, library actions, selection naming and numeric transforms, colors, search and named layouts.

## Validation

- JVM: 1,029 tests, zero failures/errors, eight skipped.
- Android 15: 120 targeted tests, zero failures/errors/skips, one consolidated run on release source.
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 232, install and cold start passed. Launch screenshot inspected; crash buffer empty.
- All changes are on main. Each reproduced UI defect has its own commit; additional audit coverage and the version bump are separate commits.

## Boundaries

This is a targeted Android suite, not a full-suite or MovinkPad verification. Tests cover enlarged Simplified Chinese text, narrow portrait and short landscape windows, and selected keyboard-open workflows; they do not prove every error state, system file chooser, screen configuration or real-pen ergonomics. UI-15 remains open for these outstanding checks.

Some initial audit failures were test-targeting issues: a background dialog, a title mistaken for its text field, nested scrolling, recycled lazy-list nodes, or state retained between fixtures. The corrected harness targets the front dialog and reveals the actual control; production changes were retained only for reproduced defects. No painting-engine changes were made in this release.

Drive delivery checks all four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified.
