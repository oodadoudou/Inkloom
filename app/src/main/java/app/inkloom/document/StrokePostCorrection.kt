package app.inkloom.document

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Applies CSP-style correction after pen-up. Live input stabilization stays independent so a
 * non-zero value never adds latency while the stylus is moving.
 *
 * Default painting must never perform a visibly destructive geometry rewrite at pen-up. The
 * production pen-up path passes a [maxDisplacementPx] bound (see [POST_CORRECTION_MAX_SCREEN_PX]
 * and [screenBoundMaxDisplacement]) that:
 *  - clamps every corrected point toward the raw path so no point moves more than the bound,
 *  - caps the SIMPLIFY tolerance so Douglas-Peucker cannot collapse a stroke to its endpoints
 *    (the previously observed 37-point zigzag -> 2-point regression),
 *  - applies the bound to the final (possibly Bezier-sampled) path as well.
 *
 * The pure function keeps the unbounded behavior when no bound is supplied so geometry tools and
 * existing callers are unaffected; the default painting pipeline always supplies the bound.
 */
object StrokePostCorrection {
    /** The visible jump allowed at pen-up for the default pipeline, in screen pixels. */
    const val POST_CORRECTION_MAX_SCREEN_PX = 2.5f

    /** Page-space displacement bound that keeps the visible jump at [maxScreenPx] regardless of zoom. */
    fun screenBoundMaxDisplacement(maxScreenPx: Float, pageWidth: Float, viewWidthPx: Int, viewportScale: Float): Float {
        val screenPxPerPagePx = viewWidthPx.coerceAtLeast(1).toFloat() * viewportScale.coerceAtLeast(1f) / pageWidth.coerceAtLeast(1f)
        return maxScreenPx / screenPxPerPagePx
    }

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
        maxDisplacementPx: Float = Float.POSITIVE_INFINITY,
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
            correctBetweenCorners(stroke.copy(points = sharpened), effective, pageWidth, pageHeight, quadraticBezier, maxDisplacementPx)
        } else correctSegment(stroke.copy(points = sharpened), effective, pageWidth, pageHeight, maxDisplacementPx)
        val result = if (!preserveSharpAngles && quadraticBezier && corrected.points.size >= 3) {
            corrected.copy(points = quadraticSpline(corrected.points))
        } else corrected
        return result.copy(points = clampToRawPath(result.points, stroke.points, maxDisplacementPx, pageWidth, pageHeight))
    }

    /** Quantified audit of one correction invocation, for regression and tooling. */
    data class CorrectionReport(
        val inputPoints: Int,
        val outputPoints: Int,
        val maxDisplacementPx: Float,
        val meanDisplacementPx: Float,
        val inputLengthPx: Float,
        val outputLengthPx: Float,
        val boundingBoxDeltaPx: Float,
    ) {
        val destructive: Boolean get() = outputPoints < 3 && inputPoints >= 3
    }

    fun audit(
        stroke: InkStroke,
        amount: Float,
        adjustBySpeed: Boolean = false,
        adjustByScale: Boolean = false,
        viewportScale: Float = 1f,
        pageWidth: Float,
        pageHeight: Float,
        quadraticBezier: Boolean = false,
        preserveSharpAngles: Boolean = false,
        maxDisplacementPx: Float = Float.POSITIVE_INFINITY,
    ): CorrectionReport {
        val output = apply(
            stroke, amount, adjustBySpeed, adjustByScale, viewportScale, pageWidth, pageHeight,
            quadraticBezier, preserveSharpAngles, maxDisplacementPx,
        ).points
        val input = stroke.points
        val displacements = output.map { nearestOnPolyline(it, input, pageWidth, pageHeight).distancePx }
        return CorrectionReport(
            inputPoints = input.size,
            outputPoints = output.size,
            maxDisplacementPx = displacements.maxOrNull() ?: 0f,
            meanDisplacementPx = displacements.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f,
            inputLengthPx = pathLengthPx(input, pageWidth, pageHeight),
            outputLengthPx = pathLengthPx(output, pageWidth, pageHeight),
            boundingBoxDeltaPx = max(
                abs(boundsWidthPx(output, pageWidth) - boundsWidthPx(input, pageWidth)),
                abs(boundsHeightPx(output, pageHeight) - boundsHeightPx(input, pageHeight)),
            ),
        )
    }

    private fun correctBetweenCorners(
        stroke: InkStroke,
        amount: Float,
        pageWidth: Float,
        pageHeight: Float,
        quadraticBezier: Boolean,
        maxDisplacementPx: Float,
    ): InkStroke {
        val corners = cornerIndices(stroke.points, pageWidth, pageHeight)
        if (corners.isEmpty()) return correctSegment(stroke, amount, pageWidth, pageHeight, maxDisplacementPx)
        val boundaries = buildList { add(0); addAll(corners); add(stroke.points.lastIndex) }.distinct().sorted()
        val output = ArrayList<StrokePoint>(stroke.points.size)
        boundaries.zipWithNext().forEach { (start, end) ->
            val segment = stroke.copy(points = stroke.points.subList(start, end + 1))
            val correctedSegment = correctSegment(segment, amount, pageWidth, pageHeight, maxDisplacementPx).points
            val corrected = if (quadraticBezier && correctedSegment.size >= 3) quadraticSpline(correctedSegment) else correctedSegment
            if (output.isNotEmpty() && corrected.isNotEmpty()) output.removeAt(output.lastIndex)
            output.addAll(corrected)
        }
        return stroke.copy(points = output.ifEmpty { stroke.points })
    }

    private fun correctSegment(
        stroke: InkStroke,
        amount: Float,
        pageWidth: Float,
        pageHeight: Float,
        maxDisplacementPx: Float,
    ): InkStroke {
        val raw = stroke.points
        val smoothed = StrokeCorrection.apply(stroke, CorrectLineMode.SMOOTH, amount, pageWidth, pageHeight)
        val bounded = clampToRawPath(smoothed.points, raw, maxDisplacementPx, pageWidth, pageHeight)
        if (amount < .55f || bounded.size < 4) return stroke.copy(points = bounded)
        val requestedStrength = (amount - .55f) / .45f * .35f
        val simplified = StrokeCorrection.apply(
            stroke.copy(points = bounded),
            CorrectLineMode.SIMPLIFY,
            cappedSimplifyStrength(requestedStrength, maxDisplacementPx),
            pageWidth,
            pageHeight,
        )
        return stroke.copy(points = clampToRawPath(simplified.points, raw, maxDisplacementPx, pageWidth, pageHeight))
    }

    /** Caps Douglas-Peucker tolerance (1 + strength*200 px) at the displacement bound. */
    private fun cappedSimplifyStrength(requested: Float, maxDisplacementPx: Float): Float {
        if (maxDisplacementPx.isInfinite() || maxDisplacementPx <= 0f) return requested
        val maxStrength = ((maxDisplacementPx - 1f) / 200f).coerceAtLeast(0f)
        return requested.coerceAtMost(maxStrength)
    }

    /** Pulls every point back toward the raw polyline so none moves further than [maxDisplacementPx]. */
    private fun clampToRawPath(
        points: List<StrokePoint>,
        raw: List<StrokePoint>,
        maxDisplacementPx: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): List<StrokePoint> {
        if (maxDisplacementPx.isInfinite() || maxDisplacementPx <= 0f) return points
        if (raw.isEmpty()) return points
        return points.map { point ->
            val nearest = nearestOnPolyline(point, raw, pageWidth, pageHeight)
            if (nearest.distancePx <= maxDisplacementPx) point
            else {
                val length = nearest.distancePx
                if (length <= .0001f) point
                else point.copy(
                    x = (nearest.x + (point.x * pageWidth - nearest.x) / length * maxDisplacementPx) / pageWidth,
                    y = (nearest.y + (point.y * pageHeight - nearest.y) / length * maxDisplacementPx) / pageHeight,
                )
            }
        }
    }

    private data class NearestPoint(val x: Float, val y: Float, val distancePx: Float)

    private fun nearestOnPolyline(point: StrokePoint, polyline: List<StrokePoint>, pageWidth: Float, pageHeight: Float): NearestPoint {
        val px = point.x * pageWidth
        val py = point.y * pageHeight
        if (polyline.size == 1) {
            val sx = polyline[0].x * pageWidth
            val sy = polyline[0].y * pageHeight
            return NearestPoint(sx, sy, hypot((px - sx).toDouble(), (py - sy).toDouble()).toFloat())
        }
        var bestX = px
        var bestY = py
        var bestDistance = Float.MAX_VALUE
        for (index in 1 until polyline.size) {
            val sx = polyline[index - 1].x * pageWidth
            val sy = polyline[index - 1].y * pageHeight
            val ex = polyline[index].x * pageWidth
            val ey = polyline[index].y * pageHeight
            val segX = ex - sx
            val segY = ey - sy
            val lengthSquared = segX * segX + segY * segY
            val t = if (lengthSquared <= .0001f) 0f else (((px - sx) * segX + (py - sy) * segY) / lengthSquared).coerceIn(0f, 1f)
            val nx = sx + segX * t
            val ny = sy + segY * t
            val distance = hypot((px - nx).toDouble(), (py - ny).toDouble()).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = nx
                bestY = ny
            }
        }
        return NearestPoint(bestX, bestY, bestDistance)
    }

    private fun pathLengthPx(points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): Float {
        if (points.size < 2) return 0f
        var length = 0f
        for (index in 1 until points.size) {
            length += hypot(
                ((points[index].x - points[index - 1].x) * pageWidth).toDouble(),
                ((points[index].y - points[index - 1].y) * pageHeight).toDouble(),
            ).toFloat()
        }
        return length
    }

    private fun boundsWidthPx(points: List<StrokePoint>, pageWidth: Float): Float =
        (points.maxOf(StrokePoint::x) - points.minOf(StrokePoint::x)) * pageWidth

    private fun boundsHeightPx(points: List<StrokePoint>, pageHeight: Float): Float =
        (points.maxOf(StrokePoint::y) - points.minOf(StrokePoint::y)) * pageHeight

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
