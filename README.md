# Inkloom Android

Inkloom is an offline-first Android handwritten notebook and PDF annotation application built from the supplied BrushNote product specification and Stitch design system. It targets Android 10+ phones, tablets, and ChromeOS devices.

## Implemented

- Adaptive monochrome library and editor UI, compact phone navigation, hideable page rail, draggable/hideable tool dock with persistent free position, horizontal/vertical layouts and top/bottom/left/right docking presets, and compact brush inspector with real tip previews.
- Durable local documents, nested folders, favorites, tags, archive, trash, duplication, page CRUD/reordering/templates/bookmarks/custom numbering, 0–3 level collapsible page outlines, cross-notebook page move/copy, atomic snapshots, and append-only crash-recovery journal.
- Search across note/page titles, custom page numbers, tags, text boxes, and sticky notes from the library or current note.
- Persistent Background/Notes/Highlights/Objects/custom layers with active-layer writing, visibility, locking, rename, reorder, and safe custom-layer removal; locked strokes are protected from erase, selection, undo, and clear-page operations.
- Real page objects: insert retained images with reversible crop and rectangle/rounded/oval masks, rich text (bold/italic/underline, alignment, line spacing, bullets, numbering and checklists), sticky notes, and pen-drawn rectangles, ellipses, triangles, diamonds, lines and arrows with live preview and 45° line/arrow snapping; select, drag, resize, rotate, duplicate, delete, and edit properties with journal-backed persistence and matching PDF/PNG export.
- Per-page geometry uses the actual PDF/image/paper aspect ratio. Reversible four-edge page crop changes the visible aspect ratio while retaining and correctly mapping the untouched background, ink and objects on screen and in PNG/PDF export. A 90° page rotation transforms the crop, background, normalized ink coordinates, stylus orientation, object bounds and object rotation together, and is covered by round-trip tests.
- Permission-gated local audio notes use AAC/M4A recording, page-linked draggable audio cards, title/duration metadata, playback, search, persistence, flattened-export labels, and inclusion in portable editable backups.
- Stylus-first input: fingers pan and focal-point pinch-zoom by default, optional finger drawing, recent-stylus/contact-area palm cancellation, Wacom pressure/tilt diagnostics, configurable pen buttons, two-finger double-tap undo, bounded panning, and viewport reset.
- Pressure/tilt/orientation sampling with persisted pressure-onset, full-pressure and sensitivity calibration plus a live response-curve preview; independent persisted Pen/Marker/Eraser sizes, independent Pen/Marker opacity, HSV color, persisted custom palettes and stabilization; fixed, stylus-following, stroke-following or deterministic random tip rotation; distance-, percentage- and fade-based start/end taper; whole/local eraser, undo/redo, freeform multi-stroke lasso with atomic move/scale/rotate/copy/delete, shape, text, and editable/collapsible sticky objects.
- Completed ink is cached in a size-aware off-screen layer, so live pen updates redraw the active stroke instead of replaying the entire page; document, layer, selection, erase, undo and resize changes invalidate the cache explicitly.
- Non-destructive PDF and image import with ink overlays; flattened PDF, page PNG, and portable editable ZIP package export/import. Bundle restore validates paths, entry count, and expanded size before retaining assets.
- Storage Access Framework brush import with content-signature probing, 256 MB safety limit, SHA-256 deduplication, original-source retention, compatibility grades, and persistent conversion reports.
- ABR 6.1/6.2 import decodes bounded raw or PackBits/RLE grayscale sampled tips. A multi-brush ABR becomes separate library entries with correctly inverted Inkloom masks instead of cycling unrelated tips through one stroke; the two authorized real files yield 11 and 13 extracted previews. ABR dynamics and proprietary Photoshop paint behavior remain on editable Inkloom defaults.
- SUT import reads the CSP SQLite preset, maps px/mm physical brush size, opacity/flow/spacing/hardness/thickness/rotation and feature flags, resolves ordered main/secondary tip and paper-texture materials, decodes pressure/tilt/velocity/random source bits and their independent minima, retains pressure and velocity response curves, maps CSP stabilization plus its speed-response mode, and maps verified fixed/direction-of-pen/direction-of-line tip-angle modes. CSP Starting and ending is decoded as distance, percentage or fade mode with its target list, independent start/end ranges, units and minimum sizes; retained but unchecked targets remain disabled. CSP Watercolor edge radius/unit, opacity, darkness and blur are mapped into a separate edge pass. Dual Brush uses its real referenced secondary tip as an independent deterministic stream with its own size, spacing, flow, hardness, thickness, rotation, Ribbon, spray and repeat settings. Main and secondary spraying retain independent particle size/unit, brush-size linking, density and center bias. Paper texture retains density, scale, rotation, brightness, contrast, inversion and density emphasis and is tiled rather than stretched once across the tip. Alpha-only density in CSP black-RGB material thumbnails is preserved, and live drawing/export use one shared decoder. Disabled CSP sources stay disabled even when the file retains old curves. Curves, taper, watercolor edge, spray, dual and texture settings are immutable stroke snapshots. Imported tips retain their native aspect ratio and thickness axis; CSP repeat methods and Ribbon strokes use deterministic tip selection, tangent alignment, and continuous distance placement. Pressure/time/tilt/orientation/velocity are interpolated independently of Android event frequency, and screen/export share the same path. Pen-barrel twist, old-C2F Offscreen decoding, wet pigment mixing and unverified CSP Dual Brush composite-mode formulas remain compatibility limitations rather than pixel-identical CSP simulation.
- Android share/open entry points for PDF, image, and brush files; no broad storage permission or network dependency.
- 91 JVM tests for persistence/recovery, format signatures, safety limits, deterministic brush behavior, a 5,000-stroke sampling regression, and the authorized ABR/SUT sample folder, plus four Android 15 instrumentation tests.

