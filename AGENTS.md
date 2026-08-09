# Inkloom engineering rules

## Product behavior research

- Before implementing or changing a drawing, selection, brush-library, layer, canvas-navigation, or stylus interaction, research the equivalent behavior in at least two current mainstream drawing or handwriting products.
- Prefer official manuals, platform documentation, and reproducible device behavior. Record the sources and the chosen interaction contract in the task notes or commit message before coding.
- Match established user expectations (including thresholds, drag targets, cancellation, visual feedback, and accessibility) instead of inventing a substitute interaction.
- Do not copy proprietary artwork, icons, names, or source code. Reproduce behavior with Inkloom's white visual system and original implementation.

## Regression requirements

- Every input or canvas change must test stylus drawing, stylus hover, erasing, brush switching, two-finger zoom/pan, and drawing after zoom.
- Every reorder interaction must have both operation-level tests and an Android interaction test for the actual long-press/drag path.
- Imported brush changes must be exercised with SUT, ABR, and Procreate fixtures when they are available locally.
- Ship only after JVM tests and the Android 15 instrumented suite pass. Keep unrelated features in separate, reversible commits.
