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
     * Procreate's low Movement drags/smears grain with the brush shape, while high Movement rolls
     * it like Texturized grain fixed behind the stroke. Values between those endpoints partially
     * roll the grain.
     * Four phase buckets per axis cap one preset at 16 decoded variants, avoiding decode/GC stalls
     * on MotionEvent while retaining a stable page-space texture under overlapping dabs.
     *
     * Zoom runs from Follow Size at zero to Cropped/fixed at one. This is intentionally independent
     * of authored Scale; multiplying the two at import time loses that behavior.
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
        val cropped = zoom.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val scale = scalePercent.takeIf(Float::isFinite)?.coerceIn(1f, 4000f) ?: 100f
        val effectiveScale = (scale * (REFERENCE_GRAIN_DIAMETER_PX / safeDiameter).pow(cropped))
            .coerceIn(1f, 4000f)
        val wavelength = (safeDiameter * effectiveScale / 100f).coerceAtLeast(1f)
        val stationaryAmount = movement.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return GrainSample(
            offset = GrainOffset(
                quantizePhase(baseOffset.x + centerX / wavelength * stationaryAmount),
                quantizePhase(baseOffset.y + centerY / wavelength * stationaryAmount),
            ),
            scalePercent = quantizeScale(effectiveScale),
        )
    }

    fun requiresPerDabSampling(movement: Float, zoom: Float): Boolean =
        movement > EPSILON || zoom > EPSILON

    fun requiresPerDabRotation(strokeRotation: Float): Boolean = kotlin.math.abs(strokeRotation) > EPSILON

    /** Procreate Moving Grain Rotation: inverse ← fixed → follow stroke. */
    fun rotationDegrees(baseDegrees: Float, strokeRotation: Float, strokeDirectionDegrees: Float): Float {
        val base = baseDegrees.takeIf(Float::isFinite)?.coerceIn(-360f, 360f) ?: 0f
        val follow = strokeRotation.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val direction = strokeDirectionDegrees.takeIf(Float::isFinite) ?: 0f
        return kotlin.math.round((base + direction * follow) / ROTATION_BUCKET_DEGREES) * ROTATION_BUCKET_DEGREES
    }

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
    private const val ROTATION_BUCKET_DEGREES = 15f
}
