# Inkloom 0.219.0 Debug checkpoint

Source: `7a73eb13c0388c160ba28cd337e0b51ee64ec57d`

- Unified workspace persistence for panel trees, named layouts, portrait/landscape profiles and the tool rail. Existing layout trees take priority over legacy flags. Ordinary settings saves no longer overwrite panel positions or visibility.
- One-time migration removes obsolete live settings branches. Damaged current or legacy layout bytes remain in app-private recovery storage; startup recovers the last valid workspace where available.
- Historical brush-size and shortcut defaults migrate once. Legacy fills preserve raster precision, object order, identity and alpha-lock relationships.
- Includes the preceding eight ordered development areas: layer modes; structured object warps; physical canvas sizing/native PNG; explicit raster interchange subsets; scanned-PDF OCR/all search occurrences; ICC/F16 and profiled output; filters/presets/visible copies; folder trash restore and shared asset protection.
- Validation: JVM 970 tests, 0 failures, 8 skipped. Workspace-focused Android 15 regression runs of 52 and 17 tests passed; focused tests for the other eight areas passed separately. This checkpoint does **not** claim a completed current full Android suite.
- APK v2 signature, installation, cold startup, and library/editor smoke passed; crash buffer empty. Lint passed with 0 errors and 114 warnings. Drive evidence is recorded in the adjacent JSON metadata.
- MovinkPad behavior, actual printer/wide-gamut hardware, opening interchange files in third-party applications, and cloud re-download remain pending. ICC editing uses canonical linear extended sRGB; PSD/ORA/TIFF support explicit bounded subsets, not every proprietary layer type. Native 8K uses PNG/TIFF/Inkloom bundle; layered round trips and JPEG are limited to 16 MP. OCR quality depends on source scans. Lens blur uses a bounded disk kernel rather than depth-map optics.
