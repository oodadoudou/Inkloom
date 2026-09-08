package app.inkloom.engine.v5.surface

/**
 * Pixel payload copied through a document selection. Color values remain premultiplied and are
 * already multiplied by [selectionAlpha], so feathered edges survive copy, cut and paste exactly.
 */
data class RasterSelectionClipboard(
    val sourceSurfaceWidth: Int,
    val sourceSurfaceHeight: Int,
    val sourceLeft: Int,
    val sourceTop: Int,
    val width: Int,
    val height: Int,
    val pixelFormat: PixelTileFormat,
    val selectionAlpha: ByteArray,
    internal val rgba8: IntArray? = null,
    internal val rgbaF16: LongArray? = null,
) {
    init {
        require(width > 0 && height > 0)
        require(selectionAlpha.size == Math.multiplyExact(width, height))
        when (pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> require(rgba8?.size == selectionAlpha.size && rgbaF16 == null)
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> require(rgbaF16?.size == selectionAlpha.size && rgba8 == null)
        }
    }
}

data class RasterSelectionEdit(
    val record: PixelTileTransactionRecord,
    val dirtyBounds: PixelRect,
)

/** Sparse-tile selection consumers shared by delete/cut/copy/paste production call paths. */
object RasterSelectionContent {
    fun copy(
        pixels: PixelTileSurfaceSnapshot,
        selection: MaskTileSurfaceSnapshot,
    ): RasterSelectionClipboard? {
        val bounds = selectionBoundsInPixels(selection, pixels.logicalWidth, pixels.logicalHeight) ?: return null
        val alpha = sampledAlpha(selection, bounds, pixels.logicalWidth, pixels.logicalHeight)
        return when (pixels.pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> {
                val copied = pixels.copyPremultipliedPixels(bounds)
                copied.indices.forEach { index -> copied[index] = scaleArgb(copied[index], alpha[index].toInt().and(0xff)) }
                RasterSelectionClipboard(pixels.logicalWidth, pixels.logicalHeight, bounds.left, bounds.top, bounds.width, bounds.height, pixels.pixelFormat, alpha, rgba8 = copied)
            }
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> {
                val copied = pixels.copyPremultipliedF16(bounds)
                copied.indices.forEach { index -> copied[index] = scaleF16(copied[index], alpha[index].toInt().and(0xff) / 255f) }
                RasterSelectionClipboard(pixels.logicalWidth, pixels.logicalHeight, bounds.left, bounds.top, bounds.width, bounds.height, pixels.pixelFormat, alpha, rgbaF16 = copied)
            }
        }
    }

