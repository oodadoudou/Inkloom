package app.inkloom.document

import kotlin.math.max

data class BrushTipScale(val x: Float, val y: Float)

object BrushTipTransform {
    fun scale(width: Int, height: Int, diameter: Float, thickness: Float, thicknessVertical: Boolean): BrushTipScale {
        val base = diameter.coerceAtLeast(1f) / max(width, height).coerceAtLeast(1)
        val shaped = thickness.coerceIn(.05f, 2f)
        return if (thicknessVertical) BrushTipScale(base, base * shaped) else BrushTipScale(base * shaped, base)
    }
}
