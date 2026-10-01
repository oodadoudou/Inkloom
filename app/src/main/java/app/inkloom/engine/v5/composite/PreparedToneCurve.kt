package app.inkloom.engine.v5.composite

import app.inkloom.document.AdjustmentCurvePoint
import kotlin.math.sqrt

/** Shape-preserving cubic interpolation, prepared once for a tile projection. */
internal class PreparedToneCurve(raw: List<AdjustmentCurvePoint>, private val smooth: Boolean = false) {
    val points = normalize(raw)
    private val slopes = FloatArray(points.size - 1) { i ->
        (points[i + 1].output - points[i].output) / (points[i + 1].input - points[i].input)
    }
    private val tangents = FloatArray(points.size).also { tangent ->
        tangent[0] = slopes.first(); tangent[tangent.lastIndex] = slopes.last()
        for (i in 1 until tangent.lastIndex) tangent[i] =
            if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        for (i in slopes.indices) {
            if (slopes[i] == 0f) { tangent[i] = 0f; tangent[i + 1] = 0f; continue }
            val a = tangent[i] / slopes[i]; val b = tangent[i + 1] / slopes[i]
            val magnitude = a * a + b * b
            if (magnitude > 9f) {
                val scale = 3f / sqrt(magnitude)
                tangent[i] = scale * a * slopes[i]; tangent[i + 1] = scale * b * slopes[i]
            }
        }
    }

    fun sample(input: Float): Float {
        val x = input.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        if (x <= points.first().input) return points.first().output
        if (x >= points.last().input) return points.last().output
        var i = 0
        var right = points.lastIndex
        while (right - i > 1) {
            val middle = (i + right) / 2
            if (points[middle].input < x) i = middle else right = middle
        }
        val from = points[i]; val to = points[right]
        val span = to.input - from.input; val t = (x - from.input) / span
        if (!smooth) return from.output + (to.output - from.output) * t
        val t2 = t * t; val t3 = t2 * t
        return ((2 * t3 - 3 * t2 + 1) * from.output + (t3 - 2 * t2 + t) * span * tangents[i] +
            (-2 * t3 + 3 * t2) * to.output + (t3 - t2) * span * tangents[right]).coerceIn(0f, 1f)
    }

    companion object {
        fun normalize(raw: List<AdjustmentCurvePoint>): List<AdjustmentCurvePoint> {
            val values = raw.filter { it.input.isFinite() && it.output.isFinite() }
                .map { AdjustmentCurvePoint(it.input.coerceIn(0f, 1f), it.output.coerceIn(0f, 1f)) }
                .sortedBy { it.input }.distinctBy { it.input }
            if (values.isEmpty()) return listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f))
            return buildList {
                add(AdjustmentCurvePoint(0f, values.first().output))
                values.filter { it.input in .0001f.. .9999f }.forEach { point ->
                    if (point.input - last().input >= .0001f) add(point)
                }
                add(AdjustmentCurvePoint(1f, values.last().output))
            }
        }
    }
}
