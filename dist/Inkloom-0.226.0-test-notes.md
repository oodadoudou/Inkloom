# Inkloom 0.226.0 Debug verification

Source: `ebba4fe35074e2528cb937d010a990d09a8a3e1d`. APK SHA-256: `0c5ae89b5e2b9598fa3c5db1049acc19a4b6ccb53fb49716fcbdced812efc355`.

- Clipped adjustment layers: "clip to layer below" now applies to adjustment layers in the canvas, page render and export compositors. The correction is weighted by base coverage (base alpha × base mask × base opacity); clipped layers between the base and the adjustment are corrected with it, and layers below the base keep their colors. Pixel layers and isolated folders can be bases. Stacked clipped adjustments and clipped layers above an adjustment keep the same base. A hidden or missing base makes the clipped correction a no-op. RGBA8 and F16 are covered.
- Limitation: this follows the engine's existing per-layer clipping model. Semi-transparent base edges blend the corrected backdrop by coverage, which can differ slightly from Photoshop's isolated clipping-group composite. A layer clipped directly onto an unclipped adjustment layer still has no visible base; this is unchanged.
- Clipping history: the quick action and layer menu toggle clipping for pixel and adjustment layers through the scoped appearance transaction. It waits for pending paint publication, patches only the layer's flag, rejects stale history and Through-folder bases, refuses locked layers, and is undone/redone in chronological order.

1016 JVM tests: 0 failures, 8 skipped. 45 unique relevant Android 15 tests passed across final runs. The adjustment, layer-target window, layer-order window and group-rendering classes (21 tests) reran after the history change. Merge, draft export, native parity, benchmark and legacy-fill classes ran on the compositor commit just before it. Lint and signature evidence are in the adjacent JSON.

No full Android-suite success is claimed. MovinkPad touch, pressure, latency and long-session validation remain DEVICE_PENDING. Drive Desktop local four-file hashes are checked during delivery; cloud redownload is unverified. Requirements/design/task documents remain ignored.
