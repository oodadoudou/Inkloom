package app.inkloom.document

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Applies CSP-style correction after pen-up. Live input stabilization stays independent so a
 * non-zero value never adds latency while the stylus is moving.
 */
object StrokePostCorrection {
    fun apply(
        stroke: InkStroke,
        amount: Float,
        adjustBySpeed: Boolean,
        adjustByScale: Boolean,
        viewportScale: Float,
        pageWidth: Float,
        pageHeight: Float,
        quadraticBezier: Boolean = false,
        preserveSharpAngles: Boolean = false,
    ): InkStroke {
        if (stroke.points.size < 3) return stroke
        val sharpened = if (preserveSharpAngles) sharpenCorners(stroke.points, stroke.width, pageWidth, pageHeight) else stroke.points
        if (amount <= 0f) return if (sharpened === stroke.points) stroke else stroke.copy(points = sharpened)
        var effective = amount.coerceIn(0f, 1f)
        if (adjustBySpeed) {
            val averageVelocity = stroke.points.asSequence().map(StrokePoint::velocity).filter(Float::isFinite).average().takeIf(Double::isFinite)?.toFloat() ?: 0f
            // Preserve fast, expressive gestures while applying the requested cleanup to slow ink.
            effective *= (1f / (1f + averageVelocity.coerceAtLeast(0f) * .35f)).coerceIn(.25f, 1f)
        }
        if (adjustByScale) effective /= sqrt(viewportScale.coerceAtLeast(1f))
        if (effective < .01f) return if (sharpened === stroke.points) stroke else stroke.copy(points = sharpened)

        val corrected = if (preserveSharpAngles) {
            correctBetweenCorners(stroke.copy(points = sharpened), effective, pageWidth, pageHeight, quadraticBezier)
        } else correctSegment(stroke.copy(points = sharpened), effective, pageWidth, pageHeight)
        return if (!preserveSharpAngles && quadraticBezier && corrected.points.size >= 3) corrected.copy(points = quadraticSpline(corrected.points)) else corrected
    }

    private fun correctBetweenCorners(stroke: InkStroke, amount: Float, pageWidth: Float, pageHeight: Float, quadraticBezier: Boolean): InkStroke {
        val corners = cornerIndices(stroke.points, pageWidth, pageHeight)
        if (corners.isEmpty()) return correctSegment(stroke, amount, pageWidth, pageHeight)
        val boundaries = buildList { add(0); addAll(corners); add(stroke.points.lastIndex) }.distinct().sorted()
        val output = ArrayList<StrokePoint>(stroke.points.size)
        boundaries.zipWithNext().forEach { (start, end) ->
            val segment = stroke.copy(points = stroke.points.subList(start, end + 1))
            val correctedSegment = correctSegment(segment, amount, pageWidth, pageHeight).points
            val corrected = if (quadraticBezier && correctedSegment.size >= 3) quadraticSpline(correctedSegment) else correctedSegment
            if (output.isNotEmpty() && corrected.isNotEmpty()) output.removeAt(output.lastIndex)
            output.addAll(corrected)
        }
        return stroke.copy(points = output.ifEmpty { stroke.points })
    }

    private fun correctSegment(stroke: InkStroke, amount: Float, pageWidth: Float, pageHeight: Float): InkStroke {
        val smoothed = StrokeCorrection.apply(stroke, CorrectLineMode.SMOOTH, amount, pageWidth, pageHeight)
        return if (amount < .55f || smoothed.points.size < 4) smoothed else {
            StrokeCorrection.apply(smoothed, CorrectLineMode.SIMPLIFY, (amount - .55f) / .45f * .35f, pageWidth, pageHeight)
        }
    }