The feature comparison and implementation priorities are recorded in [`docs/FEATURE_RESEARCH.md`](docs/FEATURE_RESEARCH.md).
The requirement-by-requirement status and remaining gates are recorded in [`docs/COMPLETION_AUDIT.md`](docs/COMPLETION_AUDIT.md).
The reproducible Android 15 emulator result and screenshots are recorded in [`docs/ANDROID15_SMOKE_TEST.md`](docs/ANDROID15_SMOKE_TEST.md).

## Build

```bash
export JAVA_HOME="$PWD/.toolchains/jdk-17.0.16+8/Contents/Home"
./gradlew testDebugUnitTest lintDebug assembleDebug -PbrushSamplesDir=/Users/doudouda/Downloads/tmp
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Android 15 emulator on this Mac

This checkout includes an isolated Android 15 ARM64 tablet-emulator setup that stays inside the project instead of changing a system Android Studio installation:

```bash
./scripts/setup_android15_emulator.sh
./scripts/start_android15_emulator.sh
./scripts/install_debug_apk.sh
```

For automated smoke tests without an emulator window, use `./scripts/start_android15_emulator.sh --headless`; the normal command remains the visible interactive tablet emulator.

After the emulator has booted, run `./scripts/run_android15_smoke_test.sh` in another Terminal window to execute the Compose instrumentation tests, install the current debug APK, cold-start Inkloom, and verify the running package version.

The first command downloads the official emulator and default Android 15 ARM64 image (a large one-time download). The emulator is suitable for layout, persistence, PDF/image/brush import, export and crash smoke tests. Wacom pressure, hover, palm rejection and pen-button acceptance must still run on the physical Movink 14.

## Wacom Movink 14 test checklist

1. Install the APK on the Android 15 device, open **Settings**, and leave **Draw with finger** off.
2. Verify a finger pans, two fingers zoom, the Wacom pen draws, and resting a palm does not leave ink.
3. Enable **Input diagnostics** and verify pressure, tilt, tool type, and pen-button state change on screen.
4. In **Settings**, raise **Pressure onset** until hover/light accidental contact no longer inks; lower **Full pressure point** until a comfortable press reaches maximum output; then adjust **Pressure sensitivity** while watching the response-curve preview.
5. Draw light-to-heavy strokes with Pen, Marker, Eraser and one imported SUT brush. Confirm that every tool reaches its minimum/maximum size smoothly and that exported PNG/PDF output matches the canvas.
6. Assign the primary pen button to **Eraser**, hold it while drawing, then release it and confirm the previous brush returns. Repeat with the eraser tail if the pen exposes one.
7. Import the sample SUT and ABR files from `/Users/doudouda/Downloads/tmp`, inspect their tip previews and conversion reports, and compare simple stamp brushes before testing watercolor or dual-brush presets. Each ABR file should expand into its individual brush-tip entries.
