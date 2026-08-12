package app.inkloom.document

import kotlin.math.hypot
import kotlin.math.pow

object StrokeTaper {
    data class DabFactors(val size: List<Float>, val opacity: List<Float>?)

    fun evaluate(stroke: InkStroke, points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): DabFactors {
        val procreate = procreateFactors(stroke, points, pageWidth, pageHeight)
        return if (procreate != null) DabFactors(procreate.first, procreate.second) else DabFactors(
            factors(
                points, pageWidth, pageHeight, stroke.taperMode,
                stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum,
                stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum,
            ),
            null,
        )
    }

    fun sizeFactors(stroke: InkStroke, points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): List<Float> =
        evaluate(stroke, points, pageWidth, pageHeight).size

    fun opacityFactors(stroke: InkStroke, points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): List<Float>? =
        procreateFactors(stroke, points, pageWidth, pageHeight)?.second

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

    private fun procreateFactors(
        stroke: InkStroke,
        points: List<StrokePoint>,
        pageWidth: Float,
        pageHeight: Float,
    ): Pair<List<Float>, List<Float>>? {
        val taper = stroke.procreateTaper
        if (taper.isDisabled() || points.isEmpty()) return null
        val stylus = stroke.inputKind == StrokeInputKind.STYLUS
        val startLength = if (stylus) taper.pressureStartLength else taper.touchStartLength
        val endLength = if (stylus) taper.pressureEndLength else taper.touchEndLength
        val size = if (stylus) taper.pressureSize else taper.touchSize
        val opacity = if (stylus) taper.pressureOpacity else taper.touchOpacity
        val tip = if (stylus) taper.pressureTip else taper.touchTip
        if ((startLength <= .0001f && endLength <= .0001f) || (size <= .0001f && opacity <= .0001f)) return null
        val distances = FloatArray(points.size)
        for (index in 1 until points.size) {
            val dx = (points[index].x - points[index - 1].x) * pageWidth.coerceAtLeast(1f)
            val dy = (points[index].y - points[index - 1].y) * pageHeight.coerceAtLeast(1f)
            distances[index] = distances[index - 1] + hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
        val total = distances.last()
        if (total <= .001f) return List(points.size) { 1f } to List(points.size) { 1f }
        val startSpan = (total * startLength.coerceIn(0f, 1f)).coerceAtLeast(.001f)
        val endSpan = (total * endLength.coerceIn(0f, 1f)).coerceAtLeast(.001f)
        val sizeFloor = 1f - size.coerceIn(0f, 1f)
        val opacityFloor = 1f - opacity.coerceIn(0f, 1f)
        val sizeFactors = ArrayList<Float>(points.size)
        val opacityFactors = ArrayList<Float>(points.size)
        for (index in points.indices) {
            val distance = distances[index]
            val startProgress = if (startLength > .0001f && distance < startSpan) distance / startSpan else 1f
            val remaining = total - distance
            val endProgress = if (endLength > .0001f && remaining < endSpan) remaining / endSpan else 1f
            var progress = minOf(startProgress, endProgress).coerceIn(0f, 1f)
            if (!taper.classic) progress = progress * progress * (3f - 2f * progress)
            val tipExponent = lerp(2.4f, .35f, tip.coerceIn(0f, 1f))
            progress = progress.pow(tipExponent)
            if (stylus && taper.pressureResponse > .0001f && progress < 1f) {
                val pressure = points[index].pressure.coerceIn(0f, 1f)
                progress *= lerp(1f, .2f + .8f * pressure, taper.pressureResponse)
            }
            sizeFactors += lerp(sizeFloor, 1f, progress)
            opacityFactors += lerp(opacityFloor, 1f, progress)
        }
        return sizeFactors to opacityFactors
    }

    private const val PX_PER_MM = 3.7795275f
    private const val REFERENCE_PAGE_WIDTH = 1600f
}
