package app.inkloom.document

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class SpecialRulerMode {
    NONE,
    PARALLEL,
    RADIAL,
    CONCENTRIC,
    ISOMETRIC,
}

enum class SpecialRulerHandle { CENTER, ROTATION }

data class SpecialRulerPoint(val x: Float, val y: Float)

data class SpecialRulerState(
    val mode: SpecialRulerMode = SpecialRulerMode.NONE,
    val assisted: Boolean = true,
    val editing: Boolean = false,
    val centerX: Float = .5f,
    val centerY: Float = .5f,
    val angleDegrees: Float = 0f,
    val spacing: Float = 64f,
) {
    fun normalized() = copy(
        centerX = centerX.takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f,
        centerY = centerY.takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f,
        angleDegrees = RulerGuideState.normalizeAngle(angleDegrees),
        spacing = spacing.takeIf(Float::isFinite)?.coerceIn(16f, 400f) ?: 64f,
    )

    companion object {
        fun defaultFor(mode: SpecialRulerMode) = SpecialRulerState(mode = mode, editing = mode != SpecialRulerMode.NONE)
    }
}

sealed interface SpecialRulerConstraint {
    fun apply(x: Float, y: Float): RulerGuidePoint
}

private class DirectionConstraint(
    private val startX: Float,
    private val startY: Float,
    private val directionX: Float,
    private val directionY: Float,
    private val pageWidth: Float,
    private val pageHeight: Float,
) : SpecialRulerConstraint {
    override fun apply(x: Float, y: Float): RulerGuidePoint {
        val px = (x - startX) * pageWidth
        val py = (y - startY) * pageHeight
        val denominator = directionX * directionX + directionY * directionY
        if (denominator <= .000001f) return RulerGuidePoint(startX, startY)
        val amount = (px * directionX + py * directionY) / denominator
        return RulerGuidePoint(
            (startX + directionX * amount / pageWidth).coerceIn(0f, 1f),
            (startY + directionY * amount / pageHeight).coerceIn(0f, 1f),
        )
    }
}

private class ConcentricConstraint(
    startX: Float,
    startY: Float,
    private val centerX: Float,
    private val centerY: Float,
    private val pageWidth: Float,
    private val pageHeight: Float,
) : SpecialRulerConstraint {
    private val radius = hypot(((startX - centerX) * pageWidth).toDouble(), ((startY - centerY) * pageHeight).toDouble()).toFloat()

    override fun apply(x: Float, y: Float): RulerGuidePoint {
        val dx = (x - centerX) * pageWidth
        val dy = (y - centerY) * pageHeight
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance <= .000001f || radius <= .000001f) return RulerGuidePoint(centerX, centerY)
        val scale = radius / distance
        return RulerGuidePoint(
            (centerX + dx * scale / pageWidth).coerceIn(0f, 1f),
            (centerY + dy * scale / pageHeight).coerceIn(0f, 1f),
        )
    }
}

object SpecialRulers {
    fun constraintAt(
        state: SpecialRulerState,
        startX: Float,
        startY: Float,
        movementX: Float,
        movementY: Float,
        pageWidth: Float,
        pageHeight: Float,
        minimumMovementPixels: Float = 3f,
    ): SpecialRulerConstraint? {
        val normalized = state.normalized()
        if (normalized.mode == SpecialRulerMode.NONE || !normalized.assisted || pageWidth <= 0f || pageHeight <= 0f) return null
        val moveX = (movementX - startX) * pageWidth
        val moveY = (movementY - startY) * pageHeight
        val movementLength = hypot(moveX.toDouble(), moveY.toDouble()).toFloat()
        if (!movementLength.isFinite() || movementLength < minimumMovementPixels.coerceAtLeast(0f)) return null
        return when (normalized.mode) {
            SpecialRulerMode.NONE -> null
            SpecialRulerMode.PARALLEL -> directionConstraint(normalized.angleDegrees, startX, startY, pageWidth, pageHeight)
            SpecialRulerMode.RADIAL -> DirectionConstraint(
                startX,
                startY,
                (normalized.centerX - startX) * pageWidth,
                (normalized.centerY - startY) * pageHeight,
                pageWidth,
                pageHeight,
            )
            SpecialRulerMode.CONCENTRIC -> ConcentricConstraint(startX, startY, normalized.centerX, normalized.centerY, pageWidth, pageHeight)
            SpecialRulerMode.ISOMETRIC -> {
                val angle = listOf(normalized.angleDegrees + 30f, normalized.angleDegrees + 90f, normalized.angleDegrees + 150f).maxBy { candidate ->
                    val radians = Math.toRadians(candidate.toDouble())
                    abs(moveX * cos(radians).toFloat() + moveY * sin(radians).toFloat()) / movementLength
                }
                directionConstraint(angle, startX, startY, pageWidth, pageHeight)
            }
        }
    }

    fun rotationHandle(state: SpecialRulerState, width: Float, height: Float, distancePx: Float): SpecialRulerPoint {
        val angle = when (state.mode) {
            SpecialRulerMode.ISOMETRIC -> state.angleDegrees + 30f
            else -> state.angleDegrees
        }
        val radians = Math.toRadians(angle.toDouble())
        return SpecialRulerPoint(
            state.centerX * width + cos(radians).toFloat() * distancePx,
            state.centerY * height + sin(radians).toFloat() * distancePx,
        )
    }

    fun hitHandle(state: SpecialRulerState, x: Float, y: Float, width: Float, height: Float, radiusPx: Float, rotationDistancePx: Float): SpecialRulerHandle? {
        if (!state.editing || state.mode == SpecialRulerMode.NONE || width <= 0f || height <= 0f) return null
        val centerX = state.centerX * width
        val centerY = state.centerY * height
        if (hypot((x - centerX).toDouble(), (y - centerY).toDouble()) <= radiusPx) return SpecialRulerHandle.CENTER
        if (state.mode == SpecialRulerMode.RADIAL || state.mode == SpecialRulerMode.CONCENTRIC) return null
        val rotation = rotationHandle(state, width, height, rotationDistancePx)
        return SpecialRulerHandle.ROTATION.takeIf { hypot((x - rotation.x).toDouble(), (y - rotation.y).toDouble()) <= radiusPx }
    }

    fun moveHandle(state: SpecialRulerState, handle: SpecialRulerHandle, x: Float, y: Float, width: Float, height: Float): SpecialRulerState {
        if (width <= 0f || height <= 0f) return state.normalized()
        return when (handle) {
            SpecialRulerHandle.CENTER -> state.copy(centerX = x / width, centerY = y / height).normalized()
            SpecialRulerHandle.ROTATION -> {
                val raw = Math.toDegrees(atan2((y - state.centerY * height).toDouble(), (x - state.centerX * width).toDouble())).toFloat()
                state.copy(angleDegrees = if (state.mode == SpecialRulerMode.ISOMETRIC) raw - 30f else raw).normalized()
            }
        }
    }

    private fun directionConstraint(angleDegrees: Float, startX: Float, startY: Float, pageWidth: Float, pageHeight: Float): SpecialRulerConstraint {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return DirectionConstraint(startX, startY, cos(radians).toFloat(), sin(radians).toFloat(), pageWidth, pageHeight)
    }
}
