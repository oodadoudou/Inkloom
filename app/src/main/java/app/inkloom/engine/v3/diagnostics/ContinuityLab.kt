package app.inkloom.engine.v3.diagnostics

import app.inkloom.document.BrushEngineKind
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.brush.BrushProgramRuntime
import kotlin.math.hypot

/**
 * Deterministic dab-continuity lab for brush quality. Given one fixed StrokeInput and brush it
 * classifies the beaded-line failure modes independently of UI feel:
 *
 *  - GEOMETRIC_GAP: consecutive deposits do not overlap (a real hole between dabs/segments).
 *  - COVERAGE_PUMPING: deposits overlap but the alpha/compositing periodicizes (darker at every
 *    join or stamp overlap) -- detected on the raster by the instrumented profile test.
 *  - RADIUS_PUMPING: the deposited radius varies periodically along the stroke even when the
 *    input pressure/velocity are constant.
 *  - TIP_PERIODICITY: the stamp texture itself repeats at the spacing frequency.
 *
 * This file is pure Kotlin (no Android) so the geometry classification is JVM-deterministic; the
 * raster alpha/coverage profile is measured in the instrumented ContinuityLabProfileTest.
 */
internal object ContinuityLab {
    enum class EmissionModel { STAMPED, POLYLINE }

    enum class Failure { GEOMETRIC_GAP, COVERAGE_PUMPING, RADIUS_PUMPING, TIP_PERIODICITY, NONE }

    const val REFERENCE_WIDTH = 1600f

    /** Overlap threshold: a gap ratio above this means consecutive deposits do not overlap. */
    const val GEOMETRIC_GAP_RATIO = 1f

    /** Radius variation coefficient above which a constant-input stroke shows radius pumping. */
    const val RADIUS_PUMPING_CV = .12f

    data class DepositMetrics(
        val index: Int,
        val x: Float,
        val y: Float,
        val radiusPx: Float,
        val gapToNextPx: Float,
        val gapRatio: Float,
    )

    data class Report(
        val emissionModel: EmissionModel,
        val depositCount: Int,
        val radiusMeanPx: Float,
        val radiusCv: Float,
        val gapRatioMean: Float,
        val gapRatioMax: Float,
        val geometricGapRatio: Float,
        val constantInput: Boolean,
        val failures: List<Failure>,
    )

    fun emissionModel(stroke: InkStroke): EmissionModel = when {
        stroke.tipAssetPath != null || stroke.tipAssetPaths.isNotEmpty() ||
            stroke.spray || stroke.stampCount > 1 ||
            stroke.engineKind == BrushEngineKind.AIRBRUSH ||
            stroke.brushId.startsWith("system-decoration-") ||
            GRAPHITE_BRUSH_IDS.contains(stroke.brushId) -> EmissionModel.STAMPED
        else -> EmissionModel.POLYLINE
    }

    fun analyze(stroke: InkStroke, viewportWidth: Float, viewportHeight: Float): Report {
        val model = emissionModel(stroke)
        val deposits = deposits(stroke, viewportWidth, viewportHeight, model)
        if (deposits.isEmpty()) {
            return Report(model, 0, 0f, 0f, 0f, 0f, 0f, false, listOf(Failure.NONE))
        }
        val radiusMean = deposits.map { it.radiusPx }.average().toFloat()
        val radiusStd = deposits.map { (it.radiusPx - radiusMean) * (it.radiusPx - radiusMean) }.average().toFloat().let { kotlin.math.sqrt(it) }
        val radiusCv = if (radiusMean > .0001f) radiusStd / radiusMean else 0f
        val gapRatios = deposits.filter { it.gapToNextPx >= 0f }.map { it.gapRatio }
        val gapRatioMean = gapRatios.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
        val gapRatioMax = gapRatios.maxOrNull() ?: 0f
        val geometricGapRatio = gapRatios.count { it > GEOMETRIC_GAP_RATIO }.toFloat() / gapRatios.size.coerceAtLeast(1)
        // Radius pumping is only meaningful when the input sensors were constant: a pressure ramp
        // legitimately changes radius, so it must never be classified as a periodic artifact.
        val constantInput = stroke.points.map { it.pressure }.distinct().size <= 1
        val radiusPumping = constantInput && radiusCv > RADIUS_PUMPING_CV
        val failures = buildList {
            if (gapRatios.isNotEmpty() && gapRatios.any { it > GEOMETRIC_GAP_RATIO }) add(Failure.GEOMETRIC_GAP)
            if (radiusPumping) add(Failure.RADIUS_PUMPING)
            if (isEmpty()) add(Failure.NONE)
        }
        return Report(model, deposits.size, radiusMean, radiusCv, gapRatioMean, gapRatioMax, geometricGapRatio, constantInput, failures)
    }

