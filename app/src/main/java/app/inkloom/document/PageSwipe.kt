package app.inkloom.document

import kotlin.math.abs

/**
 * Recognises a one-finger page-turn swipe, as in notebook apps that page horizontally. A quick,
 * mostly horizontal flick turns the page; short, slow, diagonal or long deliberate drags stay
 * canvas pans. Swiping left goes to the next page, right to the previous one.
 */
object PageSwipe {
    enum class Direction { NEXT, PREVIOUS }

    private const val MIN_DISTANCE_DP = 72f
    private const val FAR_DISTANCE_FRACTION = .22f
    private const val MIN_VELOCITY_DP_PER_MS = .55f
    private const val MAX_DURATION_MS = 800L
    private const val HORIZONTAL_DOMINANCE = 1.8f

    fun classify(dxPx: Float, dyPx: Float, durationMs: Long, viewWidthPx: Float, density: Float): Direction? {
        if (!dxPx.isFinite() || !dyPx.isFinite() || !viewWidthPx.isFinite() || !density.isFinite()) return null
        if (durationMs !in 1..MAX_DURATION_MS || viewWidthPx <= 0f || density <= 0f) return null
        val distance = abs(dxPx)
        if (distance < MIN_DISTANCE_DP * density || distance < abs(dyPx) * HORIZONTAL_DOMINANCE) return null
        val fast = distance / durationMs >= MIN_VELOCITY_DP_PER_MS * density
        val far = distance >= viewWidthPx * FAR_DISTANCE_FRACTION
        if (!fast && !far) return null
        return if (dxPx < 0f) Direction.NEXT else Direction.PREVIOUS
    }
}
