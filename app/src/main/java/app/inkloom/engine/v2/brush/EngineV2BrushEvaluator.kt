package app.inkloom.engine.v2.brush

import app.inkloom.document.BrushDynamics
import app.inkloom.document.CspBrushGap
import app.inkloom.document.InkStroke
import app.inkloom.document.SprayDynamics
import app.inkloom.document.SprayGeometry
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeStampSampler

/** Shared deterministic evaluator used by realtime, tile replay, thumbnail, PNG and PDF paths. */
internal object EngineV2BrushEvaluator {
    fun sampleStampPoints(stroke: InkStroke, width: Float, height: Float, dual: Boolean): List<StrokePoint> {
        val pageWidth = width.coerceAtLeast(1f)
        val referenceScale = pageWidth / REFERENCE_WIDTH
        return StrokeStampSampler.sample(
            stroke.points,
            pageWidth,
            height.coerceAtLeast(1f),
            includeStationaryPoints = stroke.continuousPlot,
        ) { point ->
            val pressure = point.pressure.coerceIn(0f, 1f)
            val diameter: Float
            val spacingDynamic: Float
            val gapRatio: Float
            val divisor: Int
            if (dual) {
                diameter = (
                    stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) *
                        pressure(stroke.dualSizeDynamics.pressureEnabled, stroke.dualSizeDynamics.pressureMinimum, stroke.dualSizeDynamics.pressureCurve, pressure) *
                        secondary(stroke.dualSizeDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SIZE)
                    ).coerceAtLeast(1f)
                spacingDynamic = pressure(stroke.dualSpacingDynamics.pressureEnabled, stroke.dualSpacingDynamics.pressureMinimum, stroke.dualSpacingDynamics.pressureCurve, pressure) *
                    secondary(stroke.dualSpacingDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SPACING)
                gapRatio = CspBrushGap.ratio(stroke.dualSpacingMode, stroke.dualSpacing).let { if (stroke.dualRibbon) it.coerceAtMost(.06f) else it }
                divisor = if (stroke.dualSpray) SprayGeometry.particlesPerPlot(SprayDynamics.dualDensity(stroke, point)) else 1
            } else {
                diameter = (
                    stroke.width * referenceScale *
                        pressure(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) *
                        secondary(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
                    ).coerceAtLeast(1f)
                spacingDynamic = pressure(stroke.spacingPressureEnabled, stroke.spacingPressureMinimum, stroke.spacingPressureCurve, pressure) *
                    secondary(stroke.spacingSecondaryDynamics, point, stroke.renderSeed, SALT_SPACING)
                gapRatio = CspBrushGap.ratio(stroke.spacingMode, stroke.spacing).let { if (stroke.ribbon) it.coerceAtMost(.06f) else it }
                divisor = if (stroke.spray) SprayGeometry.particlesPerPlot(SprayDynamics.density(stroke, point)) else 1
            }
            diameter * gapRatio * spacingDynamic.coerceIn(.01f, 1f) / divisor
        }
    }

    private fun pressure(enabled: Boolean, minimum: Float, curve: List<app.inkloom.document.BrushCurvePoint>, input: Float): Float =
        BrushDynamics.pressure(enabled, minimum, curve, input)

    private fun secondary(
        dynamics: app.inkloom.document.BrushSecondaryDynamics,
        point: StrokePoint,
        strokeSeed: Int,
        salt: Int,
    ): Float = BrushDynamics.secondary(dynamics, point, strokeSeed, salt)

    private const val REFERENCE_WIDTH = 1600f
    private const val SALT_SIZE = 11
    private const val SALT_SPACING = 53
    private const val SALT_DUAL_SIZE = 101
    private const val SALT_DUAL_SPACING = 107
}
