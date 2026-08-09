package app.inkloom.document

import kotlin.math.hypot

/** Pixel-aware contour modification used by CSP-style expand, shrink and border selection. */
object SelectionContourGeometry {
    fun offset(
        contour: List<StrokePoint>,
        pixels: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): List<StrokePoint> {
        if (contour.size < 3 || pixels == 0f) return contour.toList()
        val centerX = contour.map(StrokePoint::x).average().toFloat()
        val centerY = contour.map(StrokePoint::y).average().toFloat()
        val width = pageWidth.coerceAtLeast(1f)
        val height = pageHeight.coerceAtLeast(1f)
        return contour.map { point ->
            val dx = (point.x - centerX) * width
            val dy = (point.y - centerY) * height
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (distance <= .001f) point else {
                val targetDistance = (distance + pixels).coerceAtLeast(0f)
                val scale = targetDistance / distance
                point.copy(
                    x = (centerX + dx * scale / width).coerceIn(0f, 1f),
                    y = (centerY + dy * scale / height).coerceIn(0f, 1f),
                )
            }
        }
    }
}
