package app.inkloom.document

import android.graphics.Bitmap
import app.inkloom.engine.v3.tools.NativeRasterOps

/** Native scanline flood fill used by the paint-bucket, ColorDrop and automatic-selection tools.
 * [tolerance] is the maximum per-channel difference (0..255) from the touched source pixel. */
object FloodFill {
    data class Result(val mask: Bitmap, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val isEmpty: Boolean get() = width <= 0 || height <= 0
    }

    fun fill(
        source: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int = 0,
        areaScalingPixels: Int = 0,
    ): Result {
        if (source.isRecycled || startX !in 0 until source.width || startY !in 0 until source.height) {
            return empty()
        }
        val fullMask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val bounds = NativeRasterOps.floodFill(
            source = source,
            destination = fullMask,
            startX = startX,
            startY = startY,
            tolerance = tolerance.coerceIn(0, 255),
            closeGapPixels = closeGapPixels.coerceIn(0, MAX_EFFECT_RADIUS),
            areaScalingPixels = areaScalingPixels.coerceIn(-MAX_EFFECT_RADIUS, MAX_EFFECT_RADIUS),
        )
        if (bounds == null || bounds.isEmpty) {
            fullMask.recycle()
            return empty()
        }
        val cropped = Bitmap.createBitmap(fullMask, bounds.left, bounds.top, bounds.width(), bounds.height())
        fullMask.recycle()
        return Result(cropped, bounds.left, bounds.top, bounds.right - 1, bounds.bottom - 1)
    }

    private fun empty() = Result(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0, 0, -1, -1)

    private const val MAX_EFFECT_RADIUS = 64
}
