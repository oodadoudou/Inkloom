package app.inkloom.document

import kotlin.math.abs
import kotlin.math.sign

/**
 * Procreate's three-finger scrub: the fingers' centre moves side to side. It fires once after
 * [reversals] changes of direction, each stroke at least [minimumLeg] long, within [maximumDurationMs].
 */
class ScrubGesture(
    private val minimumLeg: Float,
    private val reversals: Int = 3,
    private val maximumDurationMs: Long = 1_500L,
) {
    private var startedAt = -1L
    private var extreme = 0f
    private var direction = 0
    private var count = 0

    fun start(timeMs: Long, x: Float) {
        startedAt = timeMs
        extreme = x
        direction = 0
        count = 0
    }

    /** True exactly once, on the move that completes the scrub. */
    fun move(timeMs: Long, x: Float): Boolean {
        if (startedAt < 0L) return false
        if (timeMs - startedAt > maximumDurationMs) { cancel(); return false }
        val delta = x - extreme
        when {
            direction == 0 -> if (abs(delta) >= minimumLeg) { direction = sign(delta).toInt(); extreme = x }
            delta * direction > 0f -> extreme = x
            abs(delta) >= minimumLeg -> { direction = -direction; extreme = x; count++ }
        }
        if (count < reversals) return false
        cancel()
        return true
    }

    fun cancel() {
        startedAt = -1L
        direction = 0
        count = 0
    }
}
