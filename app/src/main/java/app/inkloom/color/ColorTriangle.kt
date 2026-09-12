package app.inkloom.color

import kotlin.math.hypot

/** White (top left), black (bottom left), pure hue (right). Coordinates are in field pixels. */
internal class ColorTriangle(val width: Float, val height: Float) {
    init { require(width > 0f && height > 0f) }
    data class Point(val x: Float, val y: Float)
    data class Weights(val hue: Float, val white: Float, val black: Float) {
        val value get() = hue + white
        val saturation get() = if (value > 0f) hue / value else 0f
    }
    fun position(hue: Float, white: Float, black: Float): Point {
        val total = hue + white + black
        return Point(width * hue / total, height * (black + hue * .5f) / total)
    }
    fun position(saturation: Float, value: Float): Point =
        position(saturation * value, (1f - saturation) * value, 1f - value)

    fun weights(x: Float, y: Float): Weights {
        val p = closestPoint(x, y)
        val h = (p.x / width).coerceIn(0f, 1f)
        val w = (1f - p.y / height - h * .5f).coerceIn(0f, 1f)
        val b = (p.y / height - h * .5f).coerceIn(0f, 1f)
        val total = h + w + b
        return Weights(h / total, w / total, b / total)
    }
    fun distanceTo(x: Float, y: Float): Float = closestPoint(x, y).let { hypot(x - it.x, y - it.y) }
    private fun closestPoint(x: Float, y: Float): Point {
        val nx = x / width; val ny = y / height
        if (nx >= 0f && ny >= nx * .5f && ny <= 1f - nx * .5f) return Point(x, y)
        fun edge(ax: Float, ay: Float, bx: Float, by: Float): Point {
            val dx = bx - ax; val dy = by - ay
            val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
            return Point(ax + t * dx, ay + t * dy)
        }
        return listOf(edge(0f, 0f, 0f, height), edge(0f, 0f, width, height * .5f), edge(0f, height, width, height * .5f))
            .minBy { (x - it.x) * (x - it.x) + (y - it.y) * (y - it.y) }
    }
}