    fun clear(surface: PixelTileSurface, selection: MaskTileSurfaceSnapshot): RasterSelectionEdit? {
        val bounds = selectionBoundsInPixels(selection, surface.logicalWidth, surface.logicalHeight) ?: return null
        val transaction = surface.beginWrite("clear raster selection")
        try {
            visitTiles(bounds, surface.tileSize) { key, tileBounds, localDirty ->
                when (surface.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> transaction.editTile(key, localDirty) { values, stride ->
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val alpha = selectionAlphaAtPixel(selection, x, y, surface.logicalWidth, surface.logicalHeight)
                            if (alpha != 0) values[(y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize] =
                                scaleArgb(values[(y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize], 255 - alpha)
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { values, stride ->
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val alpha = selectionAlphaAtPixel(selection, x, y, surface.logicalWidth, surface.logicalHeight)
                            if (alpha != 0) {
                                val index = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                                values[index] = scaleF16(values[index], (255 - alpha) / 255f)
                            }
                        }
                    }
                }
            }
            return RasterSelectionEdit(transaction.commit(), bounds)
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
    }

    fun paste(
        surface: PixelTileSurface,
        clipboard: RasterSelectionClipboard,
        left: Int,
        top: Int,
    ): RasterSelectionEdit? {
        require(surface.pixelFormat == clipboard.pixelFormat) { "Clipboard color format does not match the layer" }
        val requested = PixelRect(left, top, left + clipboard.width, top + clipboard.height)
        val bounds = requested.intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)).takeUnless(PixelRect::isEmpty)
            ?: return null
        val transaction = surface.beginWrite("paste raster selection")
        try {
            visitTiles(bounds, surface.tileSize) { key, tileBounds, localDirty ->
                when (surface.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> transaction.editTile(key, localDirty) { values, stride ->
                        val source = requireNotNull(clipboard.rgba8)
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val sourceIndex = (y - top) * clipboard.width + x - left
                            val destinationIndex = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                            values[destinationIndex] = sourceOverArgb(source[sourceIndex], values[destinationIndex])
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { values, stride ->
                        val source = requireNotNull(clipboard.rgbaF16)
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val sourceIndex = (y - top) * clipboard.width + x - left
                            val destinationIndex = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                            values[destinationIndex] = sourceOverF16(source[sourceIndex], values[destinationIndex])
                        }
                    }
                }
            }
            return RasterSelectionEdit(transaction.commit(), bounds)
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
    }

    fun selectionAt(
        clipboard: RasterSelectionClipboard,
        logicalWidth: Int,
        logicalHeight: Int,
        tileSize: Int,
        left: Int,
        top: Int,
        destinationSurfaceWidth: Int,
        destinationSurfaceHeight: Int,
    ): MaskTileSurfaceSnapshot {
        val surface = MaskTileSurface(logicalWidth, logicalHeight, tileSize)
        val pixelBounds = PixelRect(left, top, left + clipboard.width, top + clipboard.height)
            .intersect(PixelRect(0, 0, destinationSurfaceWidth, destinationSurfaceHeight))
        val bounds = PixelRect(
            kotlin.math.floor(pixelBounds.left.toDouble() * logicalWidth / destinationSurfaceWidth).toInt(),
            kotlin.math.floor(pixelBounds.top.toDouble() * logicalHeight / destinationSurfaceHeight).toInt(),
            kotlin.math.ceil(pixelBounds.right.toDouble() * logicalWidth / destinationSurfaceWidth).toInt(),
            kotlin.math.ceil(pixelBounds.bottom.toDouble() * logicalHeight / destinationSurfaceHeight).toInt(),
        ).intersect(PixelRect(0, 0, logicalWidth, logicalHeight))
        if (bounds.isEmpty) return surface.snapshot()
        val write = surface.beginWrite("restore pasted selection")
        try {
            for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
                val pixelX = ((x + .5f) * destinationSurfaceWidth / logicalWidth).toInt().coerceIn(pixelBounds.left, pixelBounds.right - 1)
                val pixelY = ((y + .5f) * destinationSurfaceHeight / logicalHeight).toInt().coerceIn(pixelBounds.top, pixelBounds.bottom - 1)
                val alpha = clipboard.selectionAlpha[(pixelY - top) * clipboard.width + pixelX - left].toInt().and(0xff)
                if (alpha != 0) write.setAlpha(x, y, alpha)
            }
            write.commit().close()
        } catch (failure: Throwable) {
            write.close()
            throw failure
        }
        return surface.snapshot()
    }

    internal fun selectionBoundsInPixels(selection: MaskTileSurfaceSnapshot, width: Int, height: Int): PixelRect? {
        val source = selection.nonZeroBounds() ?: return null
        return PixelRect(
            kotlin.math.floor(source.left.toDouble() * width / selection.logicalWidth).toInt().coerceIn(0, width),
            kotlin.math.floor(source.top.toDouble() * height / selection.logicalHeight).toInt().coerceIn(0, height),
            kotlin.math.ceil(source.right.toDouble() * width / selection.logicalWidth).toInt().coerceIn(0, width),
            kotlin.math.ceil(source.bottom.toDouble() * height / selection.logicalHeight).toInt().coerceIn(0, height),
        ).takeUnless(PixelRect::isEmpty)
    }

    private fun sampledAlpha(selection: MaskTileSurfaceSnapshot, bounds: PixelRect, width: Int, height: Int): ByteArray =
        ByteArray(Math.multiplyExact(bounds.width, bounds.height)) { index ->
            val x = bounds.left + index % bounds.width
            val y = bounds.top + index / bounds.width
            selectionAlphaAtPixel(selection, x, y, width, height).toByte()
        }

    internal fun selectionAlphaAtPixel(selection: MaskTileSurfaceSnapshot, x: Int, y: Int, width: Int, height: Int): Int =
        selection.alphaAt(
            ((x + .5f) * selection.logicalWidth / width).toInt().coerceIn(0, selection.logicalWidth - 1),
            ((y + .5f) * selection.logicalHeight / height).toInt().coerceIn(0, selection.logicalHeight - 1),
        )

    private inline fun visitTiles(bounds: PixelRect, tileSize: Int, block: (PixelTileKey, PixelRect, PixelRect) -> Unit) {
        for (row in bounds.top / tileSize..(bounds.bottom - 1) / tileSize) {
            for (column in bounds.left / tileSize..(bounds.right - 1) / tileSize) {
                val key = PixelTileKey(column, row)
                val tile = PixelRect(column * tileSize, row * tileSize, (column + 1) * tileSize, (row + 1) * tileSize)
                val overlap = tile.intersect(bounds)
                block(key, overlap, PixelRect(overlap.left - tile.left, overlap.top - tile.top, overlap.right - tile.left, overlap.bottom - tile.top))
            }
        }
    }

    private fun scaleArgb(value: Int, alpha: Int): Int {
        if (alpha <= 0) return 0
        if (alpha >= 255) return value
        fun channel(shift: Int): Int = (((value ushr shift and 0xff) * alpha + 127) / 255) shl shift
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    private fun sourceOverArgb(source: Int, destination: Int): Int {
        val inverse = 255 - (source ushr 24 and 0xff)
        fun channel(shift: Int): Int = ((source ushr shift and 0xff) + ((destination ushr shift and 0xff) * inverse + 127) / 255).coerceAtMost(255)
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun scaleF16(value: Long, factor: Float): Long = PackedRgbaF16.packPremultiplied(
        PackedRgbaF16.premultipliedRed(value) * factor,
        PackedRgbaF16.premultipliedGreen(value) * factor,
        PackedRgbaF16.premultipliedBlue(value) * factor,
        PackedRgbaF16.alpha(value) * factor,
    )

    private fun sourceOverF16(source: Long, destination: Long): Long {
        val inverse = 1f - PackedRgbaF16.alpha(source).coerceIn(0f, 1f)
        return PackedRgbaF16.packPremultiplied(
            PackedRgbaF16.premultipliedRed(source) + PackedRgbaF16.premultipliedRed(destination) * inverse,
            PackedRgbaF16.premultipliedGreen(source) + PackedRgbaF16.premultipliedGreen(destination) * inverse,
            PackedRgbaF16.premultipliedBlue(source) + PackedRgbaF16.premultipliedBlue(destination) * inverse,
            PackedRgbaF16.alpha(source) + PackedRgbaF16.alpha(destination) * inverse,
        )
    }
}
