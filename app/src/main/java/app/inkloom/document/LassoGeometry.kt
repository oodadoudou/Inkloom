package app.inkloom.document

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object LassoGeometry {
    fun rectangle(start: StrokePoint, end: StrokePoint, minimumExtent: Float = MINIMUM_RECTANGLE_EXTENT): List<StrokePoint> {
        val left = minOf(start.x, end.x).coerceIn(0f, 1f)
        val top = minOf(start.y, end.y).coerceIn(0f, 1f)
        val right = maxOf(start.x, end.x).coerceIn(0f, 1f)
        val bottom = maxOf(start.y, end.y).coerceIn(0f, 1f)
        if (right - left < minimumExtent || bottom - top < minimumExtent) return emptyList()
        return listOf(
            StrokePoint(left, top),
            StrokePoint(right, top),
            StrokePoint(right, bottom),
            StrokePoint(left, bottom),
        )
    }

    fun ellipse(start: StrokePoint, end: StrokePoint, segments: Int = 48): List<StrokePoint> {
        val left = minOf(start.x, end.x).coerceIn(0f, 1f)
        val top = minOf(start.y, end.y).coerceIn(0f, 1f)
        val right = maxOf(start.x, end.x).coerceIn(0f, 1f)
        val bottom = maxOf(start.y, end.y).coerceIn(0f, 1f)
        if (right - left < MINIMUM_RECTANGLE_EXTENT || bottom - top < MINIMUM_RECTANGLE_EXTENT) return emptyList()
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val radiusX = (right - left) / 2f
        val radiusY = (bottom - top) / 2f
        return List(segments.coerceIn(16, 128)) { index ->
            val angle = PI * 2.0 * index / segments.coerceIn(16, 128)
            StrokePoint((centerX + cos(angle).toFloat() * radiusX).coerceIn(0f, 1f), (centerY + sin(angle).toFloat() * radiusY).coerceIn(0f, 1f))
        }
    }

    fun brushCorridor(path: List<StrokePoint>, diameter: Float, modelWidth: Float, modelHeight: Float): List<StrokePoint> {
        if (path.isEmpty() || modelWidth <= 0f || modelHeight <= 0f) return emptyList()
        val radius = diameter.coerceAtLeast(1f) / 2f
        if (path.size == 1) {
            val center = path.single()
            return List(32) { index ->
                val angle = PI * 2.0 * index / 32.0
                StrokePoint(
                    (center.x + cos(angle).toFloat() * radius / modelWidth).coerceIn(0f, 1f),
                    (center.y + sin(angle).toFloat() * radius / modelHeight).coerceIn(0f, 1f),
                )
            }
        }
        val positive = ArrayList<StrokePoint>(path.size)
        val negative = ArrayList<StrokePoint>(path.size)
        path.forEachIndexed { index, point ->
            val before = path[(index - 1).coerceAtLeast(0)]
            val after = path[(index + 1).coerceAtMost(path.lastIndex)]
            val tangentX = (after.x - before.x) * modelWidth
            val tangentY = (after.y - before.y) * modelHeight
            val length = hypot(tangentX.toDouble(), tangentY.toDouble()).toFloat().coerceAtLeast(.0001f)
            val offsetX = -tangentY / length * radius / modelWidth
            val offsetY = tangentX / length * radius / modelHeight
            positive += StrokePoint((point.x + offsetX).coerceIn(0f, 1f), (point.y + offsetY).coerceIn(0f, 1f))
            negative += StrokePoint((point.x - offsetX).coerceIn(0f, 1f), (point.y - offsetY).coerceIn(0f, 1f))
        }
        return positive + negative.asReversed()
    }

    fun contains(polygon: List<StrokePoint>, point: StrokePoint): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val crossingX = (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x
                if (point.x < crossingX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private const val MINIMUM_RECTANGLE_EXTENT = 0.0005f
}
