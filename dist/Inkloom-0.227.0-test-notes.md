# Inkloom 0.227.0 Debug verification

Source: `6be9fb6b1da886eb8f885181640a7fdf501eafcd`. APK SHA-256: `0d492b5fb45aada89d7ab11ddbefb3ddfeac3ec896848091f270bec2306472af`.

- Adjustment layers created while a selection is active take that selection as their layer mask, as in Photoshop and Clip Studio Paint. The mask is prepared after the paint publication barrier and inserted with the layer in one transaction. One undo removes both; redo restores the same reserved mask asset. Creation undo still refuses a layer whose mask was edited later, so chronological history undoes the mask stroke first.
- Filters: new fixed-strength Sharpen and Sharpen more (3x3 Laplacian, 4-neighbour 0.25 / 8-neighbour 0.5, unpremultiplied linear colour). Transparent neighbours contribute the centre colour and alpha is preserved, like the existing unsharp mask, so uniform ink on a transparent layer is intentionally unchanged. RGBA8/F16, selection weighting, tile-independent output, presets/favourites, and preview/cancel/apply with one undo. The kernels are original approximations, not proprietary CSP/Photoshop pixel matches.
- Carried from 0.226.0: clipped adjustment layers and chronological clip-to-below history.

1020 JVM tests: 0 failures, 8 skipped. 49 unique relevant Android 15 tests passed across the final runs; class lists are in the adjacent JSON. Lint and signature evidence are in the JSON.

No full Android-suite success is claimed. MovinkPad touch, pressure, latency and long-session validation remain DEVICE_PENDING. Drive Desktop local four-file hashes are checked during delivery; cloud redownload is unverified. Requirements/design/task documents remain ignored.
