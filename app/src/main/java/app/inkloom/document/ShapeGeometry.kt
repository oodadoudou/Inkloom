package app.inkloom.document

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

data class ShapePoint(val x: Float, val y: Float)

object ShapeGeometry {
    fun snapEnd(start: ShapePoint, end: ShapePoint, pageWidth: Float, pageHeight: Float, toleranceDegrees: Float = 8f): ShapePoint {
        val dx = (end.x - start.x) * pageWidth.coerceAtLeast(1f)
        val dy = (end.y - start.y) * pageHeight.coerceAtLeast(1f)
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length <= .001f) return end
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val snappedAngle = round(angle / 45f) * 45f
        if (abs(angleDelta(angle, snappedAngle)) > toleranceDegrees) return end
        val radians = Math.toRadians(snappedAngle.toDouble())
        return ShapePoint(
            start.x + cos(radians).toFloat() * length / pageWidth.coerceAtLeast(1f),
            start.y + sin(radians).toFloat() * length / pageHeight.coerceAtLeast(1f),
        )
    }

    fun create(kind: ShapeKind, start: ShapePoint, rawEnd: ShapePoint, pageWidth: Float, pageHeight: Float, colorArgb: Long, strokeWidth: Float): PageElement.Shape? {
        if (kind == ShapeKind.FREEFORM) return null
        val end = if (kind in setOf(ShapeKind.LINE, ShapeKind.ARROW)) snapEnd(start, rawEnd, pageWidth, pageHeight) else rawEnd
        val dx = (end.x - start.x) * pageWidth.coerceAtLeast(1f)
        val dy = (end.y - start.y) * pageHeight.coerceAtLeast(1f)
        if (hypot(dx.toDouble(), dy.toDouble()) < 8f) return null
        return if (kind in setOf(ShapeKind.LINE, ShapeKind.ARROW)) {
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val lineHeight = (strokeWidth * 4f).coerceAtLeast(24f)
            val centerX = (start.x + end.x) / 2f
            val centerY = (start.y + end.y) / 2f
            PageElement.Shape(
                x = centerX - length / pageWidth.coerceAtLeast(1f) / 2f,
                y = centerY - lineHeight / pageHeight.coerceAtLeast(1f) / 2f,
                width = length,
                height = lineHeight,
                rotationDegrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat(),
                kind = kind,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
            )
        } else {
            PageElement.Shape(
                x = minOf(start.x, end.x),
                y = minOf(start.y, end.y),
                width = abs(dx).coerceAtLeast(20f),
                height = abs(dy).coerceAtLeast(20f),
                kind = kind,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
            )
        }
    }

    fun createFreeform(rawPoints: List<ShapePoint>, pageWidth: Float, pageHeight: Float, colorArgb: Long, strokeWidth: Float): PageElement.Shape? {
        val safeWidth = pageWidth.coerceAtLeast(1f)
        val safeHeight = pageHeight.coerceAtLeast(1f)
        val points = simplify(
            rawPoints.map { ShapePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) },
            safeWidth,
            safeHeight,
            SIMPLIFY_TOLERANCE_PX,
        )
        if (points.size < 3) return null
        val minX = points.minOf(ShapePoint::x)
        val maxX = points.maxOf(ShapePoint::x)
        val minY = points.minOf(ShapePoint::y)
        val maxY = points.maxOf(ShapePoint::y)
        val width = (maxX - minX) * safeWidth
        val height = (maxY - minY) * safeHeight
        if (width < MINIMUM_EXTENT_PX || height < MINIMUM_EXTENT_PX || polygonArea(points, safeWidth, safeHeight) < MINIMUM_AREA_PX) return null
        val spanX = (maxX - minX).coerceAtLeast(1f / safeWidth)
        val spanY = (maxY - minY).coerceAtLeast(1f / safeHeight)
        return PageElement.Shape(
            x = minX,
            y = minY,
            width = width,
            height = height,
            kind = ShapeKind.FREEFORM,
            colorArgb = colorArgb,
            strokeWidth = strokeWidth,
            path = points.map { ShapeVertex((it.x - minX) / spanX, (it.y - minY) / spanY) },
        )
    }

    private fun simplify(points: List<ShapePoint>, pageWidth: Float, pageHeight: Float, tolerancePx: Float): List<ShapePoint> {
        val distinct = points.distinctConsecutive(pageWidth, pageHeight)
        if (distinct.size <= 2) return distinct
        val keep = BooleanArray(distinct.size)
        keep[0] = true
        keep[distinct.lastIndex] = true
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending += 0 to distinct.lastIndex
        while (pending.isNotEmpty()) {
            val (startIndex, endIndex) = pending.removeLast()
            var furthestIndex = -1
            var furthestDistance = 0f
            for (index in startIndex + 1 until endIndex) {
                val distance = segmentDistancePx(distinct[index], distinct[startIndex], distinct[endIndex], pageWidth, pageHeight)
                if (distance > furthestDistance) { furthestDistance = distance; furthestIndex = index }
            }
            if (furthestIndex >= 0 && furthestDistance > tolerancePx) {
                keep[furthestIndex] = true
                pending += startIndex to furthestIndex
                pending += furthestIndex to endIndex
            }
        }
        return distinct.filterIndexed { index, _ -> keep[index] }
    }

    private fun List<ShapePoint>.distinctConsecutive(pageWidth: Float, pageHeight: Float): List<ShapePoint> =
        fold(mutableListOf()) { result, point ->
            val previous = result.lastOrNull()
            if (previous == null || hypot(((point.x - previous.x) * pageWidth).toDouble(), ((point.y - previous.y) * pageHeight).toDouble()) >= MINIMUM_SAMPLE_DISTANCE_PX) result += point
            result
        }

    private fun segmentDistancePx(point: ShapePoint, start: ShapePoint, end: ShapePoint, pageWidth: Float, pageHeight: Float): Float {
        val px = point.x * pageWidth; val py = point.y * pageHeight
        val sx = start.x * pageWidth; val sy = start.y * pageHeight
        val ex = end.x * pageWidth; val ey = end.y * pageHeight
        val dx = ex - sx; val dy = ey - sy
        if (dx == 0f && dy == 0f) return hypot((px - sx).toDouble(), (py - sy).toDouble()).toFloat()
        val amount = (((px - sx) * dx + (py - sy) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot((px - (sx + dx * amount)).toDouble(), (py - (sy + dy * amount)).toDouble()).toFloat()
    }

    private fun polygonArea(points: List<ShapePoint>, pageWidth: Float, pageHeight: Float): Float {
        var twiceArea = 0.0
        points.indices.forEach { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.x * pageWidth * next.y * pageHeight - next.x * pageWidth * current.y * pageHeight
        }
        return abs(twiceArea).toFloat() / 2f
    }

    /**
     * QuickShape recognition: turns a rough freehand path into a perfect shape.
     * Returns a LINE for a nearly-straight open stroke, a RECTANGLE for a closed
     * stroke with four ~90° corners, an ELLIPSE for any other closed stroke, or
     * null when the gesture is too short/ambiguous (caller keeps the freeform path).
     */
    fun quickShape(
        rawPoints: List<ShapePoint>,
        pageWidth: Float,
        pageHeight: Float,
        colorArgb: Long,
        strokeWidth: Float,
    ): PageElement.Shape? {
        val safeWidth = pageWidth.coerceAtLeast(1f)
        val safeHeight = pageHeight.coerceAtLeast(1f)
        val points = rawPoints.map { ShapePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }.distinctConsecutive(safeWidth, safeHeight)
        if (points.size < 3) return null
        val start = points.first()
        val end = points.last()
        val minX = points.minOf(ShapePoint::x); val maxX = points.maxOf(ShapePoint::x)
        val minY = points.minOf(ShapePoint::y); val maxY = points.maxOf(ShapePoint::y)
        val bboxW = (maxX - minX) * safeWidth
        val bboxH = (maxY - minY) * safeHeight
        val span = hypot(bboxW.toDouble(), bboxH.toDouble()).toFloat().coerceAtLeast(1f)
        if (span < MINIMUM_EXTENT_PX) return null
        val closurePx = hypot(((end.x - start.x) * safeWidth).toDouble(), ((end.y - start.y) * safeHeight).toDouble()).toFloat()
        val closed = closurePx < span * CLOSURE_FRACTION
        if (!closed) {
            // Open stroke: snap to a straight line when every point stays near the chord.
            val maxDeviation = points.maxOf { segmentDistancePx(it, start, end, safeWidth, safeHeight) }
            if (maxDeviation < span * LINE_TOLERANCE_FRACTION) {
                return create(ShapeKind.LINE, start, snapEnd(start, end, safeWidth, safeHeight), safeWidth, safeHeight, colorArgb, strokeWidth)
            }
            return null
        }
        // Closed stroke: find dominant corners with an aggressive simplify pass.
        val simplified = simplify(points, safeWidth, safeHeight, span * CORNER_TOLERANCE_FRACTION)
        val corners = if (simplified.size > 1 && hypot(
                ((simplified.last().x - simplified.first().x) * safeWidth).toDouble(),
                ((simplified.last().y - simplified.first().y) * safeHeight).toDouble(),
            ).toFloat() < span * CLOSURE_FRACTION) simplified.dropLast(1) else simplified
        if (corners.size == 4 && cornersRightish(corners, safeWidth, safeHeight)) {
            return create(ShapeKind.RECTANGLE, ShapePoint(minX, minY), ShapePoint(maxX, maxY), safeWidth, safeHeight, colorArgb, strokeWidth)
        }
        return create(ShapeKind.ELLIPSE, ShapePoint(minX, minY), ShapePoint(maxX, maxY), safeWidth, safeHeight, colorArgb, strokeWidth)
    }

    private fun cornersRightish(corners: List<ShapePoint>, pageWidth: Float, pageHeight: Float): Boolean {
        if (corners.size < 3) return false
        var rightish = 0
        for (index in corners.indices) {
            val previous = corners[(index - 1 + corners.size) % corners.size]
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            val angle = cornerAngleDegrees(previous, current, next, pageWidth, pageHeight)
            if (abs(angleDelta(angle, 90f)) <= RIGHT_ANGLE_TOLERANCE_DEGREES) rightish++
        }
        return rightish >= 3
    }

    private fun cornerAngleDegrees(previous: ShapePoint, current: ShapePoint, next: ShapePoint, pageWidth: Float, pageHeight: Float): Float {
        val ax = (current.x - previous.x) * pageWidth; val ay = (current.y - previous.y) * pageHeight
        val bx = (next.x - current.x) * pageWidth; val by = (next.y - current.y) * pageHeight
        val dot = ax * bx + ay * by
        val cross = ax * by - ay * bx
        return abs(Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat())
    }

    private fun angleDelta(first: Float, second: Float): Float = ((first - second + 180f).mod(360f)) - 180f

    private const val MINIMUM_SAMPLE_DISTANCE_PX = 1f
    private const val SIMPLIFY_TOLERANCE_PX = 1.5f
    private const val MINIMUM_EXTENT_PX = 8f
    private const val MINIMUM_AREA_PX = 24f
    private const val CLOSURE_FRACTION = 0.15f
    private const val LINE_TOLERANCE_FRACTION = 0.06f
    private const val CORNER_TOLERANCE_FRACTION = 0.04f
    private const val RIGHT_ANGLE_TOLERANCE_DEGREES = 22f
}
