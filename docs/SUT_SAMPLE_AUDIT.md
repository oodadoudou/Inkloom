# SUT sample audit

Baseline: 12 user-authorized `.sut` files under `/Users/doudouda/Downloads/tmp`, inspected read-only against their active `Node`/`Variant` row.

## Effector layout verified by cross-sample comparison

- 44-byte big-endian header.
- Source flags: `0x10` pressure, `0x20` tilt, `0x40` velocity, `0x80` random.
- Four following percentages are the independent pressure, tilt, velocity and random minima.
- First optional curve chunk is pressure response; second optional curve chunk is velocity response.
- Curve data is retained by CSP when a source is disabled, so source flags—not curve presence—control whether a dynamic is active.

Across 60 audited property effectors (size, opacity, flow, interval and thickness):

| Finding | Count |
| --- | ---: |
| Pressure source enabled | 19 |
| Tilt source enabled | 5 |
| Velocity source enabled | 4 |
| Random source enabled | 2 |
| Curve retained while pressure was disabled | 14 |

The previous parser would have incorrectly enabled pressure for those 14 retained-curve cases. Parser version 6 fixes that migration and preserves all four source configurations as immutable stroke snapshots.

## Starting and ending audit

- Six of the 12 samples retain both CSP Starting and Ending range switches. Five use `BrushInOutType=0` (Specify length); one uses `BrushInOutType=1` (By percentage).
- `BrushInOutTarget` is a big-endian target list with a 12-byte header followed by 21 `{parameter ID, enabled, minimum}` records. ID `1001` is the brush-size target in the audited generation.
- None of the six range-enabled samples simultaneously enable the brush-size target. Parser version 9 therefore retains their ranges but does not invent a visual size taper. This corrects the earlier assumption that `BrushInRatio/BrushOutRatio` were minimum sizes; they are the range values for percentage mode.
- For presets that explicitly enable the size target, the engine supports distance, percentage and fade modes, preserves independent units/minimums, and evaluates taper from cumulative physical path distance in the same screen/export stroke snapshot.

## Watercolor edge audit

- Five samples explicitly enable `BrushUseWaterEdge`.
- Their radii are `3.0`, `5.9`, or `12.4` with 10–20% alpha power; darkness and blur are zero in this sample set.
- Parser version 9 retains radius/unit, alpha power, value power and blur/unit. Line and stamp renderers apply the same separate edge pass on screen and in PNG/PDF export.

## Tip material and stroke layout audit

- 11 of the 12 samples reference CSP `MaterialFile` brush-tip assets; the remaining sample explicitly uses a round tip and has no `MaterialFile` table.
- One sample references two ordered tip materials. Parser version 7 extracts every referenced tip in source order and retains CSP's repeat-method value; deterministic repeat, reverse, no-repeat, random, one-cycle and randomized-cycle selection is shared by display and export.
- Three samples enable CSP Ribbon. They are now rendered with continuous distance placement and stroke-tangent alignment.
- Source tips range from `33×300` to `300×300`. The previous renderer normalized every tip to a square, destroying long and flat brush shapes. Version 0.8.0 scales by the source's longest dimension, preserves its aspect ratio, and applies CSP's horizontal/vertical thickness direction on the correct axis.
- Parser version 12 retains paper-texture density, scale, rotation, brightness, contrast, inversion and density emphasis. For example, `Painting brush.sut` stores 153% scale, `-59` brightness and `-14` contrast; the previous stretched mask ignored all three. Display/export now share a tiled, transformed texture sampler while still clearly reporting the separate old-C2F full-resolution limitation below.

## Physical size, stabilization, and alpha-texture audit

- `BrushSizeUnit=2` presets are physical millimetre brushes. Parser version 13 converts those values to Inkloom's reference-pixel model instead of treating millimetres as raw pixels; pixel-unit presets are retained directly.
- CSP `FlickerReduction`, `FlickerReductionBySpeed`, and `FlickerReductionBySpeedType` are retained as per-brush stabilization, speed adjustment, and the corresponding slow/fast response mode. They combine with the user's global stabilizer without discarding the imported preset's intent.
- Several real material thumbnails store useful density as alpha over black RGB. For example, the audited `Painting brush.sut` grain has black RGB with non-uniform alpha. RGB-only sampling collapsed it to a solid mask. Parser/renderer version 13 composites that alpha into effective luminance, and the live canvas and PNG/PDF path now use one shared decoder.
- Pure JVM texture tests and an Android bitmap instrumentation test cover the alpha-only mask case.

## C2F material container findings

- Each audited `material_0.layer` uses the old `89 C2F` container, not the modern `CSFCHUNK` `.clip` container.
- The container is a valid little-endian-length chunk stream (`HEAD`, two `dATA` chunks, `TAIL`) with standard CRC-32 stored little-endian.
- CRC-valid PNGs found inside some layers are fully transparent `CanvasPreview` placeholders. They are deliberately rejected as brush tips; choosing them would produce invisible strokes.
- A published community decoder that locates the first zlib signature was tested against the extracted `Painting brush.sut` layer. Its successful 141,231-byte output begins at byte 19,193, inside an embedded PNG `IDAT`; `197 × 179 × 4 + 179` is exactly 141,231 bytes (RGBA plus one PNG filter byte per row). It therefore decodes the embedded 197×179 preview, not the proprietary full-resolution Offscreen raster, and is not used as a full-material decoder.
- The tar-level `thumbnail/thumbnail.png` is currently the only consistently recoverable non-empty mask. Full Offscreen tile decoding remains unclaimed until the old C2F database/page encoding can be decoded and validated against all samples.

## Dual Brush audit

- `Painting brush.sut` enables `UseDualBrush=1` and references three distinct materials: a paper texture, the main tip `Lane Chalk Champ 2`, and the secondary tip `Sampled Brush 2`. The UTF-16LE paths occur independently in `BrushPatternImageArray` and `DualPatternImageArray`; parser version 10 resolves the latter instead of reusing and rotating the main tip.
- Its retained secondary settings are size `106.003%`, interval `15.909%`, flow `100%`, hardness `100%`, thickness `100%`, fixed rotation `0°`, rotation-random scale `100%`, spray enabled, Ribbon disabled and repeat mode `0`.
- Its secondary spray stores particle size `124.186%`, brush-size linking enabled, density `1`, center bias `0`, and pixel unit. Parser version 11 snapshots these fields independently from the secondary brush size and the renderer uses brush size as the spray area rather than the old arbitrary square jitter.
- Display and PNG/PDF export now run the real secondary mask through the same independent, deterministic stamp sampler with those snapshotted settings. Missing secondary thumbnails explicitly fall back to the main tip.
- `DualBrushCompositeMode=7` is retained in metadata, stroke snapshots, editable backups and the Brush Studio report. Because that private numeric mode has not been validated, the renderer currently uses normal source-over blending and labels the limitation instead of guessing a CSP formula.

## Still not claimed as CSP-identical

- The final header velocity-scale word differs between preset generations (`100` or `500`); it is retained by the decoder but not applied until its unit is proven.
- Angle-source high-bit combination strengths are retained but not guessed.
- Pen-barrel twist is not exposed by the current Android `MotionEvent` path.
- C2F Offscreen/full-resolution material decoding, wet-pigment simulation and pixel-identical formulas for unverified Dual Brush composite modes remain separate renderer work.
