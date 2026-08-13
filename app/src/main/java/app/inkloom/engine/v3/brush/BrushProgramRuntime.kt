package app.inkloom.engine.v3.brush

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.CspBrushGap
import app.inkloom.document.InkStroke
import app.inkloom.document.SprayDynamics
import app.inkloom.document.SprayGeometry
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.WetMixDynamics
import java.util.LinkedHashMap

/** Immutable, format-neutral scalar program shared by live, retained, thumbnail and export paths. */
internal class BrushProgram private constructor(
    private val primary: CoatProgram,
    private val secondary: CoatProgram,
    private val opacity: ScalarInputProgram,
    private val blur: ScalarInputProgram,
    private val continuousPlot: Boolean,
) {
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

    fun evaluateDab(stroke: InkStroke, point: StrokePoint, dual: Boolean): DabDynamics {
        val coat = if (dual) secondary else primary
        return DabDynamics(
            size = coat.size.evaluate(point, stroke.renderSeed) * WetMixDynamics.sizeMultiplier(stroke, point),
            opacity = opacity.evaluate(point, stroke.renderSeed),
            flow = coat.flow.evaluate(point, stroke.renderSeed),
            thickness = coat.thickness.evaluate(point, stroke.renderSeed),
            blur = blur.evaluate(point, stroke.renderSeed),
            wetAlpha = WetMixDynamics.alphaMultiplier(stroke, point),
        )
    }

    fun sampleStampPoints(stroke: InkStroke, width: Float, height: Float, dual: Boolean): List<StrokePoint> =
        StrokeStampSampler.sample(
            stroke.points,
            width.coerceAtLeast(1f),
            height.coerceAtLeast(1f),
            includeStationaryPoints = continuousPlot,
            spacingFor = spacingFunction(stroke, width, dual),
        )

    fun newIncrementalSampler(width: Float, height: Float): StrokeStampSampler.Incremental =
        StrokeStampSampler.Incremental(
            width.coerceAtLeast(1f),
            height.coerceAtLeast(1f),
            includeStationaryPoints = continuousPlot,
        )

    fun updateIncrementalSampler(
        sampler: StrokeStampSampler.Incremental,
        stroke: InkStroke,
        width: Float,
        dual: Boolean,
    ): List<StrokePoint> = sampler.update(stroke.points, spacingFunction(stroke, width, dual))

    private fun spacingFunction(stroke: InkStroke, width: Float, dual: Boolean): (StrokePoint) -> Float {
        val pageWidth = width.coerceAtLeast(1f)
        val referenceScale = pageWidth / REFERENCE_WIDTH
        val coat = if (dual) secondary else primary
        return { point ->
            val diameter = (
                stroke.width * referenceScale * coat.sizeScale *
                    coat.size.evaluate(point, stroke.renderSeed) * WetMixDynamics.sizeMultiplier(stroke, point)
                ).coerceAtLeast(1f)
            val spacingDynamic = coat.spacing.evaluate(point, stroke.renderSeed)
            val divisor = when {
                dual && coat.spray -> SprayGeometry.particlesPerPlot(SprayDynamics.dualDensity(stroke, point))
                coat.spray -> SprayGeometry.particlesPerPlot(SprayDynamics.density(stroke, point))
                else -> 1
            }
            diameter * coat.gapRatio * spacingDynamic.coerceIn(.01f, 1f) / divisor
        }
    }

    private data class CoatProgram(
        val size: ScalarInputProgram,
        val flow: ScalarInputProgram,
        val thickness: ScalarInputProgram,
        val spacing: ScalarInputProgram,
        val gapRatio: Float,
        val sizeScale: Float,
        val spray: Boolean,
    )

    private data class ScalarInputProgram(
        val pressureEnabled: Boolean,
        val pressureMinimum: Float,
        val pressureCurve: List<BrushCurvePoint>,
        val secondary: BrushSecondaryDynamics,
        val salt: Int,
    ) {
        fun evaluate(point: StrokePoint, strokeSeed: Int): Float =
            BrushDynamics.pressure(pressureEnabled, pressureMinimum, pressureCurve, point.pressure.coerceIn(0f, 1f)) *
                BrushDynamics.secondary(secondary, point, strokeSeed, salt)
    }

    companion object {
        fun compile(stroke: InkStroke): BrushProgram {
            fun scalar(
                enabled: Boolean,
                minimum: Float,
                curve: List<BrushCurvePoint>,
                secondary: BrushSecondaryDynamics,
                salt: Int,
            ) = ScalarInputProgram(enabled, minimum, curve.toList(), secondary, salt)

            val primary = CoatProgram(
                size = scalar(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, stroke.sizeSecondaryDynamics, SALT_SIZE),
                flow = scalar(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, stroke.flowSecondaryDynamics, SALT_FLOW),
                thickness = scalar(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, stroke.thicknessSecondaryDynamics, SALT_THICKNESS),
                spacing = scalar(stroke.spacingPressureEnabled, stroke.spacingPressureMinimum, stroke.spacingPressureCurve, stroke.spacingSecondaryDynamics, SALT_SPACING),
                gapRatio = CspBrushGap.ratio(stroke.spacingMode, stroke.spacing).let { if (stroke.ribbon) it.coerceAtMost(.06f) else it },
                sizeScale = 1f,
                spray = stroke.spray,
            )
            val dual = CoatProgram(
                size = scalar(stroke.dualSizeDynamics.pressureEnabled, stroke.dualSizeDynamics.pressureMinimum, stroke.dualSizeDynamics.pressureCurve, stroke.dualSizeDynamics.secondary, SALT_DUAL_SIZE),
                flow = scalar(stroke.dualFlowDynamics.pressureEnabled, stroke.dualFlowDynamics.pressureMinimum, stroke.dualFlowDynamics.pressureCurve, stroke.dualFlowDynamics.secondary, SALT_DUAL_FLOW),
                thickness = scalar(stroke.dualThicknessDynamics.pressureEnabled, stroke.dualThicknessDynamics.pressureMinimum, stroke.dualThicknessDynamics.pressureCurve, stroke.dualThicknessDynamics.secondary, SALT_DUAL_THICKNESS),
                spacing = scalar(stroke.dualSpacingDynamics.pressureEnabled, stroke.dualSpacingDynamics.pressureMinimum, stroke.dualSpacingDynamics.pressureCurve, stroke.dualSpacingDynamics.secondary, SALT_DUAL_SPACING),
                gapRatio = CspBrushGap.ratio(stroke.dualSpacingMode, stroke.dualSpacing).let { if (stroke.dualRibbon) it.coerceAtMost(.06f) else it },
                sizeScale = stroke.dualSizeScale.coerceIn(.01f, 8f),
                spray = stroke.dualSpray,
            )
            return BrushProgram(
                primary = primary,
                secondary = dual,
                opacity = scalar(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, stroke.opacitySecondaryDynamics, SALT_OPACITY),
                blur = scalar(stroke.blurPressureEnabled, stroke.blurPressureMinimum, stroke.blurPressureCurve, stroke.blurSecondaryDynamics, SALT_BLUR),
                continuousPlot = stroke.continuousPlot,
            )
        }

        private const val REFERENCE_WIDTH = 1600f
        private const val SALT_SIZE = 11
        private const val SALT_OPACITY = 31
        private const val SALT_FLOW = 47
        private const val SALT_SPACING = 53
        private const val SALT_THICKNESS = 71
        private const val SALT_BLUR = 89
        private const val SALT_DUAL_SIZE = 101
        private const val SALT_DUAL_FLOW = 103
        private const val SALT_DUAL_SPACING = 107
        private const val SALT_DUAL_THICKNESS = 109
    }
}

