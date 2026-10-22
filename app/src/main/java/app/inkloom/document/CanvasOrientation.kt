package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot

/** CSP Edit > Rotate/Flip canvas: permanent changes to the artwork, unlike the view-only mirror. */
enum class CanvasOrientationChange(val clockwiseQuarterTurns: Int, val flip: CanvasFlip?) {
    ROTATE_CLOCKWISE(1, null),
    ROTATE_180(2, null),
    ROTATE_COUNTERCLOCKWISE(3, null),
    FLIP_HORIZONTAL(0, CanvasFlip.HORIZONTAL),
    FLIP_VERTICAL(0, CanvasFlip.VERTICAL),
}

enum class CanvasFlip {
    HORIZONTAL, VERTICAL;

    val other: CanvasFlip get() = if (this == HORIZONTAL) VERTICAL else HORIZONTAL
}

/**
 * Pure geometry for rotating and flipping a whole page. Rotation stays lazy for paint layers
 * (their quarter-turn flag), while layer masks, which are stored in page space, are rotated as
 * pixels. Flips rewrite pixels: paint layers keep their rotation flag and flip the stored axis
 * that shows as the requested page axis.
 */
object CanvasOrientation {
    /** Imported PDF and image backgrounds cannot be mirrored; paper templates flip as a no-op. */
    fun canFlip(page: InkPage): Boolean = page.background is PageBackground.Paper

    fun rotated(page: InkPage, clockwiseQuarterTurns: Int): InkPage {
        var result = page
        repeat(clockwiseQuarterTurns.mod(4)) { result = PageGeometry.rotateClockwise(result) }
        return result
    }

    /** The stored axis to flip for content kept [quarterTurns] clockwise from its storage orientation. */
    fun storedAxis(flip: CanvasFlip, quarterTurns: Int): CanvasFlip = if (quarterTurns.mod(2) == 0) flip else flip.other

    fun flippedCrop(crop: PageCrop, flip: CanvasFlip): PageCrop = when (flip) {
        CanvasFlip.HORIZONTAL -> PageCrop(1f - crop.right, crop.top, 1f - crop.left, crop.bottom)
        CanvasFlip.VERTICAL -> PageCrop(crop.left, 1f - crop.bottom, crop.right, 1f - crop.top)
    }

    fun flippedBounds(bounds: PdfTextBounds, flip: CanvasFlip): PdfTextBounds = when (flip) {
        CanvasFlip.HORIZONTAL -> bounds.copy(left = 1f - bounds.right, right = 1f - bounds.left)
        CanvasFlip.VERTICAL -> bounds.copy(top = 1f - bounds.bottom, bottom = 1f - bounds.top)
    }

    fun flippedFrame(frame: PageBackgroundFrame, pageWidth: Float, pageHeight: Float, flip: CanvasFlip): PageBackgroundFrame = when (flip) {
        CanvasFlip.HORIZONTAL -> frame.copy(left = pageWidth - frame.left - frame.width)
        CanvasFlip.VERTICAL -> frame.copy(top = pageHeight - frame.top - frame.height)
    }

