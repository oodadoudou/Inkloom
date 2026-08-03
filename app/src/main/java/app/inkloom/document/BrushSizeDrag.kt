package app.inkloom.document

import kotlin.math.pow

object BrushSizeDrag {
    fun resolve(
        startSize: Float,
        horizontalDeltaPixels: Float,
        pixelsPerDoubling: Float = 180f,
        minimumSize: Float = .5f,
        maximumSize: Float = 400f,
    ): Float {
        val multiplier = 2.0.pow((horizontalDeltaPixels / pixelsPerDoubling.coerceAtLeast(1f)).toDouble()).toFloat()
        return (startSize * multiplier).coerceIn(minimumSize, maximumSize)
    }
}
