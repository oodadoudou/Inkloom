package app.inkloom.engine.v5.runtime

import kotlin.math.*

/**
 * Draw and hold: a held stroke becomes a straight line, a smooth curve, a circular arc, a circle or
 * an ellipse. Recognition is conservative: ambiguous handwriting remains the original stroke.
 */
object StrokeRegularizer {
    enum class Kind { LINE, CURVE, ARC, CIRCLE, ELLIPSE }
    data class Result(val kind: Kind, val samples: List<RawDocumentSample>, val shape: HeldShape)

    fun fit(input: List<RawDocumentSample>): Result? {
        if (input.size < 4) return null
        val samples = input.filterIndexed { index, sample -> index == 0 ||
            hypot(sample.x - input[index - 1].x, sample.y - input[index - 1].y) > .05f }
        if (samples.size < 4) return null
        val lengths = FloatArray(samples.size)
        for (i in 1 until samples.size) lengths[i] = lengths[i - 1] + hypot(samples[i].x - samples[i - 1].x, samples[i].y - samples[i - 1].y)
        val length = lengths.last()
        if (length < 24f) return null
        val first = samples.first(); val last = samples.last()
        val chord = hypot(last.x - first.x, last.y - first.y)
        val shape = if (chord < length * CLOSED_CHORD && length > 60f) closedShape(samples, lengths)
            else if (chord < length * .35f) null
            else openShape(samples, lengths, chord)
        shape ?: return null
        return Result(shape.kind, shape.samples(samples, lengths), shape)
    }

    /** Continue holding to scale and rotate around the first point (centre for closed shapes). */
    fun moveEndpoint(samples: List<RawDocumentSample>, x: Float, y: Float, snapAngle: Boolean = false): List<RawDocumentSample> {
        if (samples.size < 2) return samples
        val (c, s) = similarity(samples.first().x, samples.first().y, samples.last().x, samples.last().y, x, y, snapAngle)
        val origin = samples.first()
        return samples.map { sample ->
            val dx = sample.x - origin.x; val dy = sample.y - origin.y
            sample.copy(x = origin.x + c * dx - s * dy, y = origin.y + s * dx + c * dy)
        }
    }

    /** The shape after the same scale and rotation as [moveEndpoint]. */
    fun moveEndpoint(shape: HeldShape, x: Float, y: Float, snapAngle: Boolean = false): HeldShape {
        val (sx, sy) = shape.point(0f); val (ex, ey) = shape.point(1f)
        if (shape.closed) return shape
        val (c, s) = similarity(sx, sy, ex, ey, x, y, snapAngle)
        return shape.transformed(sx, sy, c, s)
    }

    private fun similarity(ox: Float, oy: Float, ex: Float, ey: Float, x: Float, y: Float, snapAngle: Boolean): Pair<Float, Float> {
        val originalLength = hypot(ex - ox, ey - oy).coerceAtLeast(.001f)
        val scale = hypot(x - ox, y - oy).coerceAtLeast(1f) / originalLength
        var angle = atan2(y - oy, x - ox)
        if (snapAngle) angle = round(angle / (PI.toFloat() / 12f)) * PI.toFloat() / 12f
        val rotation = angle - atan2(ey - oy, ex - ox)
        return cos(rotation) * scale to sin(rotation) * scale
    }

    private fun openShape(samples: List<RawDocumentSample>, lengths: FloatArray, chord: Float): HeldShape? {
        val first = samples.first(); val last = samples.last()
        val dx = last.x - first.x; val dy = last.y - first.y
        val length = lengths.last()
        val lineError = samples.maxOf { abs((it.x - first.x) * dy - (it.y - first.y) * dx) / chord }
        if (lineError <= max(2f, chord * .035f)) return HeldShape.Line(first.x, first.y, last.x, last.y)
        val times = lengths.map { it / length }
        val fitted = quadratic(samples, times)
        val quadratic = fitted?.first
        val quadraticError = fitted?.second
        val quadraticFits = quadraticError != null && quadraticError <= max(3f, length * .045f)
        val arc = arc(samples)
        // A circular arc wins unless a smooth curve fits clearly better (a parabola is not an arc).
        if (arc != null && (!quadraticFits || arc.second <= quadraticError!! + .5f)) return arc.first
        return if (quadraticFits) quadratic else null
    }

