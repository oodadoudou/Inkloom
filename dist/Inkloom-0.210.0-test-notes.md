# Inkloom 0.210.0 Debug

Source: `48f5cd2bd1e5915d319f6dd23d24737b2e37c651`

- Added a drag/zoom crop preview before imported notebook covers are saved. Cancel keeps the notebook unchanged.
- Existing notebooks can choose a preset, saved cover, or newly imported image without changing their pages.
- Validation: 914 JVM tests (0 failures, 8 skipped); 3 focused Android 15 interaction tests (0 failures); Lint 0 errors; APK v2 signature verified; installed Debug APK cold-started in 4590 ms.
- Primary Wacom MovinkPad Pro gesture feel remains `DEVICE_PENDING`. Full Android instrumentation suite was not rerun for this notebook-only change.
- Google Drive Desktop copy is locally hash-verified after sync; remote cloud download has not been verified.
