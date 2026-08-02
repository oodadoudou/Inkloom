package app.inkloom.document

import kotlin.math.abs
import kotlin.math.atan2

class TwoPointerRotationGesture(
    private val meaningfulRotationDegrees: Float = 1f,
) {
    private var previousAngleDegrees: Float? = null
    private var accumulatedDegrees = 0f

    val isRotating: Boolean
        get() = abs(accumulatedDegrees) >= meaningfulRotationDegrees

    fun begin(firstX: Float, firstY: Float, secondX: Float, secondY: Float) {
        previousAngleDegrees = angleDegrees(firstX, firstY, secondX, secondY)
        accumulatedDegrees = 0f
    }

    fun update(firstX: Float, firstY: Float, secondX: Float, secondY: Float): Float? {
        val current = angleDegrees(firstX, firstY, secondX, secondY)
        val previous = previousAngleDegrees ?: run {
            previousAngleDegrees = current
            return null
        }
        val delta = normalizeDelta(current - previous)
        previousAngleDegrees = current
        accumulatedDegrees += delta
        return delta
    }

    fun reset() {
        previousAngleDegrees = null
        accumulatedDegrees = 0f
    }

    companion object {
        fun normalizeDelta(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

        private fun angleDegrees(firstX: Float, firstY: Float, secondX: Float, secondY: Float): Float =
            Math.toDegrees(atan2((secondY - firstY).toDouble(), (secondX - firstX).toDouble())).toFloat()
    }
}
