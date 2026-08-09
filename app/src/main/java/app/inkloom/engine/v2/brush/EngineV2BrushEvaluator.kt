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
    data class DabDynamics(
        val size: Float,
        val opacity: Float,
        val flow: Float,
        val thickness: Float,
        val blur: Float,
        val wetAlpha: Float,
    ) {
        val depositedAlpha: Float get() = (opacity * flow * wetAlpha).coerceIn(0f, 1f)
    }

    /**
     * Compiles every input-driven scalar used by a dab into one deterministic result. Keeping
     * this evaluation outside both renderers prevents live, tile and export paths from drifting.
     */
    fun evaluateDab(stroke: InkStroke, point: StrokePoint, dual: Boolean = false): DabDynamics {
        val pressure = point.pressure.coerceIn(0f, 1f)
        val sizeDynamics = if (dual) stroke.dualSizeDynamics else null
        val flowDynamics = if (dual) stroke.dualFlowDynamics else null
        val thicknessDynamics = if (dual) stroke.dualThicknessDynamics else null
        val size = if (sizeDynamics != null) {
            pressure(sizeDynamics.pressureEnabled, sizeDynamics.pressureMinimum, sizeDynamics.pressureCurve, pressure) *
                secondary(sizeDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SIZE)
        } else {
            pressure(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) *
                secondary(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
        }
        val opacity = pressure(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) *
            secondary(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
        val flow = if (flowDynamics != null) {
            pressure(flowDynamics.pressureEnabled, flowDynamics.pressureMinimum, flowDynamics.pressureCurve, pressure) *
                secondary(flowDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_FLOW)
        } else {
            pressure(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) *
                secondary(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
        }
        val thickness = if (thicknessDynamics != null) {
            pressure(thicknessDynamics.pressureEnabled, thicknessDynamics.pressureMinimum, thicknessDynamics.pressureCurve, pressure) *
                secondary(thicknessDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_THICKNESS)
        } else {
            pressure(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, pressure) *
                secondary(stroke.thicknessSecondaryDynamics, point, stroke.renderSeed, SALT_THICKNESS)
        }
        val blur = pressure(stroke.blurPressureEnabled, stroke.blurPressureMinimum, stroke.blurPressureCurve, pressure) *
            secondary(stroke.blurSecondaryDynamics, point, stroke.renderSeed, SALT_BLUR)
        return DabDynamics(
            size = size,
            opacity = opacity,
            flow = flow,
            thickness = thickness,
            blur = blur,
            wetAlpha = app.inkloom.document.WetMixDynamics.alphaMultiplier(stroke, point),
        )
    }

    fun sampleStampPoints(stroke: InkStroke, width: Float, height: Float, dual: Boolean): List<StrokePoint> {
        return StrokeStampSampler.sample(
            stroke.points,
            width.coerceAtLeast(1f),
            height.coerceAtLeast(1f),
            includeStationaryPoints = stroke.continuousPlot,
            spacingFor = spacingFunction(stroke, width, dual),
        )
    }

    fun newIncrementalSampler(stroke: InkStroke, width: Float, height: Float, dual: Boolean): StrokeStampSampler.Incremental =
        StrokeStampSampler.Incremental(width.coerceAtLeast(1f), height.coerceAtLeast(1f), includeStationaryPoints = stroke.continuousPlot)

    fun updateIncrementalSampler(
        sampler: StrokeStampSampler.Incremental,
        stroke: InkStroke,
        width: Float,
        dual: Boolean,
    ): List<StrokePoint> = sampler.update(stroke.points, spacingFunction(stroke, width, dual))

    private fun spacingFunction(stroke: InkStroke, width: Float, dual: Boolean): (StrokePoint) -> Float {
        val pageWidth = width.coerceAtLeast(1f)
        val referenceScale = pageWidth / REFERENCE_WIDTH
        return { point ->
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
    private const val SALT_OPACITY = 31
    private const val SALT_FLOW = 47
    private const val SALT_SPACING = 53
    private const val SALT_BLUR = 89
    private const val SALT_DUAL_SIZE = 101
    private const val SALT_DUAL_FLOW = 103
    private const val SALT_DUAL_SPACING = 107
    private const val SALT_DUAL_THICKNESS = 109
    private const val SALT_THICKNESS = 71
}
