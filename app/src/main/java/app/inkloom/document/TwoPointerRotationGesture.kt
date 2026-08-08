package app.inkloom.document

import kotlin.math.atan2

class TwoPointerRotationGesture {
    private var previousAngleDegrees: Float? = null

    fun begin(firstX: Float, firstY: Float, secondX: Float, secondY: Float) {
        previousAngleDegrees = angleDegrees(firstX, firstY, secondX, secondY)
    }

    fun update(firstX: Float, firstY: Float, secondX: Float, secondY: Float): Float? {
        val current = angleDegrees(firstX, firstY, secondX, secondY)
        val previous = previousAngleDegrees ?: run {
            previousAngleDegrees = current
            return null
        }
        val delta = normalizeDelta(current - previous)
        previousAngleDegrees = current
        return delta
    }

    fun reset() {
        previousAngleDegrees = null
    }

    companion object {
        fun normalizeDelta(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

        private fun angleDegrees(firstX: Float, firstY: Float, secondX: Float, secondY: Float): Float =
            Math.toDegrees(atan2((secondY - firstY).toDouble(), (secondX - firstX).toDouble())).toFloat()
    }
}
