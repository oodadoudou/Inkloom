# Inkloom engineering rules

## Product boundary

- Inkloom is a professional painting engine inside a lightweight, reliable notebook container. It is not a general-purpose SaaS notebook platform.
- Before proposing or implementing work, ask whether it directly improves either (1) drawing feel/capability or (2) notebook/page management. If neither is true, do not add it unless the user explicitly requests it.
- Drawing feel outranks feature count. No flicker, pen-tip alignment, stable pressure/tilt, deterministic texture, and bounded realtime latency take priority over filters, formats, and breadth.
- Keep the notebook scope focused on Library, folders, notebooks, covers, pages, thumbnails, templates, paper colors, PDF/image import, navigation, bookmarks, and basic search. Existing OCR/handwriting recognition may remain, but do not expand it into an AI product without an explicit request.
- Do not proactively build cloud sync, accounts, collaboration, realtime sharing, recording/voice workflows, meeting transcription, AI chat/summary, social/community features, team spaces, web clients, subscriptions, marketplaces, or multiplayer whiteboards.

## Product priorities

- P0: professional drawing feel on the primary device—no flicker, no cursor/path drift, no trailing strokes, correct pressure/tilt, and dependable Ink/Pencil/Eraser behavior.
- P1 drawing: brush families/editor, Blend/Smudge, Liquify, Selection, Transform, layers/masks/clipping, guides and symmetry.
- P1 notebook: Library, notebooks, page management, PDF import, templates, thumbnails, reorder and batch operations.
- P2: perspective/mesh transforms, improved wet paint, filter system, color management, and PSD/ORA interoperability.
- P3: OCR refinement, handwriting search, additional import formats, and additional templates. Implement only when they solve a demonstrated personal workflow.

## Target device and compatibility

- Wacom MovinkPad Pro 14 with Android 15, 120 Hz, Pro Pen 3 pressure/tilt/hover/buttons/eraser is the primary performance and interaction target.
- Optimize the primary device first. Other supported Android devices must remain safe and non-crashing, but lowest-common-denominator behavior must not degrade the MovinkPad experience.
- Brush-import fidelity is usage-driven: prioritize the user's real SUT, ABR, and Procreate brushes. Report unsupported semantics honestly instead of claiming or engineering broad nominal compatibility.

## UI and architecture scope

- Keep the top-level product structure limited to Library and Editor. Editor owns Canvas, Pages, Brushes, Layers, Colors, Selection, Transform, and Tools.
- Split large UI orchestration by real product domains such as `LibraryWorkspace`, `NotebookWorkspace`, and the core `PaintingWorkspace`; do not introduce speculative service abstractions for cloud, collaboration, accounts, or future SaaS needs.
- Prefer a small number of explicit local components over extensibility that has no current Inkloom use case.

## Product behavior research

- Before implementing or changing a drawing, selection, brush-library, layer, canvas-navigation, or stylus interaction, research the equivalent behavior in at least two current mainstream drawing or handwriting products.
- Prefer official manuals, platform documentation, and reproducible device behavior. Record the sources and the chosen interaction contract in the task notes or commit message before coding.
- Match established user expectations (including thresholds, drag targets, cancellation, visual feedback, and accessibility) instead of inventing a substitute interaction.
- Do not copy proprietary artwork, icons, names, or source code. Reproduce behavior with Inkloom's white visual system and original implementation.

## Regression requirements

- Every input or canvas change must test stylus drawing, stylus hover, erasing, brush switching, two-finger zoom/pan, and drawing after zoom.
- Every reorder interaction must have both operation-level tests and an Android interaction test for the actual long-press/drag path.
- Imported brush changes must be exercised with SUT, ABR, and Procreate fixtures when they are available locally.
- Ship only after JVM tests and the Android 15 instrumented suite pass.
- Commit and push incrementally: one small feature, fix, migration step, or mechanical cleanup per reversible commit. Do not accumulate a multi-feature or whole-phase mega-commit.
