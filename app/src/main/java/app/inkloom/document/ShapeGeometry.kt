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

    private fun angleDelta(first: Float, second: Float): Float = ((first - second + 180f).mod(360f)) - 180f
}
