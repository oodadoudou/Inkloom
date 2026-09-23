# Inkloom 0.213.0 Debug

Source: `2d773a22761ecc6066bce65be77324b050a68e1c`

- R17: page PNG now uses the visible page pixel dimensions. Exports beyond the current 4096-pixel edge budget report a clear limit instead of silently downsizing. Encoding runs off the UI thread.
- R20: the tablet Library rail supports multi-select folder move and recursive delete. Nested folders remain browsable; deleting folders preserves their notebooks at Library root. Invalid targets reject the whole move.
- Validation: 923 JVM tests (0 failures, 8 skipped), focused Android 15 R17 PNG and R20 folder interaction tests, lint, diff check, v2 signature, install, and cold start passed. The full Android suite was not rerun for this package.
- R17 still lacks physical units/DPI, anchored crop/extend, resampling and transparent/custom-size export. R20 still lacks narrow-screen folder management, folder trash restoration, large-PDF stress validation, and physical-device validation.
- MovinkPad Pro remains `DEVICE_PENDING`. Google Drive Desktop local copy verification does not prove remote cloud download.
