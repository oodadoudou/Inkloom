package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot

/** Read-only selection alpha sampled directly into a tool's dirty raster region. */
internal class RasterSelectionMask(
    source: Bitmap?,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
) {
    private val bitmap = source?.takeUnless(Bitmap::isRecycled)
    private var sparse: MaskTileSurfaceSnapshot? = null

    constructor(source: MaskTileSurfaceSnapshot?, surfaceWidth: Int, surfaceHeight: Int) :
        this(null as Bitmap?, surfaceWidth, surfaceHeight) {
        sparse = source?.takeUnless(MaskTileSurfaceSnapshot::isEmpty)
    }

    fun apply(target: Bitmap, globalBounds: Rect): Boolean {
        if (target.isRecycled || globalBounds.isEmpty) return false
        val sparseMask = sparse
        if (sparseMask != null) return applySparse(target, globalBounds, sparseMask)
        val mask = bitmap?.takeUnless(Bitmap::isRecycled) ?: return true
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

    /** Builds only the tool's dirty ROI; the document selection remains sparse and immutable. */
    private fun applySparse(target: Bitmap, globalBounds: Rect, source: MaskTileSurfaceSnapshot): Boolean {
        val width = globalBounds.width().coerceAtMost(target.width)
        val height = globalBounds.height().coerceAtMost(target.height)
        if (width <= 0 || height <= 0) return false
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sourceY = (((globalBounds.top + y + .5f) * source.logicalHeight / surfaceHeight) - .5f)
                .toInt().coerceIn(0, source.logicalHeight - 1)
            for (x in 0 until width) {
                val sourceX = (((globalBounds.left + x + .5f) * source.logicalWidth / surfaceWidth) - .5f)
                    .toInt().coerceIn(0, source.logicalWidth - 1)
                val alpha = source.alphaAt(sourceX, sourceY)
                pixels[y * width + x] = (alpha shl 24) or (Color.WHITE and 0x00ffffff)
            }
        }
        val localMask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            localMask.setPixels(pixels, 0, width, 0, 0, width, height)
            NativeRasterOps.applySelectionAlpha(target, localMask, 0, 0, width, height, width, height)
        } finally {
            localMask.recycle()
        }
    }
}