    /**
     * Objects keep their editable data: the box moves to its mirrored place, the rotation turns
     * the other way and the content is mirrored inside the box by its local lattice, so taps and
     * selection still find the object where it is drawn.
     */
    fun flippedElement(element: PageElement, pageWidth: Float, pageHeight: Float, flip: CanvasFlip): PageElement {
        val width = pageWidth.coerceAtLeast(1f)
        val height = pageHeight.coerceAtLeast(1f)
        val lattice = element.deformation ?: ElementDeformation(2, 2, listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, 0f), ShapeVertex(0f, 1f), ShapeVertex(1f, 1f)))
        val mirrored = lattice.copy(vertices = lattice.vertices.map { vertex ->
            if (flip == CanvasFlip.HORIZONTAL) ShapeVertex(1f - vertex.x, vertex.y) else ShapeVertex(vertex.x, 1f - vertex.y)
        })
        val x = if (flip == CanvasFlip.HORIZONTAL) 1f - element.x - element.width / width else element.x
        val y = if (flip == CanvasFlip.VERTICAL) 1f - element.y - element.height / height else element.y
        val rotation = if (element.rotationDegrees == 0f) 0f else (-element.rotationDegrees).mod(360f)
        val placed = when (element) {
            is PageElement.Text -> element.copy(x = x, y = y, rotationDegrees = rotation)
            is PageElement.Sticky -> element.copy(x = x, y = y, rotationDegrees = rotation)
            is PageElement.Shape -> element.copy(x = x, y = y, rotationDegrees = rotation)
            is PageElement.Image -> element.copy(x = x, y = y, rotationDegrees = rotation)
            is PageElement.Fill -> element.copy(x = x, y = y, rotationDegrees = rotation)
            is PageElement.Link -> element.copy(x = x, y = y, rotationDegrees = rotation)
        }
        return placed.withDeformation(mirrored)
    }

    /** Lossless mirror of every stored pixel, for both 8-bit and F16 surfaces. */
    fun flippedPixels(source: PixelTileSurfaceSnapshot, flip: CanvasFlip): PixelTileSurface {
        val width = source.logicalWidth
        val height = source.logicalHeight
        val tile = source.tileSize
        val output = PixelTileSurface(width, height, tile, pixelFormat = source.pixelFormat)
        fun sourceX(x: Int) = if (flip == CanvasFlip.HORIZONTAL) width - 1 - x else x
        fun sourceY(y: Int) = if (flip == CanvasFlip.VERTICAL) height - 1 - y else y
        output.beginWrite("flip canvas").use { write ->
            mirroredKeys(source.tileInfo.map { it.key }, width, height, tile, flip).forEach { key ->
                val tw = minOf(tile, width - key.column * tile)
                val th = minOf(tile, height - key.row * tile)
                if (source.pixelFormat.isHighPrecision) write.editTileF16(key, PixelRect(0, 0, tw, th)) { pixels, stride ->
                    for (y in 0 until th) for (x in 0 until tw) {
                        val color = source.linearPixelAt(sourceX(key.column * tile + x), sourceY(key.row * tile + y))
                        pixels[y * stride + x] = PackedRgbaF16.packPremultiplied(color.red, color.green, color.blue, color.alpha)
                    }
                } else write.editTile(key, PixelRect(0, 0, tw, th)) { pixels, stride ->
                    for (y in 0 until th) for (x in 0 until tw)
                        pixels[y * stride + x] = source.pixelAt(sourceX(key.column * tile + x), sourceY(key.row * tile + y))
                }
            }
            write.commit().close()
        }
        return output
    }

    fun flippedMask(source: MaskTileSurfaceSnapshot, flip: CanvasFlip): MaskTileSurfaceSnapshot {
        val width = source.logicalWidth
        val height = source.logicalHeight
        val output = MaskTileSurface(width, height, source.tileSize, initialDefaultAlpha = source.defaultAlpha)
        output.beginWrite("flip mask").use { write ->
            mirroredKeys(source.tileInfo.map { it.key }, width, height, source.tileSize, flip).forEach { key ->
                val tw = minOf(source.tileSize, width - key.column * source.tileSize)
                val th = minOf(source.tileSize, height - key.row * source.tileSize)
                write.editTile(key, PixelRect(0, 0, tw, th)) { alpha, stride ->
                    for (y in 0 until th) for (x in 0 until tw) {
                        val px = key.column * source.tileSize + x
                        val py = key.row * source.tileSize + y
                        alpha[y * stride + x] = source.alphaAt(
                            if (flip == CanvasFlip.HORIZONTAL) width - 1 - px else px,
                            if (flip == CanvasFlip.VERTICAL) height - 1 - py else py).toByte()
                    }
                }
            }
            write.commit().close()
        }
        return output.snapshot()
    }

    /** Destination tiles that can receive pixels from the mirrored source tiles. */
    private fun mirroredKeys(keys: List<PixelTileKey>, width: Int, height: Int, tile: Int, flip: CanvasFlip): Set<PixelTileKey> {
        val result = linkedSetOf<PixelTileKey>()
        keys.forEach { key ->
            val left = key.column * tile
            val top = key.row * tile
            val right = minOf(width, left + tile) - 1
            val bottom = minOf(height, top + tile) - 1
            if (right < left || bottom < top) return@forEach
            val (x0, x1) = if (flip == CanvasFlip.HORIZONTAL) width - 1 - right to width - 1 - left else left to right
            val (y0, y1) = if (flip == CanvasFlip.VERTICAL) height - 1 - bottom to height - 1 - top else top to bottom
            for (row in y0 / tile..y1 / tile) for (column in x0 / tile..x1 / tile) result += PixelTileKey(column, row)
        }
        return result
    }
}
