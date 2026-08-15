package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Rect

/** Read-only selection alpha sampled directly into a tool's dirty raster region. */
internal class RasterSelectionMask(
    source: Bitmap?,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
) {
    private val bitmap = source?.takeUnless(Bitmap::isRecycled)

    fun apply(target: Bitmap, globalBounds: Rect): Boolean {
        val mask = bitmap?.takeUnless(Bitmap::isRecycled) ?: return true
        if (target.isRecycled || globalBounds.isEmpty) return false
        return NativeRasterOps.applySelectionAlpha(
            target,
            mask,
            globalBounds.left,
            globalBounds.top,
            globalBounds.width().coerceAtMost(target.width),
            globalBounds.height().coerceAtMost(target.height),
            surfaceWidth,
            surfaceHeight,
        )
    }
}
