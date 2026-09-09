package app.inkloom.engine.v5.tools

import kotlin.math.*

enum class SelectionTransformMode { FREEFORM, UNIFORM, DISTORT }

enum class SelectionTransformNode(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f), TOP(.5f, 0f), TOP_RIGHT(1f, 0f), RIGHT(1f, .5f),
    BOTTOM_RIGHT(1f, 1f), BOTTOM(.5f, 1f), BOTTOM_LEFT(0f, 1f), LEFT(0f, .5f),
    ROTATE(.5f, 0f), MOVE(.5f, .5f),
}

/** Oriented bounds in native raster pixels, retained across every gesture in one transform. */
data class SelectionTransformPose(
    val centerX: Float, val centerY: Float, val width: Float, val height: Float,
    val angle: Float = 0f, val flippedX: Boolean = false, val flippedY: Boolean = false,
    val corners: List<Pair<Float, Float>>? = null,
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

    fun sourceCorners(): List<Pair<Float, Float>> = corners ?: listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).map { (x,y) ->
        point(if (flippedX) 1f-x else x, if (flippedY) 1f-y else y)
    }

    fun rasterMappingFrom(original: SelectionTransformPose): SelectionRasterTransform =
        corners?.let { SelectionProjectiveMapping(original.centerX-original.width/2, original.centerY-original.height/2,
            original.width, original.height, it) } ?: affineFrom(original)

    /** Affine controls after Distort operate on the retained original quad, never its raster preview. */
    fun withPose(next: SelectionTransformPose): SelectionTransformPose {
        val quad = corners ?: return next
        return next.copy(corners = quad.map { (x,y) ->
            val local = rotate(x-centerX, y-centerY, -angle)
            val mapped = rotate(local.first*next.width/width * if (next.flippedX != flippedX) -1f else 1f,
                local.second*next.height/height * if (next.flippedY != flippedY) -1f else 1f, next.angle)
            next.centerX+mapped.first to next.centerY+mapped.second
        })
    }

    fun distort(node: SelectionTransformNode, dx: Float, dy: Float): SelectionTransformPose? {
        if (dx == 0f && dy == 0f) return this
        val indices = when (node) {
            SelectionTransformNode.TOP_LEFT -> setOf(0); SelectionTransformNode.TOP -> setOf(0,1)
            SelectionTransformNode.TOP_RIGHT -> setOf(1); SelectionTransformNode.RIGHT -> setOf(1,2)
            SelectionTransformNode.BOTTOM_RIGHT -> setOf(2); SelectionTransformNode.BOTTOM -> setOf(2,3)
            SelectionTransformNode.BOTTOM_LEFT -> setOf(3); SelectionTransformNode.LEFT -> setOf(3,0)
            else -> setOf(0,1,2,3)
        }
        val next = sourceCorners().mapIndexed { i,p -> if (i in indices) p.first+dx to p.second+dy else p }
        if (!SelectionProjectiveMapping.validCorners(next)) return null
        val left=next.minOf { it.first }; val right=next.maxOf { it.first }
        val top=next.minOf { it.second }; val bottom=next.maxOf { it.second }
        return SelectionTransformPose((left+right)/2, (top+bottom)/2, right-left, bottom-top, corners=next)
    }

    fun drag(node: SelectionTransformNode, startX: Float, startY: Float, x: Float, y: Float,
             mode: SelectionTransformMode): SelectionTransformPose {
        if (mode == SelectionTransformMode.DISTORT) return distort(node, x-startX, y-startY) ?: this
        if (node == SelectionTransformNode.MOVE) return withPose(copy(centerX = centerX + x - startX, centerY = centerY + y - startY))
        if (node == SelectionTransformNode.ROTATE) {
            val delta = atan2(y - centerY, x - centerX) - atan2(startY - centerY, startX - centerX)
            return withPose(copy(angle = (angle + Math.toDegrees(delta.toDouble()).toFloat()).mod(360f)))
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
        return withPose(copy(centerX = anchor.first - offset.first, centerY = anchor.second - offset.second,
            width = nextWidth, height = nextHeight))
    }

    companion object {
        fun rotate(x: Float, y: Float, angle: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angle.toDouble())
            return (x * cos(rad) - y * sin(rad)).toFloat() to (x * sin(rad) + y * cos(rad)).toFloat()
        }
    }
}
