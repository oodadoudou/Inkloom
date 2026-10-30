package app.inkloom.document

import kotlin.math.hypot

/**
 * A short, stationary tap of several fingers: three for redo, four for full screen (Procreate).
 * The fingers' centre must stay within [movementSlop]; pinching or rotating cancels it.
 */
class MultiFingerTapGesture(
    private val movementSlop: Float,
    private val maximumTapDurationMs: Long = 240L,
) {
    private var startedAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var eligible = false

    /** Still a candidate: stationary so far. A hold that stays eligible repeats the action. */
    val isEligible: Boolean get() = eligible

    fun start(timeMs: Long, focusX: Float, focusY: Float) {
        startedAt = timeMs
        startX = focusX
        startY = focusY
        eligible = true
    }

    fun move(focusX: Float, focusY: Float, transforming: Boolean) {
        if (transforming || hypot((focusX - startX).toDouble(), (focusY - startY).toDouble()) > movementSlop) eligible = false
    }

    fun finish(timeMs: Long): Boolean {
        val completed = eligible && startedAt > 0L && timeMs - startedAt <= maximumTapDurationMs
        cancel()
        return completed
    }

    fun cancel() {
        startedAt = 0L
        eligible = false
    }
}