    /** The per-deposit geometry (dab/segment centers, radius, gap-to-next), for the lab and audits. */
    fun deposits(stroke: InkStroke, viewportWidth: Float, viewportHeight: Float): List<DepositMetrics> =
        deposits(stroke, viewportWidth, viewportHeight, emissionModel(stroke))

    private fun deposits(stroke: InkStroke, viewportWidth: Float, viewportHeight: Float, model: EmissionModel): List<DepositMetrics> =
        when (model) {
            EmissionModel.STAMPED -> stampedDeposits(stroke, viewportWidth, viewportHeight)
            EmissionModel.POLYLINE -> polylineDeposits(stroke, viewportWidth, viewportHeight)
        }

    private fun stampedDeposits(stroke: InkStroke, viewportWidth: Float, viewportHeight: Float): List<DepositMetrics> {
        val dabs = BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)
        if (dabs.size < 2) return emptyList()
        return dabs.mapIndexed { index, dab ->
            val diameter = brushDiameterPx(stroke, dab, viewportWidth)
            val next = dabs.getOrNull(index + 1)
            val gap = if (next == null) -1f else screenDistance(dab, next, viewportWidth, viewportHeight)
            DepositMetrics(
                index = index,
                x = dab.x * viewportWidth,
                y = dab.y * viewportHeight,
                radiusPx = diameter / 2f,
                gapToNextPx = gap,
                gapRatio = if (next == null) 0f else gap / ((diameter + brushDiameterPx(stroke, next, viewportWidth)) / 2f),
            )
        }
    }

    private fun polylineDeposits(stroke: InkStroke, viewportWidth: Float, viewportHeight: Float): List<DepositMetrics> {
        val points = stroke.points
        if (points.size < 2) return emptyList()
        return points.mapIndexed { index, point ->
            val diameter = brushDiameterPx(stroke, point, viewportWidth)
            val next = points.getOrNull(index + 1)
            val gap = if (next == null) -1f else screenDistance(point, next, viewportWidth, viewportHeight)
            DepositMetrics(
                index = index,
                x = point.x * viewportWidth,
                y = point.y * viewportHeight,
                radiusPx = diameter / 2f,
                gapToNextPx = gap,
                gapRatio = if (next == null) 0f else gap / ((diameter + brushDiameterPx(stroke, next, viewportWidth)) / 2f),
            )
        }
    }

    private fun brushDiameterPx(stroke: InkStroke, point: StrokePoint, viewportWidth: Float): Float {
        val scale = viewportWidth.coerceAtLeast(1f) / REFERENCE_WIDTH
        val dynamics = BrushProgramRuntime.evaluateDab(stroke, point)
        return (stroke.width * scale * dynamics.size).coerceAtLeast(.5f)
    }

    private fun screenDistance(a: StrokePoint, b: StrokePoint, viewportWidth: Float, viewportHeight: Float): Float =
        hypot(((b.x - a.x) * viewportWidth).toDouble(), ((b.y - a.y) * viewportHeight).toDouble()).toFloat()

    private val GRAPHITE_BRUSH_IDS = setOf(
        "system-technical-pencil",
        "system-h-pencil",
        "system-hb-pencil",
        "system-2b-pencil",
        "system-6b-pencil",
    )
}
