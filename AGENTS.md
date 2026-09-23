# Inkloom engineering rules

## Current documentation

- Read `CURRENT_REQUIREMENTS.md` for release status and user priorities; `REQUESTED_FEATURE_CATALOG.md` is the leaf-level checklist. Mark a leaf complete only after its full stated behavior has a production path and automated evidence; leave partial or unverified behavior unchecked.
- Use `INTERACTION_CONTRACTS.md` for interaction constraints and `DOCUMENTATION.md` for document ownership. Update the current status file instead of creating a competing versioned gap matrix.
- Historical plans are archived outside the repository. Old reports, branches, stashes and test artifacts are not evidence that a feature is currently shipped.
- Usage thresholds belong only to the task round specified by the user; do not embed old limits in long-lived product plans.

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

## Active scope cleanup

- Scope control is active cleanup, not only a ban on future features. Audit existing modules as `CORE_PAINTING`, `CORE_NOTEBOOK`, `REQUIRED_PLATFORM_INFRA`, `DELETE_CANDIDATE`, or `UNCERTAIN`.
- Delete confirmed non-core production code instead of hiding it behind disabled UI, feature flags, unused interfaces, compatibility wrappers, or future-facing TODOs.
- A deletion must remove its source, tests, resources, navigation, dependencies, persistence/schema state, adapters, fixtures, and stale documentation when those artifacts have no remaining owner.
- Prefer a direct local implementation when there is only one production backend. Do not keep repository, sync, cloud, remote, account, marketplace, plugin, or multi-tenant abstractions for hypothetical future implementations.
- Keep `tasks/SCOPE_CLEANUP_AUDIT.md` as the current repository-wide scope ledger with only `KEEP`, `SIMPLIFY`, `DELETE`, or `NEEDS_REVIEW` recommendations. Start with independently verifiable `DELETE NOW` items.
- Scope deletion commits must remain separate from painting or notebook feature commits.

## Product behavior research

- Before implementing or changing a drawing, selection, brush-library, layer, canvas-navigation, or stylus interaction, research the equivalent behavior in at least two current mainstream drawing or handwriting products.
- Prefer official manuals, platform documentation, and reproducible device behavior. Record the sources and the chosen interaction contract in the task notes or commit message before coding.
- Match established user expectations (including thresholds, drag targets, cancellation, visual feedback, and accessibility) instead of inventing a substitute interaction.
- Do not copy proprietary artwork, icons, names, or source code. Reproduce behavior with Inkloom's user-requested deep-gray background, neutral black/white controls, original icons and original implementation.

## Regression requirements

- No silent runtime gap: every imported brush, tool, or operation that reaches a production dispatcher must terminate as exactly one of `SUPPORTED_AND_EXECUTED` or `UNSUPPORTED_WITH_EXPLICIT_REASON`. A parsed or recognized feature must never produce no commit, no rejection, or an empty result without an explicit terminal reason.
- Every input or canvas change must test stylus drawing, stylus hover, erasing, brush switching, two-finger zoom/pan, and drawing after zoom.
- Every reorder interaction must have both operation-level tests and an Android interaction test for the actual long-press/drag path.
- Imported brush changes must be exercised with SUT, ABR, and Procreate fixtures when they are available locally.
- Treat `/Users/doudouda/Downloads/2/` as the primary authorized real-brush fixture root. Maintain an ignored `tasks/USER_BRUSH_CORPUS.md` manifest without committing proprietary brush files. A required real-fixture test must report `LOCAL_FIXTURE_REQUIRED` when its corpus is unavailable; absence must not silently pass.
- Every production change must pass JVM tests, relevant Android instrumented tests, an Android 15 emulator smoke run, relevant visual/raster regressions, lint, and `git diff --check` before delivery. Painting changes additionally run the affected stylus, pressure, tilt, hover, eraser, brush-switch, zoom/rotate-then-draw, active-delta, generation-barrier, selection/mask/clipping, import/editor, and persistence gates.
- Emulator success is not hardware verification. Mark MovinkPad-specific behavior `DEVICE_PENDING` until the user validates it on the primary device, then and only then mark it `DEVICE_VERIFIED`.
- Commit and push incrementally: one small feature, fix, migration step, or mechanical cleanup per reversible commit. Do not accumulate a multi-feature or whole-phase mega-commit.
- Each commit must compile and be independently revertible; do not create a commit that requires an uncommitted follow-up to restore a buildable repository.

## APK delivery preference

- Deliver only the latest tested Debug APK to the user. Do not build or deliver a Profile APK unless the user explicitly requests it again. This preference supersedes older milestone instructions requiring both APK variants.
- Place the deliverable in `dist/` with the version, source commit, and `debug` in its filename, and provide a direct clickable file link in the final response. Keep the APK and its source/hash evidence consistent.
- After validating every new APK and its source/hash evidence, remove superseded APKs, their obsolete delivery sidecars, unused Profile outputs, and known disposable intermediate files. Keep only the current Debug delivery in `dist/`; preserve real brush fixtures, source files, active build caches, and useful regression evidence. Use `scripts/clean_generated_artifacts.py --keep <current-debug-apk> --apply` after reviewing its dry run. Never delete the current deliverable before its replacement has been validated.

- After validating each new Debug delivery, run `scripts/sync_debug_delivery.py --apk <current-debug-apk>` to copy the APK and evidence into Google Drive Desktop at `My Drive/Inkloom/Debug`. Remove only superseded managed deliveries after the new Drive copy passes SHA-256 verification. Do not upload build caches, source fixtures, or Profile APKs; distinguish local sync-folder verification from cloud download verification.

## Dual-notebook workspace requirement — deferred by user

- Latest user instruction (2026-09-08): split view is not needed for now. Do not implement it in the current completion work; retain the design for a later explicit request.

- Support two notebooks in the Editor with two explicit modes: both panes editable, or one read-only reference pane and one editable pane. Both panes retain independent page and viewport state.
- Treat this as core notebook/painting scope. Keep one durable store/journal owner; isolate pane input, history, tool state, and asynchronous import/export targets. Read-only must be enforced at mutation boundaries, not only by hiding controls.
- Detailed confirmed interaction and acceptance design: `tasks/DUAL_NOTEBOOK_SPLIT_VIEW.md`. This is a requested capability, not a claim that the current APK implements it.