    /** The best quadratic curve and its largest distance from the samples. */
    private fun quadratic(samples: List<RawDocumentSample>, initialTimes: List<Float>): Pair<HeldShape.Quadratic, Float>? {
        val first = samples.first(); val last = samples.last()
        val times = initialTimes.toFloatArray()
        var curve: HeldShape.Quadratic? = null
        // Fit the control point, then move each sample's parameter to its nearest point on the curve.
        repeat(4) { iteration ->
            var denominator = 0f; var numeratorX = 0f; var numeratorY = 0f
            samples.forEachIndexed { i, sample ->
                val t = times[i]; val one = 1f - t; val coefficient = 2f * one * t
                denominator += coefficient * coefficient
                numeratorX += coefficient * (sample.x - one * one * first.x - t * t * last.x)
                numeratorY += coefficient * (sample.y - one * one * first.y - t * t * last.y)
            }
            if (denominator < .001f) return null
            val q = HeldShape.Quadratic(first.x, first.y, numeratorX / denominator, numeratorY / denominator, last.x, last.y)
            curve = q
            for (i in 1 until samples.size - 1) {
                val t = times[i]; val (bx, by) = q.point(t)
                val dx = 2f * ((1f - t) * (q.cx - q.x0) + t * (q.x1 - q.cx)); val dy = 2f * ((1f - t) * (q.cy - q.y0) + t * (q.y1 - q.cy))
                val ddx = 2f * (q.x1 - 2f * q.cx + q.x0); val ddy = 2f * (q.y1 - 2f * q.cy + q.y0)
                val ex = bx - samples[i].x; val ey = by - samples[i].y
                val derivative = dx * dx + dy * dy + ex * ddx + ey * ddy
                if (abs(derivative) > 1e-6f) times[i] = (t - (ex * dx + ey * dy) / derivative).coerceIn(0f, 1f)
            }
        }
        val fitted = curve ?: return null
        return fitted to samples.indices.maxOf { i -> fitted.point(times[i]).let { (x, y) -> hypot(samples[i].x - x, samples[i].y - y) } }
    }

    /** A circular arc through the stroke, with its largest distance from the stroke. */
    private fun arc(samples: List<RawDocumentSample>): Pair<HeldShape.Arc, Float>? {
        val (cx, cy, r) = circleFit(samples) ?: return null
        val chord = hypot(samples.last().x - samples.first().x, samples.last().y - samples.first().y)
        if (r > chord * 4f) return null
        val error = samples.maxOf { abs(hypot(it.x - cx, it.y - cy) - r) }
        if (error > max(3f, r * .06f)) return null
        val start = atan2(samples.first().y - cy, samples.first().x - cx)
        var sweep = 0f; var previous = start
        samples.drop(1).forEach { sample ->
            val angle = atan2(sample.y - cy, sample.x - cx)
            var delta = angle - previous
            while (delta > PI) delta -= (2 * PI).toFloat(); while (delta < -PI) delta += (2 * PI).toFloat()
            sweep += delta; previous = angle
        }
        if (abs(sweep) < Math.toRadians(20.0) || abs(sweep) > Math.toRadians(340.0)) return null
        return HeldShape.Arc(cx, cy, r, start, sweep) to error
    }

