# Inkloom product research baseline

This matrix is the implementation baseline for Inkloom. Cloud AI features are intentionally excluded.

## Notebook applications

### P0 — writing and navigation

- Stylus-only ink by default; optional finger drawing; fingers pan/zoom.
- Pen, highlighter, configurable eraser, lasso, shapes, text, images, sticky notes, colors, palettes, ruler and laser pointer.
- Movable/customizable/hideable toolbar, hideable page sidebar, single/continuous page modes, page rotation and zoom presets.
- Undo/redo gestures, pen-button tool switching, palm-cancel handling, pressure curve and stroke stabilization.
- Page CRUD/reorder/duplicate, templates, bookmarks, outlines, internal links, PDF import/search/annotation/export.

### P1 — organization and rich notes

- Nested folders, tags, favorites, trash, list/grid sorting, bulk move/export/delete and storage view.
- Handwriting, typed-text and PDF-text search with page-level results.
- Images with crop/rotate/mask, reusable stickers/elements, tables, lists, checklists and hyperlinks.
- Audio recordings synchronized to strokes and objects.
- Infinite whiteboards, text documents, presentation/laser mode and multi-window support.
- Versioned editable backup packages, WebDAV/local-folder backup and conflict-preserving sync.

### P2 — optional collaboration

- Comments, share links, viewer/editor permissions and real-time collaboration.
- AI summaries, generative writing and cloud recognition are explicitly outside Inkloom's required scope.

## Drawing and brush applications

Brush presets must version these groups independently and preview changes live:

1. Tip/shape: source image, roundness, thickness, angle, flip, count and scatter.
2. Stroke path: spacing, jitter, fall-off and ribbon orientation.
3. Grain/texture: source, scale, movement, depth, brightness, contrast and blend mode.
4. Size/opacity/flow dynamics driven by pressure, tilt, speed, direction or random seed.
5. Taper: start/end length, size, opacity and pressure response.
6. Rendering: glazing, uniform/intense blend, wet edges and accumulation.
7. Wet mix: charge, dilution, attack, pull, grade and color mixing.
8. Color dynamics: foreground/background, hue, saturation, brightness and per-stamp/per-stroke modes.
9. Stabilization: streamline, motion filtering, expression and adjustable delay/latency tradeoff.
10. Spray: area size, density, particle count, direction and scatter.
11. Dual brush: second tip, size/spacing/dynamics and compositing.
12. Input: global pressure curve, per-brush pressure/tilt mapping, hover and barrel/side buttons.
13. Properties: minimum/maximum size and opacity, preview stamp, author/source and reset point.
14. Library: compact previews, groups, tags, search, favorites, recent, duplicate, export and parser report.

## Sources reviewed

- Goodnotes official Android/features and 2025–2026 product updates
- Notability official feature and audio documentation
- Notion official databases, organization, offline and export documentation
- Notewise official product/help material
- Adobe Photoshop official Brush Settings and dynamics documentation
- Procreate official Brush Studio handbook
- Clip Studio Paint official tool/brush documentation
- Android Developers MotionEvent and advanced stylus documentation

The research is used as a behavior reference only. Inkloom does not copy competitors' artwork, trademarks, or proprietary code.

Clip Studio Paint's official settings glossary confirms that fixed brush-tip interval can respond to pen pressure and stroke speed, while size, opacity/density, tilt, velocity and random sources are independently configurable. The public documentation does not define `.sut` binary curve ordering, so Inkloom maps only effectors whose owning `Variant` column is known and leaves anonymous secondary curve blocks unassigned until supported by repeatable sample evidence.
