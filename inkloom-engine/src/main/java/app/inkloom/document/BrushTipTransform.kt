package app.inkloom.document

import kotlin.math.max

data class BrushTipScale(val x: Float, val y: Float)

object BrushTipTransform {
    fun scale(width: Int, height: Int, diameter: Float, thickness: Float, thicknessVertical: Boolean, horizontalFactor: Float = 1f, verticalFactor: Float = 1f): BrushTipScale {
        val base = diameter.coerceAtLeast(1f) / max(width, height).coerceAtLeast(1)
        val shaped = thickness.coerceIn(.05f, 2f)
        val horizontal = horizontalFactor.coerceIn(.01f, 1f)
        val vertical = verticalFactor.coerceIn(.01f, 1f)
        return if (thicknessVertical) BrushTipScale(base * horizontal, base * shaped * vertical) else BrushTipScale(base * shaped * horizontal, base * vertical)
    }
}
