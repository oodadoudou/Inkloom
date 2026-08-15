package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.surface.MaskComposeMode
import app.inkloom.engine.v3.surface.MaskTileSurface
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.tools.NativeRasterOps
import app.inkloom.document.SelectionMaskCompositor
import kotlin.math.ceil

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
        val regions = mergeTouchingRects(source.tileInfo.map { info ->
            val tile = tileRect(info.key)
            PixelRect(tile.left - halo, tile.top - halo, tile.right + halo, tile.bottom + halo).intersect(logical)
        })
        val result = MaskTileSurface(width, height, surface.tileSize)
        result.beginWrite(label).run {
            regions.forEach { region ->
                val input = bitmapFrom(source, region)
                val output = try { operation(input) } finally { input.recycle() }
                if (output != null) try {
                    composeAlpha(region, bitmapAlpha(output), mode = MaskComposeMode.ADD)
                } finally {
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

        fun polygon(points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): SparseAlphaMask? {
            if (points.size < 3) return null
            val size = SelectionMaskCompositor.maskSize(pageWidth, pageHeight) ?: return null
            val left = kotlin.math.floor(points.minOf(StrokePoint::x).coerceIn(0f, 1f) * size.width - 2f).toInt().coerceIn(0, size.width - 1)
            val top = kotlin.math.floor(points.minOf(StrokePoint::y).coerceIn(0f, 1f) * size.height - 2f).toInt().coerceIn(0, size.height - 1)
            val right = ceil(points.maxOf(StrokePoint::x).coerceIn(0f, 1f) * size.width + 2f).toInt().coerceIn(left + 1, size.width)
            val bottom = ceil(points.maxOf(StrokePoint::y).coerceIn(0f, 1f) * size.height + 2f).toInt().coerceIn(top + 1, size.height)
            val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            val path = Path().apply {
                moveTo(points.first().x.coerceIn(0f, 1f) * size.width - left, points.first().y.coerceIn(0f, 1f) * size.height - top)
                points.drop(1).forEach { point ->
                    lineTo(point.x.coerceIn(0f, 1f) * size.width - left, point.y.coerceIn(0f, 1f) * size.height - top)
                }
                close()
            }
            Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            return try { fromBitmapRegion(bitmap, left, top, size.width, size.height) } finally { bitmap.recycle() }
        }

        fun fromStrokes(strokes: List<InkStroke>, pageWidth: Float, pageHeight: Float): SparseAlphaMask? {
            if (strokes.isEmpty()) return null
            val size = SelectionMaskCompositor.maskSize(pageWidth, pageHeight) ?: return null
            var result: SparseAlphaMask? = null
            strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                val radius = (stroke.width * size.width / pageWidth.coerceAtLeast(1f) / 2f).coerceAtLeast(.5f)
                val left = kotlin.math.floor(stroke.points.minOf(StrokePoint::x) * size.width - radius - 2f).toInt().coerceIn(0, size.width - 1)
                val top = kotlin.math.floor(stroke.points.minOf(StrokePoint::y) * size.height - radius - 2f).toInt().coerceIn(0, size.height - 1)
                val right = ceil(stroke.points.maxOf(StrokePoint::x) * size.width + radius + 2f).toInt().coerceIn(left + 1, size.width)
                val bottom = ceil(stroke.points.maxOf(StrokePoint::y) * size.height + radius + 2f).toInt().coerceIn(top + 1, size.height)
                val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = radius * 2f
                }
                val path = Path().apply {
                    moveTo(stroke.points.first().x * size.width - left, stroke.points.first().y * size.height - top)
                    stroke.points.drop(1).forEach { point -> lineTo(point.x * size.width - left, point.y * size.height - top) }
                }
                if (stroke.points.size == 1) Canvas(bitmap).drawCircle(pathPointX(stroke.points.first(), size.width, left), pathPointY(stroke.points.first(), size.height, top), radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                else Canvas(bitmap).drawPath(path, paint)
                val addition = try { fromBitmapRegion(bitmap, left, top, size.width, size.height) } finally { bitmap.recycle() }
                result = result?.composed(addition, AlphaMaskOperation.ADD) ?: addition
            }
            return result
        }

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

        fun full(pageWidth: Float, pageHeight: Float): SparseAlphaMask? {
            val size = SelectionMaskCompositor.maskSize(pageWidth, pageHeight) ?: return null
            val surface = MaskTileSurface(size.width, size.height, DEFAULT_TILE_SIZE)
            val columns = (size.width + surface.tileSize - 1) / surface.tileSize
            val rows = (size.height + surface.tileSize - 1) / surface.tileSize
            surface.beginWrite("select all").run {
                for (row in 0 until rows) for (column in 0 until columns) {
                    val key = PixelTileKey(column, row)
                    val tileWidth = minOf(surface.tileSize, size.width - column * surface.tileSize)
                    val tileHeight = minOf(surface.tileSize, size.height - row * surface.tileSize)
                    editTile(key, PixelRect(0, 0, tileWidth, tileHeight)) { alpha, stride ->
                        for (y in 0 until tileHeight) alpha.fill(255.toByte(), y * stride, y * stride + tileWidth)
                    }
                }
                commit().close()
            }
            return SparseAlphaMask(surface)
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

        private fun fromBitmapRegion(source: Bitmap, left: Int, top: Int, width: Int, height: Int): SparseAlphaMask? {
            val alpha = bitmapAlpha(source)
            if (alpha.none { it.toInt() != 0 }) return null
            val surface = MaskTileSurface(width, height, DEFAULT_TILE_SIZE)
            surface.beginWrite("import bounded platform selection").run {
                composeAlpha(PixelRect(left, top, left + source.width, top + source.height), alpha, mode = MaskComposeMode.ADD)
                commit().close()
            }
            return surface.takeUnless(MaskTileSurface::isEmpty)?.let(::SparseAlphaMask)
        }

        private fun pathPointX(point: StrokePoint, width: Int, left: Int) = point.x * width - left
        private fun pathPointY(point: StrokePoint, height: Int, top: Int) = point.y * height - top

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
