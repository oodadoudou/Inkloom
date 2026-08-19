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
- `FIXED` — resolved; row kept as history.

---

## KF-001: `classicPs19HasNoPeriodicJoinBeads` — PS19 continuous ribbon beading

- **File:** `app/src/androidTest/java/app/inkloom/SystemBrushPerformanceTest.kt`
- **First confirmed at:** commit `4035ffe` (feat(v3): PENCIL_RENDERER_V4 graphite runtime semantics). Confirmed reproducible on the clean baseline at `4035ffe~` and at `4035ffe` (unrelated to the V4 changes).
- **Status:** FIXED — closed at commit `(9d38858)` (see resolution below); passes in isolation and in a full-class run.
- **Failure signature (historical):**
  `java.lang.AssertionError: Continuous PS19 centerline alpha varied like overlapping round stamps: 203..227`
  (assertion `(maxAlpha - minAlpha) <= 2` on the `system-ps19` centerline alpha along
  `x in 140..860 step 5`; observed range width = 24).
- **Category:** Brush Continuity.
- **Root cause:** `usesContinuousRibbon` rejected `system-ps19` when `flow < .999f`. The
  classic PS19 test stroke uses `flow = .55`, so it fell back to stamped round dabs, which
  bead at every join. The ribbon emitter already carries flow into the single filled ribbon
  via `alphaSum`, so the sub-100% flow gate was over-strict for PS19.
- **Resolution:** `usesContinuousRibbon` now keeps the full-flow gate only for `system-pen`;
  `system-ps19` renders as one seamless ribbon regardless of flow (as long as it has no
  pressure-opacity/flow dynamics, which the ribbon cannot express per-dab).
- **Verification:** `classicPs19HasNoPeriodicJoinBeads` passes in isolation and in the full
  `SystemBrushPerformanceTest` class run. Reference Ink (`system-pen`, flow=1) goldens
  unchanged.

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

## KF-003: suite-load frame-budget flakes — flood fill / selection overlay / pixel-layer projection

- **File:** `app/src/androidTest/java/app/inkloom/FloodFillTest.kt`, `app/src/androidTest/java/app/inkloom/LassoSelectionModesTest.kt`, `app/src/androidTest/java/app/inkloom/PixelLayerProjectionBenchmarkTest.kt`
- **First confirmed at:** commit `f4bc3e9` (baseline, no brushpresetv4 changes; all pass when run in isolation and passed in the full-suite run at `4035ffe`).
- **Status:** FLAKY — intermittent under full-suite load only; each passes when run in isolation.
- **Failure signatures:**
  - `repeatedViewportFillThresholdUpdatesStayInteractive` — interactive frame-budget assertion in the flood-fill threshold test.
  - `committedSelectionOverlayStaysWithinInteractiveFrameBudget` — interactive frame-budget assertion in the lasso selection overlay test.
  - `productionCanvasChangedTileRemainsWithinFrameBudget` — frame-budget assertion projecting 50x 8192x8192 pixel-layer tile surfaces (projection/raster pipeline, independent of brush rendering).
- **Category:** Interactive / projection frame budget under emulator suite load (flood fill, selection tooling, pixel-layer projection).
- **Classification:** same family as KF-002 — timing-sensitive flakes on the emulator under suite load. None of these tests exercise brush rendering or the brushpresetv4 schema/compiler; no brush-renderer code path is implicated.
- **Expected fix milestone:** none assigned to a feature milestone; CI/emulator robustness concern, quarantined with KF-002. If any becomes deterministic (fails in isolation) raise it as a real regression.
- **How to reproduce (intermittent):** run the full instrumented suite (`./gradlew connectedDebugAndroidTest`).
- **Quarantine note:** do NOT treat transient frame-budget overages in these tests as brush-renderer regressions. Brush renderer perf is covered by `representativeFactoryBrushesKeepLiveAndCommitWorkInsideAFrame` (16.7ms budget).

---

## Conventions

- New failures: never silently merge into these rows. Create a new KF entry + GitHub issue.
- Before a V4 milestone ships, re-run both rows at the target commit and update the
  `First confirmed at` / `Expected fix milestone` fields.
- `FLAKY` rows may be re-run in isolation to confirm current status; a green isolation run
  does not close the row, it just re-confirms the flake.
