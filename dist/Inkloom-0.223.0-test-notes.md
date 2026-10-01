# Inkloom 0.223.0 Debug verification

Source: `0c4acd1e345d36a9bbada90999a24b02dbca0ab9`. APK SHA-256: `e58f9a18986a851b838f0acf27a178f978642baec47ad458b9f5793ea731c336`.

- Layer contents selection: replace/add/subtract/intersect; selected layers and folder descendants, soft alpha, empty groups/intersections and one-step undo. Occupied-region sampling uses a bounded 256px bitmap.
- Levels: independent RGB/red/green/blue input black/gamma/white and output black/white; reset, cancel, undo and reopening. Old documents default to neutral individual channels.
- Curves: per-channel graph, add/drag/delete points, drag-out removal, numeric 0-255 input/output, linear or shape-preserving smooth interpolation and per-channel reset.
- Gradient maps: multiple RGB color stops, positions, deletion, shared standard color picker, preview cancellation and one-step undo; source transparency retained.
- Clear outside selection: existing sparse RGBA8/F16 tiles only, feathered coverage, other layers and selection unchanged; protected/alpha-locked targets explicitly rejected. One-step undo/redo and exact saved pixels after reopening verified.
- Line art: convert to drawing color preserving alpha; brightness to opacity on black. RGBA8/F16, soft selection, sparse 8K engine sampling, cancellation and unchanged unselected F16 payload verified. Shared RGB picker, presets/backups, actual window cancel/apply/undo/redo/reload tested.
- Fixed fast filter previews being reset to original pixels by Compose recomposition; mask-edit targets cannot accidentally run content color filters.
- Drive delivery verifies all four copied files before retiring older packages; corrupted-evidence-copy regression retains the previous package.

995 JVM tests: 0 failures, 8 skipped. 154 unique Android 15 targeted tests passed across final runs. Lint: 0 errors, 114 warnings. Two isolated delivery-script tests passed. Broad Android run initially had one test failure due to selection-versus-raster coordinate sampling and checking an empty reload placeholder; the strengthened exact-payload test passed on final rerun. Other 153 tests passed in that run.

No full Android-suite success is claimed. MovinkPad touch, pressure, latency and long-session performance remain DEVICE_PENDING. The engine's sparse 8K cases do not prove a complete 8K production filter workflow on the device. Drive Desktop local file hashes are checked during delivery; cloud redownload is unverified. Requirements/design/task documents remain ignored. This delivery does not claim every deferred or scope-undecided CSP extension is implemented.