/**
 * Bounded compiler/cache facade. Brush parameters are immutable during one contact; only its point
 * list grows. The point-free fingerprint also lets consecutive strokes and cursor preview reuse
 * one program without hashing trajectory history on the input/render hot path.
 */
internal object BrushProgramRuntime {
    private data class ProgramKey(val fingerprint: Long)
    private data class LastProgram(val source: InkStroke, val key: ProgramKey, val program: BrushProgram)

    private val cache = object : LinkedHashMap<ProgramKey, BrushProgram>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProgramKey, BrushProgram>?): Boolean = size > MAX_PROGRAMS
    }
    private val threadLastProgram = ThreadLocal<LastProgram?>()

    fun compile(stroke: InkStroke): BrushProgram {
        threadLastProgram.get()?.takeIf { it.source === stroke }?.let { return it.program }
        val key = ProgramKey(programFingerprint(stroke))
        threadLastProgram.get()?.takeIf { it.key == key }?.let {
            threadLastProgram.set(LastProgram(stroke, key, it.program))
            return it.program
        }
        val program = synchronized(cache) { cache.getOrPut(key) { BrushProgram.compile(stroke) } }
        threadLastProgram.set(LastProgram(stroke, key, program))
        return program
    }

    fun evaluateDab(stroke: InkStroke, point: StrokePoint, dual: Boolean = false): BrushProgram.DabDynamics =
        compile(stroke).evaluateDab(stroke, point, dual)

    fun sampleStampPoints(stroke: InkStroke, width: Float, height: Float, dual: Boolean): List<StrokePoint> =
        compile(stroke).sampleStampPoints(stroke, width, height, dual)

    @Suppress("UNUSED_PARAMETER")
    fun newIncrementalSampler(stroke: InkStroke, width: Float, height: Float, dual: Boolean): StrokeStampSampler.Incremental =
        compile(stroke).newIncrementalSampler(width, height)

    fun updateIncrementalSampler(
        sampler: StrokeStampSampler.Incremental,
        stroke: InkStroke,
        width: Float,
        dual: Boolean,
    ): List<StrokePoint> = compile(stroke).updateIncrementalSampler(sampler, stroke, width, dual)

    internal fun clearForTests() {
        synchronized(cache) { cache.clear() }
        threadLastProgram.remove()
    }

    /** Deliberately excludes points, color, layer and render seed: none changes the compiled
     * scalar graph, and hashing a growing point list would put O(n) work back into every frame. */
    private fun programFingerprint(stroke: InkStroke): Long {
        var result = -3750763034362895579L
        fun add(value: Any?) {
            result = (result xor (value?.hashCode()?.toLong() ?: 0L)) * 1099511628211L
        }
        add(stroke.sizePressureEnabled); add(stroke.sizePressureMinimum); add(stroke.sizePressureCurve); add(stroke.sizeSecondaryDynamics)
        add(stroke.opacityPressureEnabled); add(stroke.opacityPressureMinimum); add(stroke.opacityPressureCurve); add(stroke.opacitySecondaryDynamics)
        add(stroke.flowPressureEnabled); add(stroke.flowPressureMinimum); add(stroke.flowPressureCurve); add(stroke.flowSecondaryDynamics)
        add(stroke.spacingPressureEnabled); add(stroke.spacingPressureMinimum); add(stroke.spacingPressureCurve); add(stroke.spacingSecondaryDynamics)
        add(stroke.thicknessPressureEnabled); add(stroke.thicknessPressureMinimum); add(stroke.thicknessPressureCurve); add(stroke.thicknessSecondaryDynamics)
        add(stroke.blurPressureEnabled); add(stroke.blurPressureMinimum); add(stroke.blurPressureCurve); add(stroke.blurSecondaryDynamics)
        add(stroke.spacingMode); add(stroke.spacing); add(stroke.ribbon); add(stroke.spray); add(stroke.continuousPlot)
        add(stroke.dualSizeScale); add(stroke.dualSizeDynamics); add(stroke.dualFlowDynamics)
        add(stroke.dualThicknessDynamics); add(stroke.dualSpacingDynamics); add(stroke.dualSpacingMode)
        add(stroke.dualSpacing); add(stroke.dualRibbon); add(stroke.dualSpray)
        return result
    }

    private const val MAX_PROGRAMS = 512
}
