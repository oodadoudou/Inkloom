# Selection input regression contract (2026-09-20)

Official references checked before changing this interaction:

- [CSP Selection area](https://help.clip-studio.com/en-us/manual_en/330_selection/Selection_area.htm): a Lasso drag creates a freehand selection; the current tool owns that drag.
- [CSP Basic touch gestures](https://help.clip-studio.com/en-us/manual_en/750_gestures/Basic_touch_gestures.htm): two-finger drag is canvas navigation; one-finger navigation depends on a separate finger/pen tool preference.
- [Photoshop Lasso tools](https://helpx.adobe.com/photoshop/using/selecting-lasso-tools.html): the freehand Lasso uses a drag to define the border.
- [Photoshop touch gestures](https://helpx.adobe.com/photoshop/using/touch-gestures.html): two-finger touch controls pan/zoom/rotation, separate from an active drawing or selection drag.
- [CSP Gradient layers](https://help.clip-studio.com/en-us/manual_en/180_layers/Gradient_layers.htm) and [Photoshop gradient fill](https://helpx.adobe.com/photoshop/desktop/adjust-color/color-effects-techniques/apply-gradient-fill.html): active selection limits gradient coverage.
- [CSP image materials](https://help.clip-studio.com/en-us/manual_en/210_file/Import_image_file_to_canvas.htm): imported image material is an independent object; rasterizing is required before editing it as raster pixels.

Inkloom contract for the current implementation: with Lasso explicitly selected, an isolated non-palm finger contact draws a selection even immediately after a stylus contact; a second finger takes over navigation. Gradient pixels remain clipped to the active selection while selection-protection overlay is allowed outside it. Until mixed object/pixel transforms exist, attempting to transform or transfer a raster selection on a layer with retained objects must explicitly reject before changing pixels. The Android 15 regressions for these cases remain the acceptance tests; MovinkPad-specific contact classification stays `DEVICE_PENDING`.
