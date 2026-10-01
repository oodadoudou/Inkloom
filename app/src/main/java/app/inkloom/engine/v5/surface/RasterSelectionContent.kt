package app.inkloom.engine.v5.surface

import android.graphics.Bitmap

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
    /** Converts the bounded clipboard payload to a portable transparent PNG source bitmap. */
    fun toBitmap(clipboard: RasterSelectionClipboard): Bitmap {
        val pixels = when (clipboard.pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> requireNotNull(clipboard.rgba8).copyOf()
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 ->
                requireNotNull(clipboard.rgbaF16).let { source -> IntArray(source.size) { PackedRgbaF16.toPremultipliedArgb8888(source[it]) } }
        }
        // Android setPixels/getPixels use straight ARGB; the engine payload is premultiplied.
        pixels.indices.forEach { index ->
            val value = pixels[index]; val alpha = value ushr 24
            fun straight(shift: Int) = if (alpha == 0) 0 else (((value ushr shift and 255) * 255 + alpha / 2) / alpha).coerceIn(0, 255)
            pixels[index] = (alpha shl 24) or (straight(16) shl 16) or (straight(8) shl 8) or straight(0)
        }
        return Bitmap.createBitmap(clipboard.width, clipboard.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, clipboard.width, 0, 0, clipboard.width, clipboard.height)
        }
    }

    /** Imports a transparent system image without discarding its alpha channel. */
    fun fromBitmap(
        bitmap: Bitmap,
        destinationWidth: Int,
        destinationHeight: Int,
        pixelFormat: PixelTileFormat,
        sourceLeft: Int = 0,
        sourceTop: Int = 0,
    ): RasterSelectionClipboard? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val rgba = IntArray(Math.multiplyExact(bitmap.width, bitmap.height))
        bitmap.getPixels(rgba, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val alpha = ByteArray(rgba.size) { index -> (rgba[index] ushr 24).toByte() }
        if (alpha.none { it.toInt().and(0xff) != 0 }) return null
        rgba.indices.forEach { index ->
            val value = rgba[index]; val coverage = value ushr 24
            fun premultiplied(shift: Int) = ((value ushr shift and 255) * coverage + 127) / 255
            rgba[index] = (coverage shl 24) or (premultiplied(16) shl 16) or (premultiplied(8) shl 8) or premultiplied(0)
        }
        return when (pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> RasterSelectionClipboard(
                destinationWidth, destinationHeight, sourceLeft, sourceTop,
                bitmap.width, bitmap.height, pixelFormat, alpha, rgba8 = rgba,
            )
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> RasterSelectionClipboard(
                destinationWidth, destinationHeight, sourceLeft, sourceTop,
                bitmap.width, bitmap.height, pixelFormat, alpha,
                rgbaF16 = LongArray(rgba.size) { index -> PackedRgbaF16.fromPremultipliedArgb8888(rgba[index]) },
            )
        }
    }

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

    /** Paints a fill into the editable layer, with a small underpaint fringe at antialiased edges.
     * Underpaint leaves opaque contours intact; it never dilates source-over across line art.
     */
    fun fill(
        surface: PixelTileSurface,
        mask: MaskTileSurfaceSnapshot,
        colorArgb: Int,
        opacity: Float,
        edgeUnderpaintPixels: Int = 0,
        preserveAlpha: Boolean = false,
    ): RasterSelectionEdit? {
        val coreBounds = selectionBoundsInPixels(mask, surface.logicalWidth, surface.logicalHeight) ?: return null
        val radius = if (preserveAlpha) 0 else edgeUnderpaintPixels.coerceIn(0, 3)
        val bounds = PixelRect((coreBounds.left - radius).coerceAtLeast(0), (coreBounds.top - radius).coerceAtLeast(0),
            (coreBounds.right + radius).coerceAtMost(surface.logicalWidth), (coreBounds.bottom + radius).coerceAtMost(surface.logicalHeight))
        val straightAlpha = colorArgb ushr 24
        val source = scaleArgb(scaleArgb(colorArgb or 0xFF000000.toInt(), straightAlpha),
            ((opacity.takeIf(Float::isFinite) ?: 0f).coerceIn(0f, 1f) * 255f).toInt())
        val sourceF16 = PackedRgbaF16.fromPremultipliedArgb8888(source)
        fun alpha(x: Int, y: Int): Int = if (x !in 0 until surface.logicalWidth || y !in 0 until surface.logicalHeight) 0
            else selectionAlphaAtPixel(mask, x, y, surface.logicalWidth, surface.logicalHeight)
        fun fringeAlpha(x: Int, y: Int): Int {
            var coverage = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) coverage = maxOf(coverage, alpha(x + dx, y + dy))
            }
            return coverage
        }
        val transaction = surface.beginWrite("fill raster region")
        try {
            visitSelectionTiles(mask, bounds, surface.logicalWidth, surface.logicalHeight, surface.tileSize, radius) { key, tileBounds, localDirty ->
                when (surface.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> transaction.editTile(key, localDirty) { values, stride ->
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val index = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                            var destination = values[index]
                            val core = alpha(x, y)
                            if (radius > 0 && core < 255 && destination ushr 24 < 255) {
                                val edge = fringeAlpha(x, y)
                                if (edge > 0) destination = sourceOverArgb(destination, scaleArgb(source, edge))
                                values[index] = destination
                            }
                            if (core > 0) {
                                val pigment = scaleArgb(source, core)
                                values[index] = if (preserveAlpha) sourceAtopArgb(pigment, destination) else sourceOverArgb(pigment, destination)
                            }
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { values, stride ->
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val index = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                            var destination = values[index]
                            val core = alpha(x, y)
                            if (radius > 0 && core < 255 && PackedRgbaF16.alpha(destination) < 1f) {
                                val edge = fringeAlpha(x, y)
                                if (edge > 0) destination = sourceOverF16(destination, scaleF16(sourceF16, edge / 255f))
                                values[index] = destination
                            }
                            if (core > 0) {
                                val pigment = scaleF16(sourceF16, core / 255f)
                                values[index] = if (preserveAlpha) sourceAtopF16(pigment, destination) else sourceOverF16(pigment, destination)
                            }
                        }
                    }
                }
            }
            return RasterSelectionEdit(transaction.commit(), bounds)
        } catch (failure: Throwable) { transaction.close(); throw failure }
    }

    fun clear(
        surface: PixelTileSurface,
        selection: MaskTileSurfaceSnapshot,
        outside: Boolean = false,
    ): RasterSelectionEdit? {
        if (selection.isEmpty) return null
        // Clearing outside must visit existing pixels, not materialize the page-sized inverse mask.
        val occupied = if (outside) surface.snapshot().tileInfo.map { info ->
            val left = info.key.column * surface.tileSize
            val top = info.key.row * surface.tileSize
            PixelRect(left, top, minOf(left + surface.tileSize, surface.logicalWidth),
                minOf(top + surface.tileSize, surface.logicalHeight))
        } else emptyList()
        val bounds = if (outside) occupied.reduceOrNull(PixelRect::union)
            else selectionBoundsInPixels(selection, surface.logicalWidth, surface.logicalHeight)
        bounds ?: return null
        val regions = if (outside) occupied else listOf(bounds)
        val transaction = surface.beginWrite("clear raster selection")
        try {
            regions.forEach { region ->
                visitTiles(region, surface.tileSize) { key, tileBounds, localDirty ->
                    when (surface.pixelFormat) {
                        PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> transaction.editTile(key, localDirty) { values, stride ->
                            for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                                val selected = selectionAlphaAtPixel(selection, x, y, surface.logicalWidth, surface.logicalHeight)
                                val alpha = if (outside) 255 - selected else selected
                                if (alpha != 0) values[(y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize] =
                                    scaleArgb(values[(y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize], 255 - alpha)
                            }
                        }
                        PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { values, stride ->
                            for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                                val selected = selectionAlphaAtPixel(selection, x, y, surface.logicalWidth, surface.logicalHeight)
                                val alpha = if (outside) 255 - selected else selected
                                if (alpha != 0) {
                                    val index = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                                    values[index] = scaleF16(values[index], (255 - alpha) / 255f)
                                }
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

    /** Cross-document paste keeps native F16 precision when possible and converts only on demand. */
    fun convert(clipboard: RasterSelectionClipboard, format: PixelTileFormat): RasterSelectionClipboard =
        if (clipboard.pixelFormat == format) clipboard else when (format) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> clipboard.copy(pixelFormat = format,
                rgba8 = requireNotNull(clipboard.rgbaF16).let { source -> IntArray(source.size) { PackedRgbaF16.toPremultipliedArgb8888(source[it]) } }, rgbaF16 = null)
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> clipboard.copy(pixelFormat = format,
                rgba8 = null, rgbaF16 = requireNotNull(clipboard.rgba8).let { source -> LongArray(source.size) { PackedRgbaF16.fromPremultipliedArgb8888(source[it]) } })
        }

    fun paste(
        surface: PixelTileSurface,
        clipboard: RasterSelectionClipboard,
        left: Int,
        top: Int,
        preserveAlpha: Boolean = false,
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
                            values[destinationIndex] = if (preserveAlpha) sourceAtopArgb(source[sourceIndex], values[destinationIndex])
                                else sourceOverArgb(source[sourceIndex], values[destinationIndex])
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { values, stride ->
                        val source = requireNotNull(clipboard.rgbaF16)
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val sourceIndex = (y - top) * clipboard.width + x - left
                            val destinationIndex = (y - key.row * surface.tileSize) * stride + x - key.column * surface.tileSize
                            values[destinationIndex] = if (preserveAlpha) sourceAtopF16(source[sourceIndex], values[destinationIndex])
                                else sourceOverF16(source[sourceIndex], values[destinationIndex])
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

    /** A mask's occupied islands determine fill work; its bounding box can span an empty 8K page. */
    private inline fun visitSelectionTiles(
        mask: MaskTileSurfaceSnapshot, bounds: PixelRect, width: Int, height: Int, tileSize: Int, halo: Int,
        block: (PixelTileKey, PixelRect, PixelRect) -> Unit,
    ) {
        if (mask.defaultAlpha != 0) {
            visitTiles(bounds, tileSize, block)
            return
        }
        val visited = hashSetOf<PixelTileKey>()
        for (info in mask.tileInfo) {
            val left = kotlin.math.floor(info.key.column * mask.tileSize.toDouble() * width / mask.logicalWidth).toInt() - halo
            val top = kotlin.math.floor(info.key.row * mask.tileSize.toDouble() * height / mask.logicalHeight).toInt() - halo
            val right = kotlin.math.ceil(minOf((info.key.column + 1) * mask.tileSize, mask.logicalWidth).toDouble() * width / mask.logicalWidth).toInt() + halo
            val bottom = kotlin.math.ceil(minOf((info.key.row + 1) * mask.tileSize, mask.logicalHeight).toDouble() * height / mask.logicalHeight).toInt() + halo
            val island = PixelRect(left, top, right, bottom).intersect(bounds)
            if (island.isEmpty) continue
            visitTiles(island, tileSize) { key, _, _ ->
                if (visited.add(key)) {
                    val tx = key.column * tileSize; val ty = key.row * tileSize
                    val tileBounds = PixelRect(tx, ty, tx + tileSize, ty + tileSize).intersect(bounds)
                    block(key, tileBounds, PixelRect(tileBounds.left - tx, tileBounds.top - ty, tileBounds.right - tx, tileBounds.bottom - ty))
                }
            }
        }
    }

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

    private fun sourceAtopArgb(source: Int, destination: Int): Int {
        val da = destination ushr 24
        val inverse = 255 - (source ushr 24)
        fun channel(shift: Int): Int = (((source ushr shift and 255) * da + (destination ushr shift and 255) * inverse + 127) / 255).coerceAtMost(255)
        return (da shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun sourceAtopF16(source: Long, destination: Long): Long {
        val alpha = PackedRgbaF16.alpha(destination)
        val inverse = 1f - PackedRgbaF16.alpha(source).coerceIn(0f, 1f)
        return PackedRgbaF16.packPremultiplied(
            PackedRgbaF16.premultipliedRed(source) * alpha + PackedRgbaF16.premultipliedRed(destination) * inverse,
            PackedRgbaF16.premultipliedGreen(source) * alpha + PackedRgbaF16.premultipliedGreen(destination) * inverse,
            PackedRgbaF16.premultipliedBlue(source) * alpha + PackedRgbaF16.premultipliedBlue(destination) * inverse, alpha)
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
