package app.inkloom.engine.v5.tools

import kotlin.math.*

enum class SelectionTransformMode { FREEFORM, UNIFORM }

enum class SelectionTransformNode(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f), TOP(.5f, 0f), TOP_RIGHT(1f, 0f), RIGHT(1f, .5f),
    BOTTOM_RIGHT(1f, 1f), BOTTOM(.5f, 1f), BOTTOM_LEFT(0f, 1f), LEFT(0f, .5f),
    ROTATE(.5f, 0f), MOVE(.5f, .5f),
}

/** Oriented bounds in native raster pixels, retained across every gesture in one transform. */
data class SelectionTransformPose(
    val centerX: Float, val centerY: Float, val width: Float, val height: Float,
    val angle: Float = 0f, val flippedX: Boolean = false, val flippedY: Boolean = false,
) {
    init {
        require(listOf(centerX, centerY, width, height, angle).all(Float::isFinite))
        require(width > 0 && height > 0)
    }

    fun point(x: Float, y: Float): Pair<Float, Float> {
        val delta = rotate((x - .5f) * width, (y - .5f) * height, angle)
        return centerX + delta.first to centerY + delta.second
    }

    fun affineFrom(original: SelectionTransformPose) = SelectionAffineTransform(
        original.centerX, original.centerY,
        width / original.width * if (flippedX) -1f else 1f,
        height / original.height * if (flippedY) -1f else 1f,
        angle, centerX - original.centerX, centerY - original.centerY,
    )

    fun drag(node: SelectionTransformNode, startX: Float, startY: Float, x: Float, y: Float,
             mode: SelectionTransformMode): SelectionTransformPose {
        if (node == SelectionTransformNode.MOVE) return copy(centerX = centerX + x - startX, centerY = centerY + y - startY)
        if (node == SelectionTransformNode.ROTATE) {
            val delta = atan2(y - centerY, x - centerX) - atan2(startY - centerY, startX - centerX)
            return copy(angle = (angle + Math.toDegrees(delta.toDouble()).toFloat()).mod(360f))
        }
        val delta = rotate(x - startX, y - startY, -angle)
        val sx = (node.x - .5f) * 2f
        val sy = (node.y - .5f) * 2f
        var nextWidth = if (sx == 0f) width else (width + delta.first * sx).coerceAtLeast(1f)
        var nextHeight = if (sy == 0f) height else (height + delta.second * sy).coerceAtLeast(1f)
        if (mode == SelectionTransformMode.UNIFORM) {
            val scale = when {
                sx == 0f -> nextHeight / height
                sy == 0f -> nextWidth / width
                else -> 1f + (delta.first * sx * width + delta.second * sy * height) / (width * width + height * height)
            }.coerceAtLeast(max(1f / width, 1f / height))
            nextWidth = width * scale; nextHeight = height * scale
        }
        val anchor = point(1f - node.x, 1f - node.y)
        val offset = rotate((.5f - node.x) * nextWidth, (.5f - node.y) * nextHeight, angle)
        return copy(centerX = anchor.first - offset.first, centerY = anchor.second - offset.second,
            width = nextWidth, height = nextHeight)
    }

    companion object {
        fun rotate(x: Float, y: Float, angle: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angle.toDouble())
            return (x * cos(rad) - y * sin(rad)).toFloat() to (x * sin(rad) + y * cos(rad)).toFloat()
        }
    }
}
