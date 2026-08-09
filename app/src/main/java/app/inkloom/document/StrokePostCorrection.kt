package app.inkloom.document

import kotlin.math.sqrt

/**
 * Applies CSP-style correction after pen-up. Live input stabilization stays independent so a
 * non-zero value never adds latency while the stylus is moving.
 */
object StrokePostCorrection {
    fun apply(
        stroke: InkStroke,
        amount: Float,
        adjustBySpeed: Boolean,
        adjustByScale: Boolean,
        viewportScale: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): InkStroke {
        if (stroke.points.size < 3 || amount <= 0f) return stroke
        var effective = amount.coerceIn(0f, 1f)
        if (adjustBySpeed) {
            val averageVelocity = stroke.points.asSequence().map(StrokePoint::velocity).filter(Float::isFinite).average().takeIf(Double::isFinite)?.toFloat() ?: 0f
            // Preserve fast, expressive gestures while applying the requested cleanup to slow ink.
            effective *= (1f / (1f + averageVelocity.coerceAtLeast(0f) * .35f)).coerceIn(.25f, 1f)
        }
        if (adjustByScale) effective /= sqrt(viewportScale.coerceAtLeast(1f))
        if (effective < .01f) return stroke

        val smoothed = StrokeCorrection.apply(stroke, CorrectLineMode.SMOOTH, effective, pageWidth, pageHeight)
        return if (effective < .55f || smoothed.points.size < 4) smoothed else {
            StrokeCorrection.apply(smoothed, CorrectLineMode.SIMPLIFY, (effective - .55f) / .45f * .35f, pageWidth, pageHeight)
        }
    }
}
