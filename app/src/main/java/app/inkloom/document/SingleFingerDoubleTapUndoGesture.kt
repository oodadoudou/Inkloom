package app.inkloom.document

import kotlin.math.hypot

class SingleFingerDoubleTapUndoGesture(
    private val movementSlop: Float,
    private val minimumIntervalMs: Long = 40L,
    private val maximumIntervalMs: Long = 360L,
    private val maximumTapDurationMs: Long = 240L,
    private val maximumTapSeparation: Float = movementSlop * 2f,
) {
    private var startedAt: Long? = null
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    private var lastTapAt: Long? = null
    private var lastTapX = 0f
    private var lastTapY = 0f

    fun start(timeMs: Long, x: Float, y: Float) {
        startedAt = timeMs
        startX = x
        startY = y
        eligible = true
    }

    fun move(x: Float, y: Float, transforming: Boolean) {
        if (transforming || hypot((x - startX).toDouble(), (y - startY).toDouble()) > movementSlop) cancel()
    }

    fun finish(timeMs: Long): Boolean {
        val startTime = startedAt
        val completed = eligible && startTime != null && timeMs - startTime in 0L..maximumTapDurationMs
        eligible = false
        startedAt = null
        if (!completed) {
            clearCompletedTap()
            return false
        }

        val previousTime = lastTapAt
        if (previousTime == null) {
            rememberCompletedTap(timeMs)
            return false
        }
        val interval = timeMs - previousTime
        val closeEnough = hypot((startX - lastTapX).toDouble(), (startY - lastTapY).toDouble()) <= maximumTapSeparation
        return if (interval in minimumIntervalMs..maximumIntervalMs && closeEnough) {
            clearCompletedTap()
            true
        } else {
            rememberCompletedTap(timeMs)
            false
        }
    }

    fun cancel() {
        eligible = false
        startedAt = null
        clearCompletedTap()
    }

    private fun rememberCompletedTap(timeMs: Long) {
        lastTapAt = timeMs
        lastTapX = startX
        lastTapY = startY
    }

    private fun clearCompletedTap() {
        lastTapAt = null
    }
}
