# Inkloom 0.222.0 Debug verification

Source: `c8c155cfa35073fd0027082a4c7df37f5b744654`. APK SHA-256: `637b579bd0ad0ceb22fbc84f1b66c1a81de41dcd641182503c22831ce4f03cb4`.

- Layer contents selection: replace/add/subtract/intersect; selected layers and folder descendants; preserve soft content alpha; empty groups/intersections undo in one step. Sampling processes occupied regions with a bounded 256px bitmap.
- Levels: independent RGB/red/green/blue input black/gamma/white and output black/white; channel reset, cancel, undo and reopening. Existing documents default to neutral individual channels.
- Curves: per-channel graph, add/drag/delete points, drag-out removal, numeric 0–255 input/output, linear or shape-preserving smooth interpolation and per-channel reset. Existing curves default to linear interpolation.
- Gradient maps: multiple RGB color stops, position/deletion controls, shared standard color picker on tap/hold, preview cancellation and one-step undo. Source transparency is retained.
- Verified existing layer drag hover expansion, cancel, edge scrolling and multi-layer reorder in actual Android windows.

989 JVM tests: 0 failures, 8 skipped. 59 unique Android 15 targeted tests passed across final runs. Lint: 0 errors, 114 warnings. Signature scheme v2, versionCode 222, emulator installation and cold startup passed. Initial test assertion issues for mask tile count, soft selection subtraction, and rounded gradient output were corrected and rerun.

No full Android-suite success is claimed. MovinkPad feel, latency, pressure and long-session performance remain DEVICE_PENDING. Drive Desktop local file hashes are checked during delivery; cloud redownload is unverified. Requirements/design/task documents remain ignored.
