package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot

/**
 * Immutable ownership-transfer object for one edited rectangle of a visible raster layer.
 *
 * Coordinates are expressed in the source bitmap's presentation space. The receiver owns
 * [bitmap] and must call [close] after the patch has been persisted or discarded.
 */
class RasterLayerPatch private constructor(
    val layerId: String,
    val bitmap: Bitmap,
    val left: Int,
    val top: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
) : AutoCloseable {
    val right: Int get() = left + bitmap.width
    val bottom: Int get() = top + bitmap.height
    val bounds: Rect get() = Rect(left, top, right, bottom)

    init {
        require(layerId.isNotBlank()) { "Layer id must not be blank" }
        require(!bitmap.isRecycled) { "Patch bitmap has been recycled" }
        require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
        require(left >= 0 && top >= 0 && right <= sourceWidth && bottom <= sourceHeight) {
            "Patch bounds must fit inside the source surface"
        }
    }

    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        /** Captures only [dirtyBounds], clipped to [source], instead of copying the full layer. */
        fun capture(layerId: String, source: Bitmap, dirtyBounds: Rect): RasterLayerPatch? {
            require(!source.isRecycled) { "Source raster surface has been recycled" }
            val clipped = Rect(dirtyBounds)
            if (!clipped.intersect(0, 0, source.width, source.height) || clipped.isEmpty) return null
            val captured = Bitmap.createBitmap(clipped.width(), clipped.height(), Bitmap.Config.ARGB_8888)
            Canvas(captured).drawBitmap(
                source,
                clipped,
                Rect(0, 0, captured.width, captured.height),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            return RasterLayerPatch(
                layerId = layerId,
                bitmap = captured,
                left = clipped.left,
                top = clipped.top,
                sourceWidth = source.width,
                sourceHeight = source.height,
            )
        }

        /** Captures a dirty region directly from authoritative tiles without a full bitmap mirror. */
        fun capture(layerId: String, source: PixelTileSurfaceSnapshot, dirtyBounds: Rect): RasterLayerPatch? {
            val clipped = Rect(dirtyBounds)
            if (!clipped.intersect(0, 0, source.logicalWidth, source.logicalHeight) || clipped.isEmpty) return null
            val captured = PixelTileBitmapAdapter.render(
                source,
                PixelRect(clipped.left, clipped.top, clipped.right, clipped.bottom),
            )
            return RasterLayerPatch(
                layerId = layerId,
                bitmap = captured,
                left = clipped.left,
                top = clipped.top,
                sourceWidth = source.logicalWidth,
                sourceHeight = source.logicalHeight,
            )
        }
    }
}
