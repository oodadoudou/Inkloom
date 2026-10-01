# Inkloom 0.221.0 Debug validation

Source: `0fe459fba2ab9724e963fac540de2747ef2bc138`.

- Single Actions entry at top right; category remembered, duplicate left wrench removed.
- Selection modes use stable neutral highlights and larger exit targets. Changing operation commits the unfinished contour with its original operation.
- Non-destructive posterize, invert, threshold and hue/saturation luminosity controls; defaults preserve old documents. Masks, group scopes, alpha, edit/cancel, undo and serialization covered.
- JVM: 981 tests, zero failures, 8 skipped.
- Android 15: 51 unique targeted tests passed across final class runs; this is not a full-suite claim. The adjustment UI rerun corrected an ambiguous test selector for duplicate Undo controls; both tests passed after correction.
- Lint: zero errors, 114 warnings. Debug assembly, v2 signature, install/cold launch, Actions UI visual check and empty crash buffer passed.
- Google Drive delivery is verified in the local sync folder. Cloud transfer is managed by Drive Desktop; cloud redownload and MovinkPad feel/performance are unverified.
- After the replacement verifies, retain only this managed Debug release and its evidence locally and in Drive. Keep source fixtures, documents and active build caches.

References: [Procreate Selection Interface](https://help.procreate.com/procreate/handbook/selections/selections-interface), [CSP Selection Area](https://help.clip-studio.com/en-us/manual_en/330_selection/Selection_area_tool.htm), [CSP Tonal Correction](https://help.clip-studio.com/en-us/manual_en/390_filters/Tonal_Correction_Effects.htm), [Photoshop Color Effects](https://helpx.adobe.com/photoshop/desktop/adjust-color/color-effects-techniques/apply-special-color-effects-to-images.html).
