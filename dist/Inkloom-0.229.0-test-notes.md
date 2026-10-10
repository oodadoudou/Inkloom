# Inkloom 0.229.0 Debug verification

Source: `0a92fd8ba54e6de3a38a2449e76d41c2f684e382`. APK SHA-256: `8d48b4bab213362107ab84b796d0bc4561da21b43af2f8f21822b8bffaae638a`.

- Adjustment dialog (AD-12): new Preview toggle (compare edited vs original on the canvas) and Reset to the adjustment's defaults. Apply always commits the edited values as one history step, even with the preview hidden. Adjustment layers stay parametric, so preview off and Reset reproduce the original pixels exactly.
- High-precision pages (AD-13): F16 pages now apply corrections on sign-preserving extended sRGB values, matching 8-bit pages (previously, for example, Threshold 0.5 turned a mid-blue white on 8-bit and black on F16). Neutral corrections no longer clip HDR, and brightness/contrast, hue/saturation, colour balance and invert keep extended values. Alpha and semi-transparent pixels are preserved, and page thumbnails share the export renderer.
- Verified without behaviour changes: adjustments correct retained image/shape objects identically on canvas, export and sparse composite and respect clipping (AD-11 closed with the 0.226/0.227 clipping and selection-mask work). After a real pinch zoom, transform drags move exact page distances and arrow keys nudge 1 / 10 page pixels (TF-34). All 16 filters, in RGBA8 and F16, leave unselected pixels bit-identical and transparent surroundings empty (FL-39).
- Carried from 0.228.0: workspace gesture fixes, narrow/large-font fixes, draft export defaults, reference exclusions, sparse composite clipping-base fix.
- Known approximations unchanged: clipped adjustments interpolate by base coverage. Sharpen / Sharpen more are original fixed kernels.

1026 JVM tests: 0 failures, 8 skipped. 152 Android 15 tests passed in one run on the release source. One intermittent selection-stroke window test timeout seen earlier is recorded in the JSON.

No full Android-suite success is claimed. MovinkPad touch, pressure, latency, 120 Hz transform/panel dragging and long sessions remain DEVICE_PENDING. Drive Desktop local four-file hashes are checked during delivery; cloud redownload is unverified. Requirements/design/task documents remain ignored.
