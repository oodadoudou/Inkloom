package app.inkloom.document

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

data class RulerGuideState(
    val centerX: Float = .5f,
    val centerY: Float = .5f,
    val angleDegrees: Float = 0f,
    val snapDegrees: Float = 15f,
) {
    fun normalized(): RulerGuideState = copy(
        centerX = centerX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
        centerY = centerY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
        angleDegrees = normalizeAngle(angleDegrees),
        snapDegrees = snapDegrees.takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 15f,
    )

    companion object {
        fun normalizeAngle(value: Float): Float {
            val finite = value.takeIf(Float::isFinite) ?: 0f
            return ((finite + 180f) % 360f + 360f) % 360f - 180f
        }
    }
}

data class RulerGuidePoint(val x: Float, val y: Float)
enum class RulerGestureMode { MOVE, ROTATE }

object RulerGuide {
    fun project(state: RulerGuideState, x: Float, y: Float, width: Float, height: Float): RulerGuidePoint {
        val normalized = state.normalized()
        val safeWidth = width.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeHeight = height.takeIf { it.isFinite() && it > 0f } ?: 1f
        val pointX = x.takeIf(Float::isFinite)?.coerceIn(0f, 1f)?.times(safeWidth) ?: normalized.centerX * safeWidth
        val pointY = y.takeIf(Float::isFinite)?.coerceIn(0f, 1f)?.times(safeHeight) ?: normalized.centerY * safeHeight
        val centerX = normalized.centerX * safeWidth
        val centerY = normalized.centerY * safeHeight
        val radians = Math.toRadians(normalized.angleDegrees.toDouble())
        val directionX = cos(radians).toFloat()
        val directionY = sin(radians).toFloat()
        val amount = (pointX - centerX) * directionX + (pointY - centerY) * directionY
        return RulerGuidePoint(
            ((centerX + amount * directionX) / safeWidth).coerceIn(0f, 1f),
            ((centerY + amount * directionY) / safeHeight).coerceIn(0f, 1f),
        )
    }

    fun distancePixels(state: RulerGuideState, xPx: Float, yPx: Float, width: Float, height: Float): Float {
        val normalized = state.normalized()
        val radians = Math.toRadians(normalized.angleDegrees.toDouble())
        val normalX = -sin(radians).toFloat()
        val normalY = cos(radians).toFloat()
        return kotlin.math.abs((xPx - normalized.centerX * width) * normalX + (yPx - normalized.centerY * height) * normalY)
    }

    fun rotationHandle(state: RulerGuideState, width: Float, height: Float): RulerGuidePoint {
        val normalized = state.normalized()
        val radians = Math.toRadians(normalized.angleDegrees.toDouble())
        val distance = minOf(width, height).coerceAtLeast(1f) * .28f
        return RulerGuidePoint(
            normalized.centerX * width + cos(radians).toFloat() * distance,
            normalized.centerY * height + sin(radians).toFloat() * distance,
        )
    }

    fun hitTest(state: RulerGuideState, xPx: Float, yPx: Float, width: Float, height: Float, radiusPx: Float): RulerGestureMode? {
        if (!xPx.isFinite() || !yPx.isFinite() || width <= 0f || height <= 0f) return null
        val handle = rotationHandle(state, width, height)
        if (hypot((xPx - handle.x).toDouble(), (yPx - handle.y).toDouble()) <= radiusPx) return RulerGestureMode.ROTATE
        return RulerGestureMode.MOVE.takeIf { distancePixels(state, xPx, yPx, width, height) <= radiusPx }
    }

    fun moveBy(state: RulerGuideState, dxPx: Float, dyPx: Float, width: Float, height: Float): RulerGuideState {
        val normalized = state.normalized()
        val safeWidth = width.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeHeight = height.takeIf { it.isFinite() && it > 0f } ?: 1f
        return normalized.copy(
            centerX = (normalized.centerX + dxPx.takeIf(Float::isFinite).orZero() / safeWidth).coerceIn(0f, 1f),
            centerY = (normalized.centerY + dyPx.takeIf(Float::isFinite).orZero() / safeHeight).coerceIn(0f, 1f),
        )
    }

    fun rotateToward(state: RulerGuideState, xPx: Float, yPx: Float, width: Float, height: Float): RulerGuideState {
        val normalized = state.normalized()
        val centerX = normalized.centerX * width
        val centerY = normalized.centerY * height
        val raw = Math.toDegrees(atan2((yPx - centerY).toDouble(), (xPx - centerX).toDouble())).toFloat()
        val snapped = round(raw / normalized.snapDegrees) * normalized.snapDegrees
        return normalized.copy(angleDegrees = RulerGuideState.normalizeAngle(snapped))
    }

    private fun Float?.orZero(): Float = this ?: 0f
}
