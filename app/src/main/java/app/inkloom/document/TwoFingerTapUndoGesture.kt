package app.inkloom.document

import kotlin.math.hypot

class TwoFingerTapUndoGesture(
    private val movementSlop: Float,
    private val maximumTapDurationMs: Long = 220L,
    private val maximumSecondFingerDelayMs: Long = 120L,
) {
    private var startedAt = 0L
    private var firstX = 0f
    private var firstY = 0f
    private var secondX = 0f
    private var secondY = 0f
    private var eligible = false

    fun start(firstDownTimeMs: Long, secondDownTimeMs: Long,
        firstX: Float, firstY: Float, secondX: Float, secondY: Float) {
        startedAt = firstDownTimeMs
        this.firstX = firstX
        this.firstY = firstY
        this.secondX = secondX
        this.secondY = secondY
        eligible = secondDownTimeMs-firstDownTimeMs in 0..maximumSecondFingerDelayMs
    }

    fun move(firstX: Float, firstY: Float, secondX: Float, secondY: Float, transforming: Boolean) {
        val stationarySlop=movementSlop*.45f
        if (transforming || hypot((firstX-this.firstX).toDouble(),(firstY-this.firstY).toDouble()) > stationarySlop ||
            hypot((secondX-this.secondX).toDouble(),(secondY-this.secondY).toDouble()) > stationarySlop) eligible=false
    }

    fun finish(timeMs: Long): Boolean {
        val completed = eligible && startedAt > 0L && timeMs - startedAt <= maximumTapDurationMs
        eligible = false
        startedAt = 0L
        return completed
    }

    fun cancel() {
        eligible = false
        startedAt = 0L
    }
}