    /** Restores a rounded corner to the intersection of its incoming/outgoing tangents. CSP does
     * not publish its private threshold; this bounded geometric version reproduces the documented
     * pointed-corner behavior without moving a point more than two brush diameters. */
    private fun sharpenCorners(points: List<StrokePoint>, brushWidth: Float, pageWidth: Float, pageHeight: Float): List<StrokePoint> {
        val corners = cornerIndices(points, pageWidth, pageHeight)
        if (corners.isEmpty()) return points
        val output = points.toMutableList()
        val maximumMove = maxOf(4f, brushWidth * 2f)
        corners.forEach { index ->
            if (index < 2 || index + 2 > points.lastIndex) return@forEach
            val intersection = lineIntersection(
                points[index - 2], points[index - 1], points[index + 1], points[index + 2], pageWidth, pageHeight,
            ) ?: return@forEach
            val current = points[index]
            val distance = hypot(
                ((intersection.first - current.x) * pageWidth).toDouble(),
                ((intersection.second - current.y) * pageHeight).toDouble(),
            ).toFloat()
            if (distance <= maximumMove && intersection.first in 0f..1f && intersection.second in 0f..1f) {
                output[index] = current.copy(x = intersection.first, y = intersection.second)
            }
        }
        return output
    }

    private fun cornerIndices(points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): List<Int> = buildList {
        for (index in 1 until points.lastIndex) {
            val previous = points[index - 1]
            val current = points[index]
            val next = points[index + 1]
            val ax = (current.x - previous.x) * pageWidth
            val ay = (current.y - previous.y) * pageHeight
            val bx = (next.x - current.x) * pageWidth
            val by = (next.y - current.y) * pageHeight
            val aLength = hypot(ax.toDouble(), ay.toDouble()).toFloat()
            val bLength = hypot(bx.toDouble(), by.toDouble()).toFloat()
            if (aLength < .75f || bLength < .75f) continue
            val cosine = ((ax * bx + ay * by) / (aLength * bLength)).coerceIn(-1f, 1f)
            if (cosine < .5f) add(index)
        }
    }

    private fun lineIntersection(
        a: StrokePoint,
        b: StrokePoint,
        c: StrokePoint,
        d: StrokePoint,
        pageWidth: Float,
        pageHeight: Float,
    ): Pair<Float, Float>? {
        val ax = a.x * pageWidth; val ay = a.y * pageHeight
        val bx = b.x * pageWidth; val by = b.y * pageHeight
        val cx = c.x * pageWidth; val cy = c.y * pageHeight
        val dx = d.x * pageWidth; val dy = d.y * pageHeight
        val denominator = (ax - bx) * (cy - dy) - (ay - by) * (cx - dx)
        if (abs(denominator) < .0001f) return null
        val first = ax * by - ay * bx
        val second = cx * dy - cy * dx
        return ((first * (cx - dx) - (ax - bx) * second) / denominator / pageWidth) to
            ((first * (cy - dy) - (ay - by) * second) / denominator / pageHeight)
    }

    /** Converts corrected anchors into a sampled quadratic B-spline. Renderers continue to consume
     * ordinary points, so live replay, export and raster commit share the same durable curve. */
    private fun quadraticSpline(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 3) return points
        val output = ArrayList<StrokePoint>(points.size * 2)
        output += points.first()
        var start = points.first()
        for (index in 1 until points.lastIndex) {
            val control = points[index]
            val end = midpoint(control, points[index + 1])
            output += quadratic(start, control, end, .5f)
            output += end
            start = end
        }
        val control = points[points.lastIndex - 1]
        val end = points.last()
        output += quadratic(start, control, end, .5f)
        output += end
        return output
    }

    private fun midpoint(a: StrokePoint, b: StrokePoint): StrokePoint = interpolate(a, b, .5f)

    private fun quadratic(a: StrokePoint, control: StrokePoint, b: StrokePoint, t: Float): StrokePoint {
        val first = interpolate(a, control, t)
        val second = interpolate(control, b, t)
        return interpolate(first, second, t)
    }

    private fun interpolate(a: StrokePoint, b: StrokePoint, t: Float): StrokePoint = a.copy(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t,
        pressure = a.pressure + (b.pressure - a.pressure) * t,
        timeMs = (a.timeMs + (b.timeMs - a.timeMs) * t).roundToLong(),
        tilt = a.tilt + (b.tilt - a.tilt) * t,
        orientation = a.orientation + (b.orientation - a.orientation) * t,
        velocity = a.velocity + (b.velocity - a.velocity) * t,
        pathDistance = a.pathDistance + (b.pathDistance - a.pathDistance) * t,
        pigmentArgb = if (t < .5f) a.pigmentArgb else b.pigmentArgb,
    )
}
