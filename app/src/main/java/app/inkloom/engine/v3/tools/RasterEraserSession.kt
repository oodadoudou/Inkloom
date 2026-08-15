package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

/** Bounded native-Skia preview for a raster brush eraser.
 *
 * The input thread records one capsule into a reusable alpha surface and composites only that
 * dirty rectangle with DST_OUT. There is no Bitmap.getPixels/setPixels or Kotlin per-pixel loop.
 * Imported brush-tip replay remains the authoritative pen-up pass until the V3 brush pipeline
 * writes directly into sparse layer tiles.
 */
class RasterEraserSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val originalTiles = BitmapTileMemento(bitmap)
    private val targetCanvas = Canvas(bitmap)
    private val kernel = RasterEraserKernel(bitmap.width, bitmap.height, selectionMask)
    private var closed = false

    val retainedBackupByteSize: Long get() = originalTiles.retainedByteSize

    fun eraseSegment(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        radiusPixels: Float,
        opacity: Float,
        hardness: Float,
    ): Rect? {
        if (closed || bitmap.isRecycled) return null
        val bounds = kernel.boundsFor(startX, startY, endX, endY, radiusPixels)
        if (bounds.isEmpty) return null
        originalTiles.capture(bounds)
        return bounds.takeIf {
            kernel.erase(targetCanvas, bounds, bounds, startX, startY, endX, endY, radiusPixels, opacity, hardness)
        }
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled) return
        originalTiles.restore(targetCanvas)
    }

    override fun close() {
        if (closed) return
        closed = true
        originalTiles.close()
        kernel.close()
    }
}
