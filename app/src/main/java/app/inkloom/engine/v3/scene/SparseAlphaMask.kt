package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.engine.v3.surface.MaskComposeMode
import app.inkloom.engine.v3.surface.MaskTileSurface
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.tools.NativeRasterOps

/**
 * Android compatibility facade over the V3 one-byte [MaskTileSurface].
 *
 * The sparse surface is authoritative. Full [Bitmap] values are created only by explicit legacy
 * display/export boundaries while those consumers are migrated to bounded tile reads.
 */
internal class SparseAlphaMask private constructor(
    internal val surface: MaskTileSurface,
) : AutoCloseable {
    val width: Int get() = surface.logicalWidth
    val height: Int get() = surface.logicalHeight
    val isEmpty: Boolean get() = surface.isEmpty
    val tileCount: Int get() = surface.tileCount
    val retainedPixels: Long get() = surface.snapshot().tileInfo.sumOf { info ->
        tileRect(info.key).let { it.width.toLong() * it.height }
    }

    /** Explicit full-page compatibility boundary. Do not use for tool dirty-ROI sampling. */
    fun snapshot(): Bitmap = bitmapFrom(surface.snapshot(), PixelRect(0, 0, width, height))

    fun draw(canvas: Canvas, destination: RectF, paint: Paint? = null) {
        val snapshot = surface.snapshot()
        val scaleX = destination.width() / width.coerceAtLeast(1)
        val scaleY = destination.height() / height.coerceAtLeast(1)
        snapshot.tileInfo.forEach { info ->
            val bounds = tileRect(info.key)
            val tile = bitmapFrom(snapshot, bounds)
            try {
                canvas.drawBitmap(
                    tile,
                    null,
                    RectF(
                        destination.left + bounds.left * scaleX,
                        destination.top + bounds.top * scaleY,
                        destination.left + bounds.right * scaleX,
                        destination.top + bounds.bottom * scaleY,
                    ),
                    paint,
                )
            } finally {
                tile.recycle()
            }
        }
    }

    fun copy(): SparseAlphaMask = SparseAlphaMask(copySurface(surface.snapshot()))

    fun composed(region: Bitmap?, operation: AlphaMaskOperation): SparseAlphaMask? {
        val incoming = fromBitmap(region, surface.tileSize)
        if (operation == AlphaMaskOperation.REPLACE) return incoming
        if (incoming == null) return if (operation == AlphaMaskOperation.INTERSECT) null else copy()
        val result = copySurface(surface.snapshot())
        result.beginWrite("selection ${operation.name.lowercase()}").run {
            composeSurface(
                incoming.surface.snapshot(),
                when (operation) {
                    AlphaMaskOperation.ADD -> MaskComposeMode.ADD
                    AlphaMaskOperation.SUBTRACT -> MaskComposeMode.SUBTRACT
                    AlphaMaskOperation.INTERSECT -> MaskComposeMode.INTERSECT
                    AlphaMaskOperation.REPLACE -> error("handled above")
                },
            )
            commit().close()
        }
        return result.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
    }

    fun inverted(): SparseAlphaMask {
        val source = surface.snapshot()
        val result = MaskTileSurface(width, height, surface.tileSize)
        val columns = (width + surface.tileSize - 1) / surface.tileSize
        val rows = (height + surface.tileSize - 1) / surface.tileSize
        result.beginWrite("invert selection").run {
            for (row in 0 until rows) for (column in 0 until columns) {
                val key = PixelTileKey(column, row)
                val bounds = tileRect(key)
                val sourceAlpha = source.copyTileAlpha(key)
                editTile(key, PixelRect(0, 0, bounds.width, bounds.height)) { output, stride ->
                    for (y in 0 until bounds.height) for (x in 0 until bounds.width) {
                        val index = y * stride + x
                        output[index] = (255 - (sourceAlpha?.get(index)?.toInt()?.and(0xff) ?: 0)).toByte()
                    }
                }
            }
            commit().close()
        }
        return SparseAlphaMask(result)
    }

    /** Binary morphology is restricted to occupied tile islands plus the requested source halo. */
    fun morphed(radius: Int, dilate: Boolean): SparseAlphaMask? {
        if (radius <= 0) return copy()
        val source = surface.snapshot()
        if (source.isEmpty) return null
        val logical = PixelRect(0, 0, width, height)
        val regions = mergeTouchingRects(source.tileInfo.map { info ->
            val tile = tileRect(info.key)
            PixelRect(tile.left - radius, tile.top - radius, tile.right + radius, tile.bottom + radius).intersect(logical)
        })
        val result = MaskTileSurface(width, height, surface.tileSize)
        result.beginWrite(if (dilate) "expand selection" else "shrink selection").run {
            regions.forEach { region ->
                val input = bitmapFrom(source, region)
                val output = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
                try {
                    NativeRasterOps.morphAlpha(input, output, radius, dilate)
                    composeAlpha(region, bitmapAlpha(output), mode = MaskComposeMode.ADD)
                } finally {
                    input.recycle()
                    output.recycle()
                }
            }
            commit().close()
        }
        return result.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
    }

    /** Pixel storage belongs to the document runtime and is released by normal object lifetime. */
    override fun close() = Unit

    private fun tileRect(key: PixelTileKey): PixelRect {
        val left = key.column * surface.tileSize
        val top = key.row * surface.tileSize
        return PixelRect(left, top, minOf(left + surface.tileSize, width), minOf(top + surface.tileSize, height))
    }

    private fun mergeTouchingRects(input: List<PixelRect>): List<PixelRect> {
        val merged = mutableListOf<PixelRect>()
        input.filterNot(PixelRect::isEmpty).forEach { value ->
            var candidate = value
            var index = 0
            while (index < merged.size) {
                val existing = merged[index]
                val touches = candidate.left <= existing.right && candidate.right >= existing.left &&
                    candidate.top <= existing.bottom && candidate.bottom >= existing.top
                if (touches) {
                    candidate = candidate.union(existing)
                    merged.removeAt(index)
                    index = 0
                } else index++
            }
            merged += candidate
        }
        return merged
    }

    companion object {
        const val DEFAULT_TILE_SIZE = 256

        fun fromBitmap(source: Bitmap?, tileSize: Int = DEFAULT_TILE_SIZE): SparseAlphaMask? {
            if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) return null
            val coordinates = NativeRasterOps.nonEmptyAlphaTiles(source, tileSize)
            check(coordinates.size % 2 == 0) { "Native alpha tile scan returned malformed coordinates" }
            if (coordinates.isEmpty()) return null
            val surface = MaskTileSurface(source.width, source.height, tileSize)
            surface.beginWrite("import platform selection mask").run {
                var index = 0
                while (index < coordinates.size) {
                    val key = PixelTileKey(coordinates[index], coordinates[index + 1])
                    val left = key.column * tileSize
                    val top = key.row * tileSize
                    val tileWidth = minOf(tileSize, source.width - left)
                    val tileHeight = minOf(tileSize, source.height - top)
                    val pixels = IntArray(tileWidth * tileHeight)
                    source.getPixels(pixels, 0, tileWidth, left, top, tileWidth, tileHeight)
                    editTile(key, PixelRect(0, 0, tileWidth, tileHeight)) { alpha, stride ->
                        for (y in 0 until tileHeight) for (x in 0 until tileWidth) {
                            alpha[y * stride + x] = (pixels[y * tileWidth + x] ushr 24).toByte()
                        }
                    }
                    index += 2
                }
                commit().close()
            }
            return SparseAlphaMask(surface)
        }

        fun fromSurface(surface: MaskTileSurface): SparseAlphaMask? =
            surface.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)

        private fun copySurface(source: MaskTileSurfaceSnapshot): MaskTileSurface =
            MaskTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize).also { copy ->
                if (!source.isEmpty) copy.beginWrite("copy selection").run {
                    composeSurface(source, MaskComposeMode.ADD)
                    commit().close()
                }
            }

        private fun bitmapFrom(source: MaskTileSurfaceSnapshot, bounds: PixelRect): Bitmap {
            val alpha = source.copyAlpha(bounds)
            val pixels = IntArray(alpha.size) { index ->
                val value = alpha[index].toInt().and(0xff)
                (value shl 24) or (Color.WHITE and 0x00ffffff)
            }
            return Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.setPixels(pixels, 0, bounds.width, 0, 0, bounds.width, bounds.height)
            }
        }

        private fun bitmapAlpha(source: Bitmap): ByteArray {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            return ByteArray(pixels.size) { index -> (pixels[index] ushr 24).toByte() }
        }
    }
}

internal enum class AlphaMaskOperation { REPLACE, ADD, SUBTRACT, INTERSECT }
