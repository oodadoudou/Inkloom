package app.inkloom.engine.v3.android

import android.graphics.Bitmap
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
import kotlin.math.floor

/** Android interop at the edge of the platform-independent sparse pixel engine. */
internal object PixelTileBitmapAdapter {
    fun import(bitmap: Bitmap, tileSize: Int = PixelTileSurface.DEFAULT_TILE_SIZE): PixelTileSurface {
        require(!bitmap.isRecycled) { "Bitmap has been recycled" }
        val surface = PixelTileSurface(bitmap.width, bitmap.height, tileSize)
        surface.beginWrite("legacy raster import").use { write ->
            val source = IntArray(tileSize * tileSize)
            val columns = (bitmap.width + tileSize - 1) / tileSize
            val rows = (bitmap.height + tileSize - 1) / tileSize
            for (row in 0 until rows) for (column in 0 until columns) {
                val left = column * tileSize
                val top = row * tileSize
                val width = minOf(tileSize, bitmap.width - left)
                val height = minOf(tileSize, bitmap.height - top)
                source.fill(0)
                bitmap.getPixels(source, 0, tileSize, left, top, width, height)
                var hasContent = false
                for (y in 0 until height) for (x in 0 until width) {
                    val index = y * tileSize + x
                    source[index] = premultiply(source[index]).also { color ->
                        if (color ushr 24 != 0) hasContent = true
                    }
                }
                if (hasContent) {
                    write.editTile(
                        PixelTileKey(column, row),
                        PixelRect(0, 0, width, height),
                    ) { pixels, _ -> source.copyInto(pixels) }
                }
            }
            write.commit().close()
        }
        return surface
    }

    /** Materializes only [source] into the requested output size; the logical full page is not allocated. */
    fun render(
        snapshot: PixelTileSurfaceSnapshot,
        source: PixelRect = PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight),
        targetWidth: Int = source.width,
        targetHeight: Int = source.height,
    ): Bitmap {
        val clipped = source.intersect(PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
        require(!clipped.isEmpty) { "Source rectangle does not intersect the surface" }
        require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }
        val output = IntArray(Math.multiplyExact(targetWidth, targetHeight))
        val scaleX = clipped.width.toFloat() / targetWidth
        val scaleY = clipped.height.toFloat() / targetHeight
        for (targetY in 0 until targetHeight) {
            val sourceY = (clipped.top + floor((targetY + .5f) * scaleY).toInt())
                .coerceIn(clipped.top, clipped.bottom - 1)
            for (targetX in 0 until targetWidth) {
                val sourceX = (clipped.left + floor((targetX + .5f) * scaleX).toInt())
                    .coerceIn(clipped.left, clipped.right - 1)
                output[targetY * targetWidth + targetX] = unpremultiply(snapshot.pixelAt(sourceX, sourceY))
            }
        }
        return Bitmap.createBitmap(output, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    private fun premultiply(color: Int): Int {
        val alpha = color ushr 24
        if (alpha == 0 || alpha == 255) return color
        val red = ((color ushr 16 and 0xff) * alpha + 127) / 255
        val green = ((color ushr 8 and 0xff) * alpha + 127) / 255
        val blue = ((color and 0xff) * alpha + 127) / 255
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun unpremultiply(color: Int): Int {
        val alpha = color ushr 24
        if (alpha == 0 || alpha == 255) return color
        val red = ((color ushr 16 and 0xff) * 255 + alpha / 2) / alpha
        val green = ((color ushr 8 and 0xff) * 255 + alpha / 2) / alpha
        val blue = ((color and 0xff) * 255 + alpha / 2) / alpha
        return (alpha shl 24) or (red.coerceAtMost(255) shl 16) or
            (green.coerceAtMost(255) shl 8) or blue.coerceAtMost(255)
    }
}
