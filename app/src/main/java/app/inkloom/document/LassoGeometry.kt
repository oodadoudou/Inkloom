package app.inkloom.document

import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

data class LassoStrokePiece(val stroke: InkStroke, val selected: Boolean)

data class LassoStrokePartition(
    val pieces: List<LassoStrokePiece>,
    val changed: Boolean,
)

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

    fun selects(polygon: List<StrokePoint>, stroke: InkStroke): Boolean = partition(polygon, stroke) { "selection-piece" }.pieces.any(LassoStrokePiece::selected)

    fun partition(
        polygon: List<StrokePoint>,
        stroke: InkStroke,
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): LassoStrokePartition {
        if (polygon.size < 3 || stroke.points.isEmpty()) return LassoStrokePartition(listOf(LassoStrokePiece(stroke, false)), changed = false)
        if (stroke.points.size == 1) return LassoStrokePartition(listOf(LassoStrokePiece(stroke, contains(polygon, stroke.points.single()))), changed = false)

        val drafts = mutableListOf<Pair<Boolean, MutableList<StrokePoint>>>()
        stroke.points.zipWithNext().forEach { (start, end) ->
            val parameters = boundaryParameters(start, end, polygon)
            parameters.zipWithNext().forEach intervalLoop@{ (from, to) ->
                if (to - from <= PARAMETER_EPSILON) return@intervalLoop
                val selected = contains(polygon, interpolate(start, end, (from + to) / 2f))
                val intervalStart = interpolate(start, end, from)
                val intervalEnd = interpolate(start, end, to)
                val current = drafts.lastOrNull()
                if (current == null || current.first != selected) {
                    drafts += selected to mutableListOf(intervalStart, intervalEnd)
                } else {
                    appendDistinct(current.second, intervalStart)
                    appendDistinct(current.second, intervalEnd)
                }
            }
        }

        val usable = drafts.mapNotNull { (selected, points) -> if (points.isNotEmpty()) selected to points else null }
        if (usable.size <= 1) {
            val selected = usable.singleOrNull()?.first ?: contains(polygon, stroke.points.first())
            return LassoStrokePartition(listOf(LassoStrokePiece(stroke, selected)), changed = false)
        }
        return LassoStrokePartition(
            pieces = usable.map { (selected, points) -> LassoStrokePiece(stroke.copy(id = nextId(), points = points), selected) },
            changed = true,
        )
    }

    private fun boundaryParameters(start: StrokePoint, end: StrokePoint, polygon: List<StrokePoint>): List<Float> {
        val parameters = mutableListOf(0f, 1f)
        val segmentX = end.x - start.x
        val segmentY = end.y - start.y
        polygon.indices.forEach { index ->
            val edgeStart = polygon[index]
            val edgeEnd = polygon[(index + 1) % polygon.size]
            val edgeX = edgeEnd.x - edgeStart.x
            val edgeY = edgeEnd.y - edgeStart.y
            val denominator = cross(segmentX, segmentY, edgeX, edgeY)
            if (abs(denominator) <= GEOMETRY_EPSILON) return@forEach
            val offsetX = edgeStart.x - start.x
            val offsetY = edgeStart.y - start.y
            val segmentAmount = cross(offsetX, offsetY, edgeX, edgeY) / denominator
            val edgeAmount = cross(offsetX, offsetY, segmentX, segmentY) / denominator
            if (segmentAmount in -GEOMETRY_EPSILON..(1f + GEOMETRY_EPSILON) && edgeAmount in -GEOMETRY_EPSILON..(1f + GEOMETRY_EPSILON)) {
                parameters += segmentAmount.coerceIn(0f, 1f)
            }
        }
        return parameters.sorted().fold(mutableListOf()) { result, value ->
            if (result.isEmpty() || abs(result.last() - value) > PARAMETER_EPSILON) result += value
            result
        }
    }

    private fun interpolate(start: StrokePoint, end: StrokePoint, amount: Float): StrokePoint {
        val t = amount.coerceIn(0f, 1f)
        fun lerp(first: Float, second: Float) = first + (second - first) * t
        val orientationDelta = ((end.orientation - start.orientation + PI.toFloat()).mod((PI * 2).toFloat())) - PI.toFloat()
        return StrokePoint(
            x = lerp(start.x, end.x),
            y = lerp(start.y, end.y),
            pressure = lerp(start.pressure, end.pressure),
            timeMs = (start.timeMs + (end.timeMs - start.timeMs).toDouble() * t).roundToLong(),
            tilt = lerp(start.tilt, end.tilt),
            orientation = start.orientation + orientationDelta * t,
            velocity = lerp(start.velocity, end.velocity),
        )
    }

    private fun appendDistinct(points: MutableList<StrokePoint>, point: StrokePoint) {
        if (points.lastOrNull()?.let { abs(it.x - point.x) <= GEOMETRY_EPSILON && abs(it.y - point.y) <= GEOMETRY_EPSILON } != true) points += point
    }

    private fun cross(firstX: Float, firstY: Float, secondX: Float, secondY: Float) = firstX * secondY - firstY * secondX

    private const val GEOMETRY_EPSILON = 0.000001f
    private const val PARAMETER_EPSILON = 0.00001f
    private const val MINIMUM_RECTANGLE_EXTENT = 0.0005f
}
