package app.inkloom.document

import kotlin.math.hypot

object StrokeTaper {
    fun factors(
        points: List<StrokePoint>,
        pageWidth: Float,
        pageHeight: Float,
        mode: BrushTaperMode,
        startEnabled: Boolean,
        startLength: Float,
        startLengthUnit: Int,
        startMinimum: Float,
        endEnabled: Boolean,
        endLength: Float,
        endLengthUnit: Int,
        endMinimum: Float,
    ): List<Float> {
        if (points.isEmpty()) return emptyList()
        if (!startEnabled && !endEnabled) return List(points.size) { 1f }
        val distances = MutableList(points.size) { 0f }
        for (index in 1 until points.size) {
            val dx = (points[index].x - points[index - 1].x) * pageWidth.coerceAtLeast(1f)
            val dy = (points[index].y - points[index - 1].y) * pageHeight.coerceAtLeast(1f)
            distances[index] = distances[index - 1] + hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
        val total = distances.last()
        if (total <= .001f) return List(points.size) { 1f }
        val startSpan = spanDistance(mode, startLength, startLengthUnit, total, pageWidth)
        val endSpan = spanDistance(mode, endLength, endLengthUnit, total, pageWidth)
        val startFloor = startMinimum.coerceIn(0f, 1f)
        val endFloor = endMinimum.coerceIn(0f, 1f)
        return distances.map { distance ->
            if (mode == BrushTaperMode.FADE) {
                if (endEnabled) lerp(1f, endFloor, distance / endSpan) else 1f
            } else {
                val startFactor = if (startEnabled && distance < startSpan) lerp(startFloor, 1f, distance / startSpan) else 1f
                val remaining = total - distance
                val endFactor = if (endEnabled && remaining < endSpan) lerp(endFloor, 1f, remaining / endSpan) else 1f
                minOf(startFactor, endFactor).coerceIn(0f, 1f)
            }
        }
    }

    private fun spanDistance(mode: BrushTaperMode, value: Float, unit: Int, total: Float, pageWidth: Float): Float = when (mode) {
        BrushTaperMode.PERCENTAGE, BrushTaperMode.FADE -> total * value.coerceIn(.001f, 1f)
        BrushTaperMode.DISTANCE -> {
            val referencePixels = if (unit == 2) value * PX_PER_MM else value
            referencePixels.coerceAtLeast(.1f) * pageWidth.coerceAtLeast(1f) / REFERENCE_PAGE_WIDTH
        }
    }.coerceAtLeast(.001f)

    private fun lerp(start: Float, end: Float, amount: Float) = start + (end - start) * amount.coerceIn(0f, 1f)

    private const val PX_PER_MM = 3.7795275f
    private const val REFERENCE_PAGE_WIDTH = 1600f
}
