# Inkloom 0.211.0 Debug

Source: `eda0f8a1137afab1b5c7441af584d80b649f8b98`

- Added explicit notebook multiselection for batch move, Trash, and restore, plus ordered multi-page move/copy between notebooks.
- PDF import now runs off the UI thread with progress and cancellation; the final save stage prevents a cancellation race.
- Library backup restore validates all referenced assets before publication. Damaged archives default to rejection; an explicit recovery action restores intact items and reports skipped content.
- Validation: 919 JVM tests (0 failures, 8 skipped); 10 focused Android 15 tests (0 failures), including a 512-page PDF; lint 0 errors; v2 signature; installed Debug APK cold-started in 6132 ms.
- Wacom MovinkPad Pro behavior remains `DEVICE_PENDING`; full Android instrumentation was not rerun.
- Google Drive Desktop local copy is SHA-256 verified; remote cloud download is not verified.
