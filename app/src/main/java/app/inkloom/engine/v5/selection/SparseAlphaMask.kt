package app.inkloom.engine.v5.selection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.android.MaskTileCanvasRenderer
import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.document.SelectionMaskCompositor
import kotlin.math.ceil

/**
 * Android presentation/producer facade over the V5 one-byte [MaskTileSurface].
 *
 * The sparse surface is authoritative. Platform bitmaps exist only at bounded Android operation
 * boundaries and are converted into sparse document coverage before publication.
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

    fun draw(canvas: Canvas, destination: RectF, paint: Paint? = null) {
        MaskTileCanvasRenderer.draw(surface.snapshot(), canvas, destination, paint)
    }

    fun copy(): SparseAlphaMask = SparseAlphaMask(copySurface(surface.snapshot()))

    fun composed(region: Bitmap?, operation: AlphaMaskOperation): SparseAlphaMask? {
        val incoming = fromBitmap(region, surface.tileSize)
        return composed(incoming, operation)
    }

    fun composed(incoming: SparseAlphaMask?, operation: AlphaMaskOperation): SparseAlphaMask? {
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
        val result = copySurface(surface.snapshot())
        result.beginWrite("invert selection").run { invert(); commit().close() }
        return SparseAlphaMask(result)
    }

    /** Binary morphology is restricted to occupied tile islands plus the requested source halo. */
    fun morphed(radius: Int, dilate: Boolean): SparseAlphaMask? {
        if (radius <= 0) return copy()
        val source = surface.snapshot()
        if (source.isEmpty) return null
        val logical = PixelRect(0, 0, width, height)
        if (source.defaultAlpha != 0 && source.tileInfo.isEmpty()) return copy()
        val regions = mergeTouchingRects(source.tileInfo.map { info ->
            val tile = tileRect(info.key)
            PixelRect(tile.left - radius, tile.top - radius, tile.right + radius, tile.bottom + radius).intersect(logical)
        })
        val result = MaskTileSurface(width, height, surface.tileSize, initialDefaultAlpha = source.defaultAlpha)
        result.beginWrite(if (dilate) "expand selection" else "shrink selection").run {
            regions.forEach { region ->
                val input = bitmapFrom(source, region)
                val output = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
                try {
                    NativeRasterOps.morphAlpha(input, output, radius, dilate)
                    composeAlpha(region, bitmapAlpha(output), mode = MaskComposeMode.REPLACE)
                } finally {
                    input.recycle()
                    output.recycle()
                }
            }
            commit().close()
        }
        return result.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
    }

    /** Feathering is evaluated once over merged sparse islands, preventing per-region seams. */
    fun feathered(radius: Float): SparseAlphaMask? = filtered(
        halo = ceil(radius.coerceAtLeast(0f) * 4f).toInt().coerceAtLeast(1),
        label = "feather selection",
    ) { input -> SelectionMaskCompositor.feather(input, radius, input.width.toFloat()) }

    /** Two-sided marching-ants alpha generated with a one-pixel source halo. */
    fun boundary(): SparseAlphaMask? = filtered(halo = 1, label = "selection boundary") { input ->
        Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).let { output ->
            if (NativeRasterOps.boundaryAlpha(input, output)) output else run { output.recycle(); null }
        }
    }

    private fun filtered(
        halo: Int,
        label: String,
        operation: (Bitmap) -> Bitmap?,
    ): SparseAlphaMask? {
        val source = surface.snapshot()
        if (source.isEmpty) return null
        val logical = PixelRect(0, 0, width, height)
        if (source.defaultAlpha != 0 && source.tileInfo.isEmpty()) {
            return if (label == "selection boundary") pageBoundaryMask() else copy()
        }
        val regions = mergeTouchingRects(source.tileInfo.map { info ->
            val tile = tileRect(info.key)
            PixelRect(tile.left - halo, tile.top - halo, tile.right + halo, tile.bottom + halo).intersect(logical)
        })
        val result = MaskTileSurface(width, height, surface.tileSize, initialDefaultAlpha = if (label == "selection boundary") 0 else source.defaultAlpha)
        result.beginWrite(label).run {
            regions.forEach { region ->
                val input = bitmapFrom(source, region)
                val output = try { operation(input) } finally { input.recycle() }
                if (output != null) try {
                    composeAlpha(region, bitmapAlpha(output), mode = if (label == "selection boundary") MaskComposeMode.ADD else MaskComposeMode.REPLACE)
                } finally {
                    output.recycle()
                }
            }
            commit().close()
        }
        return result.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
    }

    private fun pageBoundaryMask(): SparseAlphaMask? {
        val result = MaskTileSurface(width, height, surface.tileSize)
        result.beginWrite("selection page boundary").run {
            for (x in 0 until width) {
                setAlpha(x, 0, 255)
                if (height > 1) setAlpha(x, height - 1, 255)
            }
            for (y in 1 until height - 1) {
                setAlpha(0, y, 255)
                if (width > 1) setAlpha(width - 1, y, 255)
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

        fun fromFloodFill(
            source: Bitmap,
            sourceLeft: Int,
            sourceTop: Int,
            sourcePageWidth: Int,
            sourcePageHeight: Int,
            pageWidth: Float,
            pageHeight: Float,
        ): SparseAlphaMask? {
            if (source.isRecycled || sourcePageWidth <= 0 || sourcePageHeight <= 0) return null
            val size = SelectionMaskCompositor.maskSize(pageWidth, pageHeight) ?: return null
            val left = kotlin.math.floor(sourceLeft.toFloat() / sourcePageWidth * size.width).toInt().coerceIn(0, size.width - 1)
            val top = kotlin.math.floor(sourceTop.toFloat() / sourcePageHeight * size.height).toInt().coerceIn(0, size.height - 1)
            val right = ceil((sourceLeft + source.width).toFloat() / sourcePageWidth * size.width).toInt().coerceIn(left + 1, size.width)
            val bottom = ceil((sourceTop + source.height).toFloat() / sourcePageHeight * size.height).toInt().coerceIn(top + 1, size.height)
            val scaled = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            Canvas(scaled).drawBitmap(source, null, RectF(0f, 0f, scaled.width.toFloat(), scaled.height.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return try { fromBitmapRegion(scaled, left, top, size.width, size.height) } finally { scaled.recycle() }
        }

        /** Builds a non-contiguous color-range selection from a composite preview. */
        fun fromColorRange(
            source: Bitmap,
            targetColor: Int,
            tolerance: Float,
            includeAlpha: Boolean,
            pageWidth: Float,
            pageHeight: Float,
        ): SparseAlphaMask? {
            if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val tr = targetColor ushr 16 and 0xff
            val tg = targetColor ushr 8 and 0xff
            val tb = targetColor and 0xff
            val ta = targetColor ushr 24 and 0xff
            val limit = tolerance.coerceIn(0f, 1f) * if (includeAlpha) 510f else 441.673f
            val maskPixels = IntArray(pixels.size) { index ->
                val color = pixels[index]
                val dr = (color ushr 16 and 0xff) - tr
                val dg = (color ushr 8 and 0xff) - tg
                val db = (color and 0xff) - tb
                val da = if (includeAlpha) (color ushr 24 and 0xff) - ta else 0
                val distance = kotlin.math.sqrt((dr * dr + dg * dg + db * db + da * da).toFloat())
                if (distance <= limit) Color.WHITE else Color.TRANSPARENT
            }
            val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(maskPixels, 0, source.width, 0, 0, source.width, source.height)
            return try {
                fromFloodFill(bitmap, 0, 0, source.width, source.height, pageWidth, pageHeight)
            } finally {
                bitmap.recycle()
            }
        }

        fun full(pageWidth: Float, pageHeight: Float): SparseAlphaMask? {
            val size = SelectionMaskCompositor.maskSize(pageWidth, pageHeight) ?: return null
            return SparseAlphaMask(
                MaskTileSurface(size.width, size.height, DEFAULT_TILE_SIZE, initialDefaultAlpha = 255),
            )
        }

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

        fun fromSnapshot(snapshot: MaskTileSurfaceSnapshot): SparseAlphaMask? =
            snapshot.takeUnless(MaskTileSurfaceSnapshot::isEmpty)?.let { SparseAlphaMask(copySurface(it)) }

        internal fun fromBitmapRegion(source: Bitmap, left: Int, top: Int, width: Int, height: Int): SparseAlphaMask? {
            val alpha = bitmapAlpha(source)
            if (alpha.none { it.toInt() != 0 }) return null
            if (left == 0 && top == 0 && source.width == width && source.height == height &&
                alpha.all { it.toInt().and(0xff) == 255 }
            ) {
                return SparseAlphaMask(
                    MaskTileSurface(width, height, DEFAULT_TILE_SIZE, initialDefaultAlpha = 255),
                )
            }
            val surface = MaskTileSurface(width, height, DEFAULT_TILE_SIZE)
            surface.beginWrite("import bounded platform selection").run {
                composeAlpha(PixelRect(left, top, left + source.width, top + source.height), alpha, mode = MaskComposeMode.ADD)
                commit().close()
            }
            return surface.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
        }

        private fun copySurface(source: MaskTileSurfaceSnapshot): MaskTileSurface =
            MaskTileSurface(
                source.logicalWidth,
                source.logicalHeight,
                source.tileSize,
                initialDefaultAlpha = source.defaultAlpha,
            ).also { copy ->
                if (source.tileCount > 0) copy.beginWrite("copy selection").run {
                    composeSurface(source, MaskComposeMode.REPLACE)
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
