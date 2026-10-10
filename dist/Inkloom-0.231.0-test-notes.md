# Inkloom 0.231.0 Debug

Source: `fdbf0a988d96bdbfdd45bfa1398d82a2edb2f0a5`.

## Changes

- Fill and selection references now composite visible adjustments, groups, masks and clipping using the native layer compositor. Filtering keeps clipping-base identity intact.
- Tool properties provide separate, persisted **Exclude paper**, **Exclude imported page background** and **Exclude text** switches. Defaults are off; settings backups preserve them. Text-free references render on demand without changing document content or retaining a permanent second bitmap set. Eyedropper reference settings remain independent.
- Brush group management scrolls above the keyboard at narrow width and enlarged Chinese text.
- Floating page counter and jump-panel title grow to fit enlarged text.
- Includes the previously pushed library-title and wrapping Draw shape controls from after 0.230.0.

## Validation

- JVM: 1,029 tests, zero failures/errors, eight skipped.
- Android 15: 199 targeted tests, zero failures/skips, one consolidated run on release source. Includes actual brush/layer/page dialogs at 360dp width and 2x Chinese font.
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 231, install and cold start passed; launch screenshot inspected and crash buffer empty.
- Zoom-overflow test corrected after the same failure reproduced on unchanged main: its sample was below the visible viewport. No production zoom change.
- Page dialog test corrected to target the modal Tags field instead of the background Tags tab. Original production page dialogs passed; unnecessary candidate changes discarded.

## Boundaries

Not a full Android-suite or MovinkPad verification. Text-heavy-page performance and remaining window/device configurations require further validation. Third-party draft/reference interoperability remains open. These changes do not close every item in the feature catalog. Google Drive delivery verifies four local sync-folder hashes; cloud re-download is not verified.
