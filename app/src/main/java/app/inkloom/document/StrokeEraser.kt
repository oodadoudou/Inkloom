package app.inkloom.document

import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class StrokeEraseResult(val replacements: List<InkStroke>)

/** Geometry for a local, circular-on-screen eraser.
 *
 * Coordinates and radii are normalized independently, so a circular eraser on
 * a portrait page remains circular on screen. Boundary points interpolate every
 * recorded input channel, keeping pressure and dynamics continuous after a cut.
 */
object StrokeEraser {
    fun intersects(
        stroke: InkStroke,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ): Boolean {
        val safeRadiusX = radiusX.coerceAtLeast(MIN_RADIUS)
        val safeRadiusY = radiusY.coerceAtLeast(MIN_RADIUS)
        if (stroke.points.any { inside(it, centerX, centerY, safeRadiusX, safeRadiusY) }) return true
        return stroke.points.zipWithNext().any { (start, end) ->
            segmentTouchesEllipse(start, end, centerX, centerY, safeRadiusX, safeRadiusY)
        }
    }

    fun erase(
        stroke: InkStroke,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        idFactory: (Int) -> String = { UUID.randomUUID().toString() },
    ): StrokeEraseResult? {
        val safeRadiusX = radiusX.coerceAtLeast(MIN_RADIUS)
        val safeRadiusY = radiusY.coerceAtLeast(MIN_RADIUS)
        val points = stroke.points
        if (points.isEmpty()) return null
        if (points.size == 1) {
            return if (inside(points.single(), centerX, centerY, safeRadiusX, safeRadiusY)) {
                StrokeEraseResult(emptyList())
            } else {
                null
            }
        }

        val fragments = mutableListOf<MutableList<StrokePoint>>()
        var current: MutableList<StrokePoint>? = null
        var erasedAny = false

        points.zipWithNext().forEach { (start, end) ->
            val boundaries = buildList {
                add(0f)
                addAll(intersectionParameters(start, end, centerX, centerY, safeRadiusX, safeRadiusY))
                add(1f)
            }.sorted().distinctBy { (it * PARAMETER_PRECISION).toInt() }

            boundaries.zipWithNext().forEach { (from, to) ->
                if (to - from <= PARAMETER_EPSILON) return@forEach
                val midpoint = interpolate(start, end, (from + to) / 2f)
                if (inside(midpoint, centerX, centerY, safeRadiusX, safeRadiusY)) {
                    erasedAny = true
                    current = null
                } else {
                    val intervalStart = interpolate(start, end, from)
                    val intervalEnd = interpolate(start, end, to)
                    val target = current ?: mutableListOf<StrokePoint>().also {
                        fragments += it
                        current = it
                    }
                    target.addDistinct(intervalStart)
                    target.addDistinct(intervalEnd)
                }
            }
        }

        if (!erasedAny) return null
        val replacements = fragments
            .map { fragment -> fragment.removeConsecutiveDuplicates() }
            .filter { fragment -> fragment.size >= 2 && fragment.zipWithNext().any { (a, b) -> !samePosition(a, b) } }
            .mapIndexed { index, fragment -> stroke.copy(id = idFactory(index), points = fragment) }
        return StrokeEraseResult(replacements)
    }

    private fun intersectionParameters(
        start: StrokePoint,
        end: StrokePoint,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ): List<Float> {
        val startX = (start.x - centerX) / radiusX
        val startY = (start.y - centerY) / radiusY
        val deltaX = (end.x - start.x) / radiusX
        val deltaY = (end.y - start.y) / radiusY
        val a = deltaX * deltaX + deltaY * deltaY
        if (a <= PARAMETER_EPSILON) return emptyList()
        val b = 2f * (startX * deltaX + startY * deltaY)
        val c = startX * startX + startY * startY - 1f
        val discriminant = b * b - 4f * a * c
        if (discriminant <= PARAMETER_EPSILON) return emptyList()
        val root = sqrt(discriminant)
        return listOf((-b - root) / (2f * a), (-b + root) / (2f * a))
            .filter { it > PARAMETER_EPSILON && it < 1f - PARAMETER_EPSILON }
            .sorted()
    }

    private fun segmentTouchesEllipse(
        start: StrokePoint,
        end: StrokePoint,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ): Boolean {
        val startX = (start.x - centerX) / radiusX
        val startY = (start.y - centerY) / radiusY
        val deltaX = (end.x - start.x) / radiusX
        val deltaY = (end.y - start.y) / radiusY
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        val parameter = if (lengthSquared <= PARAMETER_EPSILON) {
            0f
        } else {
            (-(startX * deltaX + startY * deltaY) / lengthSquared).coerceIn(0f, 1f)
        }
        val closestX = startX + deltaX * parameter
        val closestY = startY + deltaY * parameter
        return closestX * closestX + closestY * closestY <= 1f + INSIDE_EPSILON
    }

    private fun inside(point: StrokePoint, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): Boolean {
        val x = (point.x - centerX) / radiusX
        val y = (point.y - centerY) / radiusY
        return x * x + y * y < 1f - INSIDE_EPSILON
    }

    private fun interpolate(start: StrokePoint, end: StrokePoint, parameter: Float): StrokePoint {
        val t = parameter.coerceIn(0f, 1f)
        val orientationDelta = atan2(
            sin(end.orientation - start.orientation),
            cos(end.orientation - start.orientation),
        )
        return StrokePoint(
            x = lerp(start.x, end.x, t),
            y = lerp(start.y, end.y, t),
            pressure = lerp(start.pressure, end.pressure, t),
            timeMs = (start.timeMs + (end.timeMs - start.timeMs) * t).roundToLong(),
            tilt = lerp(start.tilt, end.tilt, t),
            orientation = start.orientation + orientationDelta * t,
            velocity = lerp(start.velocity, end.velocity, t),
        )
    }

    private fun MutableList<StrokePoint>.addDistinct(point: StrokePoint) {
        if (lastOrNull()?.let { samePosition(it, point) } != true) add(point)
    }

    private fun List<StrokePoint>.removeConsecutiveDuplicates(): List<StrokePoint> = buildList {
        this@removeConsecutiveDuplicates.forEach { point ->
            if (lastOrNull()?.let { samePosition(it, point) } != true) add(point)
        }
    }

    private fun samePosition(first: StrokePoint, second: StrokePoint): Boolean =
        abs(first.x - second.x) <= POSITION_EPSILON && abs(first.y - second.y) <= POSITION_EPSILON

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    private const val MIN_RADIUS = 0.000001f
    private const val POSITION_EPSILON = 0.000001f
    private const val PARAMETER_EPSILON = 0.000001f
    private const val INSIDE_EPSILON = 0.000001f
    private const val PARAMETER_PRECISION = 1_000_000f
}
