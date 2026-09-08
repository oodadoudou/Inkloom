package app.inkloom.engine.v5.runtime

import kotlin.math.*

/** Conservative recognition: ambiguous handwriting remains the original stroke. */
object StrokeRegularizer {
    enum class Kind { LINE, CURVE }
    data class Result(val kind: Kind, val samples: List<RawDocumentSample>)

    fun fit(input: List<RawDocumentSample>): Result? {
        if (input.size < 4) return null
        val samples = input.filterIndexed { index, sample -> index == 0 ||
            hypot(sample.x - input[index - 1].x, sample.y - input[index - 1].y) > .05f }
        if (samples.size < 4) return null
        val lengths = FloatArray(samples.size)
        for (i in 1 until samples.size) lengths[i] = lengths[i - 1] + hypot(samples[i].x - samples[i - 1].x, samples[i].y - samples[i - 1].y)
        val length = lengths.last()
        val first = samples.first(); val last = samples.last()
        val dx = last.x - first.x; val dy = last.y - first.y
        val chord = hypot(dx, dy)
        if (length < 24f || chord < length * .35f) return null
        val times = lengths.map { it / length }
        val lineError = samples.maxOf { abs((it.x - first.x) * dy - (it.y - first.y) * dx) / chord }
        var controlX = (first.x + last.x) / 2f
        var controlY = (first.y + last.y) / 2f
        val kind = if (lineError <= max(2f, chord * .035f)) Kind.LINE else {
            var denominator = 0f; var numeratorX = 0f; var numeratorY = 0f
            samples.forEachIndexed { i, sample ->
                val t = times[i]; val one = 1f - t; val coefficient = 2f * one * t
                denominator += coefficient * coefficient
                numeratorX += coefficient * (sample.x - one * one * first.x - t * t * last.x)
                numeratorY += coefficient * (sample.y - one * one * first.y - t * t * last.y)
            }
            if (denominator < .001f) return null
            controlX = numeratorX / denominator; controlY = numeratorY / denominator
            val error = samples.indices.maxOf { i ->
                val t = times[i]; val one = 1f - t
                hypot(samples[i].x - (one * one * first.x + 2f * one * t * controlX + t * t * last.x),
                    samples[i].y - (one * one * first.y + 2f * one * t * controlY + t * t * last.y))
            }
            if (error > max(3f, length * .045f)) return null
            Kind.CURVE
        }
        val count = ceil(length / 2f).toInt().coerceIn(32, 512)
        var sourceIndex = 0
        val regular = List(count) { i ->
            val t = i.toFloat() / (count - 1); val one = 1f - t
            while (sourceIndex < times.lastIndex && times[sourceIndex + 1] <= t) sourceIndex++
            val source = samples[sourceIndex]
            source.copy(
                x = one * one * first.x + 2f * one * t * controlX + t * t * last.x,
                y = one * one * first.y + 2f * one * t * controlY + t * t * last.y,
                eventTimeNanos = first.eventTimeNanos + ((last.eventTimeNanos - first.eventTimeNanos) * t).toLong(),
                enqueuedTimeNanos = first.enqueuedTimeNanos + ((last.enqueuedTimeNanos - first.enqueuedTimeNanos) * t).toLong(),
            )
        }
        return Result(kind, regular)
    }

    /** Continue holding to scale/rotate around the first point without refitting the curve. */
    fun moveEndpoint(samples: List<RawDocumentSample>, x: Float, y: Float, snapAngle: Boolean = false): List<RawDocumentSample> {
        if (samples.size < 2) return samples
        val origin = samples.first(); val end = samples.last()
        val originalLength = hypot(end.x - origin.x, end.y - origin.y).coerceAtLeast(.001f)
        val scale = hypot(x - origin.x, y - origin.y).coerceAtLeast(1f) / originalLength
        var angle = atan2(y - origin.y, x - origin.x)
        if (snapAngle) angle = (round(angle / (Math.PI.toFloat() / 12f)) * Math.PI.toFloat() / 12f)
        val rotation = angle - atan2(end.y - origin.y, end.x - origin.x)
        val c = cos(rotation) * scale; val s = sin(rotation) * scale
        return samples.map { sample ->
            val dx = sample.x - origin.x; val dy = sample.y - origin.y
            sample.copy(x = origin.x + c * dx - s * dy, y = origin.y + s * dx + c * dy)
        }
    }
}
