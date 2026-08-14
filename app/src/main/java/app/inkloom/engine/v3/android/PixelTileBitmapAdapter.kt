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
        val output = if (clipped.width == targetWidth && clipped.height == targetHeight) {
            snapshot.copyPremultipliedPixels(clipped)
        } else {
            IntArray(Math.multiplyExact(targetWidth, targetHeight)).also { scaled ->
                val scaleX = clipped.width.toFloat() / targetWidth
                val scaleY = clipped.height.toFloat() / targetHeight
                for (targetY in 0 until targetHeight) {
                    val sourceY = (clipped.top + floor((targetY + .5f) * scaleY).toInt())
                        .coerceIn(clipped.top, clipped.bottom - 1)
                    for (targetX in 0 until targetWidth) {
                        val sourceX = (clipped.left + floor((targetX + .5f) * scaleX).toInt())
                            .coerceIn(clipped.left, clipped.right - 1)
                        scaled[targetY * targetWidth + targetX] = snapshot.pixelAt(sourceX, sourceY)
                    }
                }
            }
        }
        var outputIndex = 0
        while (outputIndex < output.size) {
            output[outputIndex] = unpremultiply(output[outputIndex])
            outputIndex++
        }
        return Bitmap.createBitmap(output, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    /**
     * Replaces a rectangle expressed in the rotated presentation space while keeping the surface
     * pixels in document orientation. Transparent input clears existing pixels in the region.
     */
    fun replaceRotatedRegion(
        surface: PixelTileSurface,
        rotatedRegion: PixelRect,
        rotationQuarterTurns: Int,
        bitmap: Bitmap,
    ) {
        require(!bitmap.isRecycled) { "Bitmap has been recycled" }
        val turns = rotationQuarterTurns.mod(4)
        val rotatedWidth = if (turns % 2 == 0) surface.logicalWidth else surface.logicalHeight
        val rotatedHeight = if (turns % 2 == 0) surface.logicalHeight else surface.logicalWidth
        val region = rotatedRegion.intersect(PixelRect(0, 0, rotatedWidth, rotatedHeight))
        require(!region.isEmpty) { "Replacement region does not intersect the surface" }
        val sourceBounds = when (turns) {
            0 -> region
            1 -> PixelRect(
                region.top,
                surface.logicalHeight - region.right,
                region.bottom,
                surface.logicalHeight - region.left,
            )
            2 -> PixelRect(
                surface.logicalWidth - region.right,
                surface.logicalHeight - region.bottom,
                surface.logicalWidth - region.left,
                surface.logicalHeight - region.top,
            )
            else -> PixelRect(
                surface.logicalWidth - region.bottom,
                region.left,
                surface.logicalWidth - region.top,
                region.right,
            )
        }
        val sourcePixels = IntArray(Math.multiplyExact(bitmap.width, bitmap.height))
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixelIndex = 0
        while (pixelIndex < sourcePixels.size) {
            sourcePixels[pixelIndex] = premultiply(sourcePixels[pixelIndex])
            pixelIndex++
        }
        val exactUnrotatedCopy = turns == 0 && region.width == bitmap.width && region.height == bitmap.height
        val bitmapXByRotatedX = if (exactUnrotatedCopy) IntArray(0) else IntArray(region.width) { offset ->
            ((offset.toLong() * bitmap.width) / region.width).toInt().coerceIn(0, bitmap.width - 1)
        }
        val bitmapYByRotatedY = if (exactUnrotatedCopy) IntArray(0) else IntArray(region.height) { offset ->
            ((offset.toLong() * bitmap.height) / region.height).toInt().coerceIn(0, bitmap.height - 1)
        }
        surface.beginWrite("replace visible raster region").use { write ->
            val firstColumn = sourceBounds.left / surface.tileSize
            val lastColumn = (sourceBounds.right - 1) / surface.tileSize
            val firstRow = sourceBounds.top / surface.tileSize
            val lastRow = (sourceBounds.bottom - 1) / surface.tileSize
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                val tileLeft = column * surface.tileSize
                val tileTop = row * surface.tileSize
                val tileSource = PixelRect(
                    tileLeft,
                    tileTop,
                    minOf(tileLeft + surface.tileSize, surface.logicalWidth),
                    minOf(tileTop + surface.tileSize, surface.logicalHeight),
                ).intersect(sourceBounds)
                if (tileSource.isEmpty) continue
                val dirtyLocal = PixelRect(
                    tileSource.left - tileLeft,
                    tileSource.top - tileTop,
                    tileSource.right - tileLeft,
                    tileSource.bottom - tileTop,
                )
                write.editTile(PixelTileKey(column, row), dirtyLocal) { pixels, rowStride ->
                    if (exactUnrotatedCopy) {
                        for (sourceY in tileSource.top until tileSource.bottom) {
                            val count = tileSource.width
                            val sourceOffset = (sourceY - region.top) * bitmap.width + tileSource.left - region.left
                            val destinationOffset = (sourceY - tileTop) * rowStride + tileSource.left - tileLeft
                            sourcePixels.copyInto(
                                pixels,
                                destinationOffset,
                                sourceOffset,
                                sourceOffset + count,
                            )
                        }
                        return@editTile
                    }
                    for (sourceY in tileSource.top until tileSource.bottom) {
                        for (sourceX in tileSource.left until tileSource.right) {
                            val rotatedX: Int
                            val rotatedY: Int
                            when (turns) {
                                0 -> {
                                    rotatedX = sourceX
                                    rotatedY = sourceY
                                }
                                1 -> {
                                    rotatedX = surface.logicalHeight - 1 - sourceY
                                    rotatedY = sourceX
                                }
                                2 -> {
                                    rotatedX = surface.logicalWidth - 1 - sourceX
                                    rotatedY = surface.logicalHeight - 1 - sourceY
                                }
                                else -> {
                                    rotatedX = sourceY
                                    rotatedY = surface.logicalWidth - 1 - sourceX
                                }
                            }
                            if (rotatedX !in region.left until region.right ||
                                rotatedY !in region.top until region.bottom
                            ) continue
                            val bitmapX = bitmapXByRotatedX[rotatedX - region.left]
                            val bitmapY = bitmapYByRotatedY[rotatedY - region.top]
                            pixels[(sourceY - tileTop) * rowStride + sourceX - tileLeft] =
                                sourcePixels[bitmapY * bitmap.width + bitmapX]
                        }
                    }
                }
            }
            write.commit().close()
        }
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
