package app.inkloom.document

import android.graphics.Bitmap
import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.settings.FillExpansionShape

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
        targetColor: Int? = null,
        expansionShape: FillExpansionShape = FillExpansionShape.ROUND,
        antiAlias: Boolean = true,
    ): Result {
        if (source.isRecycled || startX !in 0 until source.width || startY !in 0 until source.height) {
            return empty()
        }
        val fullMask = scratchMask(source.width, source.height)
        val bounds = NativeRasterOps.floodFill(
            source = source,
            destination = fullMask,
            startX = startX,
            startY = startY,
            tolerance = tolerance.coerceIn(0, 255),
            closeGapPixels = closeGapPixels.coerceIn(0, MAX_EFFECT_RADIUS),
            areaScalingPixels = areaScalingPixels.coerceIn(-MAX_EFFECT_RADIUS, MAX_EFFECT_RADIUS),
            targetColor = targetColor,
            expansionShape = when (expansionShape) {
                FillExpansionShape.ROUND -> 0
                FillExpansionShape.RECTANGLE -> 1
            },
            antiAlias = antiAlias,
        )
        if (bounds == null || bounds.isEmpty) {
            return empty()
        }
        // Threshold dragging recomputes this mask many times per second. Retain the full-size JNI
        // destination per worker thread and copy only the bounded result owned by the caller.
        // Bitmap's native subset copy avoids routing a pure pixel transfer through Canvas.
        val subset = Bitmap.createBitmap(fullMask, bounds.left, bounds.top, bounds.width(), bounds.height())
        val cropped = if (subset === fullMask) fullMask.copy(Bitmap.Config.ARGB_8888, true) else subset
        return Result(cropped, bounds.left, bounds.top, bounds.right - 1, bounds.bottom - 1)
    }

    private fun scratchMask(width: Int, height: Int): Bitmap {
        val current = scratchMasks.get()
        if (current != null && !current.isRecycled && current.width == width && current.height == height) return current
        current?.takeUnless(Bitmap::isRecycled)?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also(scratchMasks::set)
    }

    private fun empty() = Result(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0, 0, -1, -1)

    private val scratchMasks = ThreadLocal<Bitmap>()
    private const val MAX_EFFECT_RADIUS = 64
}
