package app.inkloom.document

import kotlin.math.floor
import kotlin.math.pow

data class GrainOffset(val x: Float, val y: Float)
data class GrainSample(val offset: GrainOffset, val scalePercent: Float)

object GrainBehavior {
    fun offset(renderSeed: Int, enabled: Boolean): GrainOffset {
        if (!enabled) return GrainOffset(0f, 0f)
        val random = java.util.Random(renderSeed.toLong() xor OFFSET_SEED)
        return GrainOffset(random.nextInt(PHASES) / PHASES.toFloat(), random.nextInt(PHASES) / PHASES.toFloat())
    }

    /**
     * Procreate's Moving grain travels with the brush shape, while Texturized grain remains fixed
     * behind the stroke. A Movement value between those endpoints partially rolls the grain.
     * Four phase buckets per axis cap one preset at 16 decoded variants, avoiding decode/GC stalls
     * on MotionEvent while retaining a stable page-space texture under overlapping dabs.
     *
     * Zoom interpolates between a fixed 100 px reference grain and grain that follows the current
     * dab diameter. This is intentionally independent of authored Scale; multiplying the two at
     * import time loses Procreate's Cropped/Follow Size behavior.
     */
    fun sample(
        baseOffset: GrainOffset,
        movement: Float,
        zoom: Float,
        scalePercent: Float,
        centerX: Float,
        centerY: Float,
        diameter: Float,
    ): GrainSample {
        val safeDiameter = diameter.takeIf(Float::isFinite)?.coerceAtLeast(.25f) ?: 1f
        val follow = zoom.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f
        val scale = scalePercent.takeIf(Float::isFinite)?.coerceIn(1f, 4000f) ?: 100f
        val effectiveScale = (scale * (REFERENCE_GRAIN_DIAMETER_PX / safeDiameter).pow(1f - follow))
            .coerceIn(1f, 4000f)
        val wavelength = (safeDiameter * effectiveScale / 100f).coerceAtLeast(1f)
        val stationaryAmount = 1f - (movement.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f)
        return GrainSample(
            offset = GrainOffset(
                quantizePhase(baseOffset.x + centerX / wavelength * stationaryAmount),
                quantizePhase(baseOffset.y + centerY / wavelength * stationaryAmount),
            ),
            scalePercent = quantizeScale(effectiveScale),
        )
    }

    fun requiresPerDabSampling(movement: Float, zoom: Float): Boolean =
        movement < 1f - EPSILON || zoom < 1f - EPSILON

    private fun quantizePhase(value: Float): Float {
        val wrapped = value - floor(value)
        return floor(wrapped * RENDER_PHASES) / RENDER_PHASES
    }

    private fun quantizeScale(value: Float): Float =
        (kotlin.math.round(value / SCALE_BUCKET_PERCENT) * SCALE_BUCKET_PERCENT).coerceIn(1f, 4000f)

    private const val OFFSET_SEED = 0x4A72C9E1L
    private const val PHASES = 64
    private const val RENDER_PHASES = 4f
    private const val SCALE_BUCKET_PERCENT = 5f
    private const val REFERENCE_GRAIN_DIAMETER_PX = 100f
    private const val EPSILON = .0001f
}
