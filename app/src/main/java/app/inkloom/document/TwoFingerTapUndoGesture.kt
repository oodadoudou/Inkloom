package app.inkloom.document

import kotlin.math.hypot

class TwoFingerTapUndoGesture(
    private val movementSlop: Float,
    private val minimumIntervalMs: Long = 40L,
    private val maximumIntervalMs: Long = 360L,
    private val maximumTapDurationMs: Long = 240L,
) {
    private var startedAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    private var lastCompletedTapAt = 0L

    fun start(timeMs: Long, focusX: Float, focusY: Float) {
        startedAt = timeMs
        startX = focusX
        startY = focusY
        eligible = true
    }

    fun move(focusX: Float, focusY: Float, scaling: Boolean) {
        if (scaling || hypot((focusX - startX).toDouble(), (focusY - startY).toDouble()) > movementSlop) eligible = false
    }

    fun finish(timeMs: Long): Boolean {
        val completed = eligible && startedAt > 0L && timeMs - startedAt <= maximumTapDurationMs
        eligible = false
        startedAt = 0L
        if (!completed) return false
        val interval = timeMs - lastCompletedTapAt
        return if (lastCompletedTapAt > 0L && interval in minimumIntervalMs..maximumIntervalMs) {
            lastCompletedTapAt = 0L
            true
        } else {
            lastCompletedTapAt = timeMs
            false
        }
    }

    fun cancel() {
        eligible = false
        startedAt = 0L
    }
}
