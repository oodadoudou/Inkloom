# Known Failures / Baseline Quarantine

This file is the single source of truth for instrumented-test failures that exist on a
clean baseline. The rule: **a permanently non-green baseline must never hide later
regressions.** Every failure below is pinned to the commit where it was first confirmed,
records its exact failure signature, states whether it is deterministic or flaky, and
assigns an expected fix milestone.

Status legend:

- `DETERMINISTIC` — reproduces on every run at the pinned commit.
- `FLAKY` — passes when run in isolation, intermittently fails under full-suite load.
- `QUARANTINED` — intentionally not fixed yet; tracked separately. A real regression that
  appears later must be raised as a new failure, not merged into these rows.

---

## KF-001: `classicPs19HasNoPeriodicJoinBeads` — PS19 continuous ribbon beading

- **File:** `app/src/androidTest/java/app/inkloom/SystemBrushPerformanceTest.kt`
- **First confirmed at:** commit `4035ffe` (feat(v3): PENCIL_RENDERER_V4 graphite runtime semantics). Confirmed reproducible on the clean baseline at `4035ffe~` and at `4035ffe` (unrelated to the V4 changes).
- **Status:** DETERMINISTIC — reproduces on every run.
- **Failure signature:**
  `java.lang.AssertionError: Continuous PS19 centerline alpha varied like overlapping round stamps: 203..227`
  (assertion `(maxAlpha - minAlpha) <= 2` on the `system-ps19` centerline alpha along
  `x in 140..860 step 5`; observed range width = 24).
- **Category:** Brush Continuity. The `system-ps19` preset (Photoshop legacy No. 19,
  spacing `.01`, intended continuous round stroke) still shows periodic join beading along
  the centerline instead of a uniform continuous ribbon.
- **Classification:** real renderer gap, not flaky. The default Ink `system-pen`
  continuous-ribbon goldens pass (`ReferencePresetGoldenTest`), so this is specific to the
  PS19 preset path (round hard tip stamped at spacing `.01` with `flow=.55`, no pressure
  dynamics). The beading amplitude (24/255) is far above the `<=2` continuity budget.
- **Expected fix milestone:** BRUSH_PRESET_V4_FOUNDATION, item "Require V3 → V4 raster
  golden parity before migrating additional brushes". PS19 is a non-reference legacy brush;
  during its V4 migration, the continuous-ribbon emitter must be verified to eliminate this
  beading. Until then it is quarantined and tracked here.
- **How to reproduce:**
  ```bash
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.inkloom.SystemBrushPerformanceTest#classicPs19HasNoPeriodicJoinBeads
  ```
- **Quarantine note:** do NOT attribute unrelated renderer regressions to this row. If the
  failure signature changes (e.g. becomes a crash, or the alpha range moves), raise a new issue.

---

## KF-002: `sparseAlphaTileDiscoveryStaysWithinSelectionFrameBudget` — selection tile-scan frame budget flake

- **File:** `app/src/androidTest/java/app/inkloom/InkCanvasLassoTest.kt`
- **First confirmed at:** commit `4035ffe` (and on the clean baseline at `4035ffe~`). Only
  observed during a full instrumented-suite run; passes when run in isolation.
- **Status:** FLAKY — intermittent under full-suite load, passes repeatedly in isolation.
- **Failure signature:**
  `java.lang.AssertionError: Sparse alpha tile scan P95 was 49.13ms`
  (assertion `p95Ms < 33.3f` over 8 `SparseAlphaMask.fromBitmap(2048x2048)` iterations).
- **Category:** Selection mask performance (native sparse alpha tile scan).
- **Classification:** timing-sensitive flake on the emulator under suite load. The 2048x2048
  scan P95 budget (33.3ms) is sensitive to emulator CPU contention; no code path touched by
  recent work is implicated (the mask scan is selection tooling, independent of brush
  rendering).
- **Expected fix milestone:** none assigned to a feature milestone; it is a CI/emulator
  robustness concern. Tracked for quarantine and periodic re-validation. If it becomes
  deterministic (always > 33.3ms) raise it as a performance regression with profiling.
- **How to reproduce (intermittent):** run the full instrumented suite:
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```
- **Quarantine note:** do NOT treat transient P95 overages in this test as brush-renderer
  regressions. Brush renderer perf is covered by
  `representativeFactoryBrushesKeepLiveAndCommitWorkInsideAFrame` (16.7ms budget).

---

## KF-003: `repeatedViewportFillThresholdUpdatesStayInteractive` / `committedSelectionOverlayStaysWithinInteractiveFrameBudget` — suite-load frame-budget flakes

- **File:** `app/src/androidTest/java/app/inkloom/FloodFillTest.kt` and `app/src/androidTest/java/app/inkloom/LassoSelectionModesTest.kt`
- **First confirmed at:** commit `f4bc3e9` (baseline, no brushpresetv4 changes; both tests pass when run in isolation and passed in the full-suite run at `4035ffe`).
- **Status:** FLAKY — intermittent under full-suite load only; each passes when run in isolation.
- **Failure signature:**
  - `repeatedViewportFillThresholdUpdatesStayInteractive` — interactive frame-budget assertion in the flood-fill threshold test.
  - `committedSelectionOverlayStaysWithinInteractiveFrameBudget` — interactive frame-budget assertion in the lasso selection overlay test.
- **Category:** Interactive frame budget under emulator suite load (flood fill / selection tooling).
- **Classification:** same family as KF-002 — timing-sensitive flakes on the emulator under suite load. Neither test exercises brush rendering or the brushpresetv4 schema/compiler; no brush-renderer code path is implicated.
- **Expected fix milestone:** none assigned to a feature milestone; CI/emulator robustness concern, quarantined with KF-002. If either becomes deterministic (fails in isolation) raise it as a real regression.
- **How to reproduce (intermittent):** run the full instrumented suite (`./gradlew connectedDebugAndroidTest`).
- **Quarantine note:** do NOT treat transient frame-budget overages in these two tests as brush-renderer regressions. Brush renderer perf is covered by `representativeFactoryBrushesKeepLiveAndCommitWorkInsideAFrame` (16.7ms budget).

---

## Conventions

- New failures: never silently merge into these rows. Create a new KF entry + GitHub issue.
- Before a V4 milestone ships, re-run both rows at the target commit and update the
  `First confirmed at` / `Expected fix milestone` fields.
- `FLAKY` rows may be re-run in isolation to confirm current status; a green isolation run
  does not close the row, it just re-confirms the flake.