    /** Ellipse (or circle) through a closed stroke, from the stroke's centre and principal axes. */
    private fun closedShape(samples: List<RawDocumentSample>, lengths: FloatArray): HeldShape? {
        // Resample evenly along the stroke so slow parts do not pull the fit.
        val length = lengths.last(); val count = 128
        var index = 0
        val points = List(count) { k ->
            val target = length * k / count
            while (index < lengths.size - 2 && lengths[index + 1] < target) index++
            val span = (lengths[index + 1] - lengths[index]).coerceAtLeast(1e-4f)
            val t = ((target - lengths[index]) / span).coerceIn(0f, 1f)
            samples[index].x + (samples[index + 1].x - samples[index].x) * t to samples[index].y + (samples[index + 1].y - samples[index].y) * t
        }
        val meanX = points.sumOf { it.first.toDouble() }.toFloat() / count
        val meanY = points.sumOf { it.second.toDouble() }.toFloat() / count
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        points.forEach { (x, y) -> val dx = x - meanX; val dy = y - meanY; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        sxx /= count; syy /= count; sxy /= count
        val rotation = (.5 * atan2(2 * sxy, sxx - syy)).toFloat()
        val c = cos(rotation); val s = sin(rotation)
        // Radii and centre from the stroke's extent along its principal axes.
        val us = points.map { (x, y) -> (x - meanX) * c + (y - meanY) * s }; val vs = points.map { (x, y) -> -(x - meanX) * s + (y - meanY) * c }
        var a = (us.max() - us.min()) / 2f; var b = (vs.max() - vs.min()) / 2f
        val midU = (us.max() + us.min()) / 2f; val midV = (vs.max() + vs.min()) / 2f
        val cx = meanX + midU * c - midV * s; val cy = meanY + midU * s + midV * c
        if (b < 4f) return null
        fun radial(x: Float, y: Float): Float { val u = ((x - cx) * c + (y - cy) * s) / a; val v = (-(x - cx) * s + (y - cy) * c) / b; return hypot(u, v) }
        // Centre the fit on the stroke: scale both radii by the median radial distance.
        val median = points.map { radial(it.first, it.second) }.sorted()[count / 2]
        a *= median; b *= median
        val errors = points.map { abs(radial(it.first, it.second) - 1f) }
        if (errors.average() > .08 || errors.max() > .25f) return null
        val first = samples.first()
        // Ellipse parameter (not polar angle) of the first point.
        val start = atan2((-(first.x - cx) * s + (first.y - cy) * c) / b, ((first.x - cx) * c + (first.y - cy) * s) / a)
        // The drawing direction decides which way round the shape goes.
        var turning = 0.0
        for (i in 1 until count) turning += (points[i - 1].first - cx) * (points[i].second - cy) - (points[i - 1].second - cy) * (points[i].first - cx)
        val direction = if (turning >= 0) 1f else -1f
        return if (b / a > CIRCLE_RATIO) HeldShape.Ellipse(cx, cy, (a + b) / 2f, (a + b) / 2f, 0f, atan2(first.y - cy, first.x - cx), direction, circle = true)
            else HeldShape.Ellipse(cx, cy, a, b, rotation, start, direction, circle = false)
    }

    /** Algebraic least-squares circle (Kåsa). */
    private fun circleFit(samples: List<RawDocumentSample>): Triple<Float, Float, Float>? {
        val mx = samples.sumOf { it.x.toDouble() } / samples.size; val my = samples.sumOf { it.y.toDouble() } / samples.size
        var suu = 0.0; var svv = 0.0; var suv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
        samples.forEach { p ->
            val u = p.x - mx; val v = p.y - my
            suu += u * u; svv += v * v; suv += u * v; suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        val determinant = suu * svv - suv * suv
        if (abs(determinant) < 1e-6) return null
        val bu = .5 * (suuu + suvv); val bv = .5 * (svvv + svuu)
        val uc = (bu * svv - bv * suv) / determinant; val vc = (bv * suu - bu * suv) / determinant
        val r = sqrt(uc * uc + vc * vc + (suu + svv) / samples.size)
        return Triple((uc + mx).toFloat(), (vc + my).toFloat(), r.toFloat())
    }

    private const val CLOSED_CHORD = .15f
    private const val CIRCLE_RATIO = .88f
}

/**
 * The regular shape a held stroke became, in surface pixels. It can be re-sampled along its
 * length for painting and reshaped by dragging its handles after the stroke is drawn.
 */
sealed interface HeldShape {
    val kind: StrokeRegularizer.Kind
    val closed: Boolean get() = false
    /** Position at [t] in 0..1 along the shape. */
    fun point(t: Float): Pair<Float, Float>
    fun handles(): List<Pair<Float, Float>>
    fun withHandle(index: Int, x: Float, y: Float): HeldShape
    /** Rotated and scaled by (c, s) around (ox, oy). */
    fun transformed(ox: Float, oy: Float, c: Float, s: Float): HeldShape

    /** Painting samples along the shape; pressure and timing follow the original stroke. */
    fun samples(template: List<RawDocumentSample>, templateLengths: FloatArray? = null): List<RawDocumentSample> {
        val lengths = templateLengths ?: FloatArray(template.size).also { l ->
            for (i in 1 until template.size) l[i] = l[i - 1] + hypot(template[i].x - template[i - 1].x, template[i].y - template[i - 1].y)
        }
        val total = lengths.last().coerceAtLeast(1e-4f)
        var approximate = 0f; var previous = point(0f)
        for (k in 1..64) { val p = point(k / 64f); approximate += hypot(p.first - previous.first, p.second - previous.second); previous = p }
        val count = ceil(approximate / 2f).toInt().coerceIn(32, 1024)
        var sourceIndex = 0
        val first = template.first(); val last = template.last()
        return List(count + if (closed) 1 else 0) { i ->
            val t = (i.toFloat() / (count - if (closed) 0 else 1)).coerceAtMost(1f)
            while (sourceIndex < lengths.lastIndex && lengths[sourceIndex + 1] / total <= t) sourceIndex++
            val (x, y) = point(t)
            template[sourceIndex].copy(x = x, y = y,
                eventTimeNanos = first.eventTimeNanos + ((last.eventTimeNanos - first.eventTimeNanos) * t).toLong(),
                enqueuedTimeNanos = first.enqueuedTimeNanos + ((last.enqueuedTimeNanos - first.enqueuedTimeNanos) * t).toLong())
        }
    }

    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : HeldShape {
        override val kind get() = StrokeRegularizer.Kind.LINE
        override fun point(t: Float) = x0 + (x1 - x0) * t to y0 + (y1 - y0) * t
        override fun handles() = listOf(x0 to y0, x1 to y1)
        override fun withHandle(index: Int, x: Float, y: Float) = if (index == 0) copy(x0 = x, y0 = y) else copy(x1 = x, y1 = y)
        override fun transformed(ox: Float, oy: Float, c: Float, s: Float) =
            map(ox, oy, c, s, x0, y0).let { (a, b) -> map(ox, oy, c, s, x1, y1).let { (d, e) -> Line(a, b, d, e) } }
    }

    /** A quadratic curve; its middle handle sits on the curve at t = 0.5. */
    data class Quadratic(val x0: Float, val y0: Float, val cx: Float, val cy: Float, val x1: Float, val y1: Float) : HeldShape {
        override val kind get() = StrokeRegularizer.Kind.CURVE
        override fun point(t: Float): Pair<Float, Float> { val one = 1f - t
            return one * one * x0 + 2f * one * t * cx + t * t * x1 to one * one * y0 + 2f * one * t * cy + t * t * y1 }
        override fun handles() = listOf(x0 to y0, point(.5f), x1 to y1)
        override fun withHandle(index: Int, x: Float, y: Float) = when (index) {
            0 -> copy(x0 = x, y0 = y)
            1 -> copy(cx = 2f * x - (x0 + x1) / 2f, cy = 2f * y - (y0 + y1) / 2f)
            else -> copy(x1 = x, y1 = y)
        }
        override fun transformed(ox: Float, oy: Float, c: Float, s: Float): Quadratic {
            val a = map(ox, oy, c, s, x0, y0); val b = map(ox, oy, c, s, cx, cy); val d = map(ox, oy, c, s, x1, y1)
            return Quadratic(a.first, a.second, b.first, b.second, d.first, d.second)
        }
    }

    /** A circular arc; handles are its start, middle and end, and it always passes through all three. */
    data class Arc(val cx: Float, val cy: Float, val radius: Float, val start: Float, val sweep: Float) : HeldShape {
        override val kind get() = StrokeRegularizer.Kind.ARC
        override fun point(t: Float) = cx + radius * cos(start + sweep * t) to cy + radius * sin(start + sweep * t)
        override fun handles() = listOf(point(0f), point(.5f), point(1f))
        override fun withHandle(index: Int, x: Float, y: Float): HeldShape {
            val p = handles().toMutableList().apply { this[index] = x to y }
            return through(p[0], p[1], p[2]) ?: this
        }
        override fun transformed(ox: Float, oy: Float, c: Float, s: Float): HeldShape =
            handles().map { map(ox, oy, c, s, it.first, it.second) }.let { p -> through(p[0], p[1], p[2]) ?: this }

        companion object {
            /** The arc from [a] through [m] to [b]; null when the three points are in a line. */
            fun through(a: Pair<Float, Float>, m: Pair<Float, Float>, b: Pair<Float, Float>): Arc? {
                val d = 2f * (a.first * (m.second - b.second) + m.first * (b.second - a.second) + b.first * (a.second - m.second))
                if (abs(d) < 1e-3f) return null
                fun sq(p: Pair<Float, Float>) = p.first * p.first + p.second * p.second
                val cx = (sq(a) * (m.second - b.second) + sq(m) * (b.second - a.second) + sq(b) * (a.second - m.second)) / d
                val cy = (sq(a) * (b.first - m.first) + sq(m) * (a.first - b.first) + sq(b) * (m.first - a.first)) / d
                val r = hypot(a.first - cx, a.second - cy)
                val start = atan2(a.second - cy, a.first - cx)
                fun ccw(angle: Float) = ((angle - start) % (2 * PI).toFloat() + (2 * PI).toFloat()) % (2 * PI).toFloat()
                val toMiddle = ccw(atan2(m.second - cy, m.first - cx)); val toEnd = ccw(atan2(b.second - cy, b.first - cx))
                // Go the way round that passes the middle point.
                val sweep = if (toMiddle <= toEnd) toEnd else toEnd - (2 * PI).toFloat()
                return Arc(cx, cy, r, start, sweep)
            }
        }
    }

    /** A circle or ellipse, drawn once round from [start] in [direction]; handles: centre and the two radii. */
    data class Ellipse(val cx: Float, val cy: Float, val a: Float, val b: Float, val rotation: Float, val start: Float, val direction: Float,
                       val circle: Boolean) : HeldShape {
        override val kind get() = if (circle) StrokeRegularizer.Kind.CIRCLE else StrokeRegularizer.Kind.ELLIPSE
        override val closed get() = true
        override fun point(t: Float): Pair<Float, Float> {
            val angle = start + direction * 2f * PI.toFloat() * t
            val u = a * cos(angle); val v = b * sin(angle)
            return cx + u * cos(rotation) - v * sin(rotation) to cy + u * sin(rotation) + v * cos(rotation)
        }
        override fun handles(): List<Pair<Float, Float>> = listOf(cx to cy,
            cx + a * cos(rotation) to cy + a * sin(rotation)) + if (circle) emptyList() else listOf(cx - b * sin(rotation) to cy + b * cos(rotation))
        override fun withHandle(index: Int, x: Float, y: Float): HeldShape = when (index) {
            0 -> copy(cx = x, cy = y)
            // The first radius handle also turns the shape; a circle's handle changes its radius.
            1 -> hypot(x - cx, y - cy).coerceAtLeast(2f).let { r -> if (circle) copy(a = r, b = r) else copy(a = r, rotation = atan2(y - cy, x - cx)) }
            else -> copy(b = abs(-(x - cx) * sin(rotation) + (y - cy) * cos(rotation)).coerceAtLeast(2f))
        }
        override fun transformed(ox: Float, oy: Float, c: Float, s: Float): HeldShape {
            val (x, y) = map(ox, oy, c, s, cx, cy); val scale = hypot(c, s)
            return copy(cx = x, cy = y, a = a * scale, b = b * scale, rotation = rotation + atan2(s, c))
        }
    }

    private companion object {
        fun map(ox: Float, oy: Float, c: Float, s: Float, x: Float, y: Float): Pair<Float, Float> {
            val dx = x - ox; val dy = y - oy
            return ox + c * dx - s * dy to oy + s * dx + c * dy
        }
    }
}
