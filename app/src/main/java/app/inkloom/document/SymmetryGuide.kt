package app.inkloom.document

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class SymmetryGuideState(
    val centerX: Float = .5f,
    val centerY: Float = .5f,
    /** Zero degrees is a horizontal axis; vertical symmetry uses its perpendicular axis. */
    val angleDegrees: Float = 0f,
) {
    fun normalized() = copy(
        centerX = centerX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
        centerY = centerY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
        angleDegrees = normalizeAngle(angleDegrees),
    )

    companion object {
        fun normalizeAngle(value: Float): Float = if (!value.isFinite()) 0f else ((value + 180f) % 360f + 360f) % 360f - 180f
    }
}

enum class SymmetryGuideGesture { MOVE, ROTATE }

object SymmetryGuide {
    fun rotationHandle(state: SymmetryGuideState, width: Float, height: Float, distance: Float): ShapePoint {
        val normalized = state.normalized()
        val radians = Math.toRadians(normalized.angleDegrees.toDouble())
        return ShapePoint(
            normalized.centerX * width + cos(radians).toFloat() * distance,
            normalized.centerY * height + sin(radians).toFloat() * distance,
        )
    }

    fun hitTest(state: SymmetryGuideState, x: Float, y: Float, width: Float, height: Float, radius: Float, handleDistance: Float): SymmetryGuideGesture? {
        val handle = rotationHandle(state, width, height, handleDistance)
        if (hypot((x - handle.x).toDouble(), (y - handle.y).toDouble()) <= radius) return SymmetryGuideGesture.ROTATE
        val normalized = state.normalized()
        val centerX = normalized.centerX * width
        val centerY = normalized.centerY * height
        if (hypot((x - centerX).toDouble(), (y - centerY).toDouble()) <= radius) return SymmetryGuideGesture.MOVE
        return null
    }

    fun moveTo(state: SymmetryGuideState, x: Float, y: Float, width: Float, height: Float) = state.copy(
        centerX = (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f),
        centerY = (y / height.coerceAtLeast(1f)).coerceIn(0f, 1f),
    ).normalized()

    fun rotateToward(state: SymmetryGuideState, x: Float, y: Float, width: Float, height: Float): SymmetryGuideState {
        val normalized = state.normalized()
        val angle = Math.toDegrees(atan2((y - normalized.centerY * height).toDouble(), (x - normalized.centerX * width).toDouble())).toFloat()
        return normalized.copy(angleDegrees = SymmetryGuideState.normalizeAngle(angle))
    }
}
