package app.inkloom.document

import kotlin.math.exp
import kotlin.math.hypot

/** Procreate Stroke Path Fall Off: stable distance-based opacity decay from the first stamp. */
object StrokeFalloff {
    fun factors(
        points: List<StrokePoint>,
        pageWidth: Float,
        pageHeight: Float,
        brushDiameter: Float,
        amount: Float,
    ): FloatArray? {
        val safeAmount = amount.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        if (safeAmount <= EPSILON || points.isEmpty()) return null
        val safeDiameter = brushDiameter.takeIf(Float::isFinite)?.coerceAtLeast(.25f) ?: 1f
        // Low values sustain long pattern strokes; at maximum, the deposit reaches ~37% after
        // half a brush diameter and then rapidly approaches invisibility.
        val decayLength = safeDiameter * (.5f + 24f * (1f - safeAmount) * (1f - safeAmount))
        val result = FloatArray(points.size)
        result[0] = 1f
        var distance = 0f
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val point = points[index]
            distance += hypot(
                ((point.x - previous.x) * pageWidth).toDouble(),
                ((point.y - previous.y) * pageHeight).toDouble(),
            ).toFloat()
            result[index] = exp((-distance / decayLength).toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return result
    }

    private const val EPSILON = .0001f
}
