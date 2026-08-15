package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import app.inkloom.document.StrokeTileKey
import app.inkloom.engine.v3.tools.NativeRasterOps

/**
 * Authoritative sparse alpha plane for selections and layer masks. Only tiles containing non-zero
 * alpha are retained. Full bitmaps are explicit boundary snapshots for platform Canvas/export
 * consumers and are never the source of truth.
 */
internal class SparseAlphaMask private constructor(
    val width: Int,
    val height: Int,
    private val tileSize: Int,
    private val tiles: MutableMap<StrokeTileKey, Bitmap>,
) : AutoCloseable {
    val isEmpty: Boolean get() = tiles.isEmpty()
    val tileCount: Int get() = tiles.size
    val retainedPixels: Long get() = tiles.values.sumOf { it.width.toLong() * it.height }

    fun snapshot(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
        val canvas = Canvas(output)
        tiles.forEach { (key, tile) ->
            canvas.drawBitmap(tile, (key.column * tileSize).toFloat(), (key.row * tileSize).toFloat(), null)
        }
    }

    fun draw(canvas: Canvas, destination: RectF, paint: Paint? = null) {
        if (tiles.isEmpty()) return
        val scaleX = destination.width() / width.coerceAtLeast(1)
        val scaleY = destination.height() / height.coerceAtLeast(1)
        tiles.forEach { (key, tile) ->
            val left = key.column * tileSize
            val top = key.row * tileSize
            canvas.drawBitmap(
                tile,
                null,
                RectF(
                    destination.left + left * scaleX,
                    destination.top + top * scaleY,
                    destination.left + (left + tile.width) * scaleX,
                    destination.top + (top + tile.height) * scaleY,
                ),
                paint,
            )
        }
    }

    fun copy(): SparseAlphaMask = SparseAlphaMask(
        width,
        height,
        tileSize,
        tiles.mapValuesTo(linkedMapOf()) { (_, bitmap) -> bitmap.copy(Bitmap.Config.ARGB_8888, true) },
    )

    /** Composes a full-coordinate region directly into sparse tiles without a page snapshot. */
    fun composed(region: Bitmap?, operation: AlphaMaskOperation): SparseAlphaMask? {
        if (operation == AlphaMaskOperation.REPLACE) return fromBitmap(region, tileSize)
        if (region == null || region.isRecycled) {
            return if (operation == AlphaMaskOperation.INTERSECT) null else copy()
        }
        require(region.width == width && region.height == height) {
            "Alpha region and sparse mask dimensions must match"
        }
        val result = copy()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            blendMode = when (operation) {
                AlphaMaskOperation.ADD -> BlendMode.SRC_OVER
                AlphaMaskOperation.SUBTRACT -> BlendMode.DST_OUT
                AlphaMaskOperation.INTERSECT -> BlendMode.DST_IN
                AlphaMaskOperation.REPLACE -> error("handled above")
            }
        }
        when (operation) {
            AlphaMaskOperation.ADD -> {
                val coordinates = NativeRasterOps.nonEmptyAlphaTiles(region, tileSize)
                var index = 0
                while (index < coordinates.size) {
                    val key = StrokeTileKey(coordinates[index], coordinates[index + 1])
                    result.drawRegionIntoTile(region, key, paint, create = true)
                    index += 2
                }
            }
            AlphaMaskOperation.SUBTRACT, AlphaMaskOperation.INTERSECT -> {
                result.tiles.keys.toList().forEach { key ->
                    result.drawRegionIntoTile(region, key, paint, create = false)
                    result.tiles[key]?.let { tile ->
                        if (NativeRasterOps.isAlphaEmpty(tile)) {
                            tile.recycle()
                            result.tiles.remove(key)
                        }
                    }
                }
            }
            AlphaMaskOperation.REPLACE -> Unit
        }
        paint.blendMode = null
        return result.takeUnless(SparseAlphaMask::isEmpty) ?: run { result.close(); null }
    }

    /** Complements every logical alpha tile without allocating a page-sized source/destination. */
    fun inverted(): SparseAlphaMask {
        val inverted = linkedMapOf<StrokeTileKey, Bitmap>()
        val columns = (width + tileSize - 1) / tileSize
        val rows = (height + tileSize - 1) / tileSize
        for (row in 0 until rows) for (column in 0 until columns) {
            val key = StrokeTileKey(column, row)
            val tileWidth = minOf(tileSize, width - column * tileSize)
            val tileHeight = minOf(tileSize, height - row * tileSize)
            val source = tiles[key] ?: Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
            val destination = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
            try {
                // A false result means the inverted tile is fully transparent (the source tile
                // was fully selected), not that JNI failed.
                NativeRasterOps.invertAlpha(source, destination)
            } finally {
                if (tiles[key] == null) source.recycle()
            }
            if (!NativeRasterOps.isAlphaEmpty(destination)) inverted[key] = destination else destination.recycle()
        }
        return SparseAlphaMask(width, height, tileSize, inverted)
    }

    /**
     * Binary dilation/erosion over connected dirty regions plus the required source halo. No
     * page-sized bitmap is created for a local selection. Disjoint regions are processed
     * independently unless their halos overlap and can influence the same output pixels.
     */
    fun morphed(radius: Int, dilate: Boolean): SparseAlphaMask? {
        if (radius <= 0) return copy()
        if (tiles.isEmpty()) return null
        val logical = Rect(0, 0, width, height)
        val operationRegions = mergeTouchingRects(tiles.keys.map { key ->
            tileRect(key).apply { inset(-radius, -radius); intersect(logical) }
        })
        val outputTiles = linkedMapOf<StrokeTileKey, Bitmap>()
        operationRegions.forEach { region ->
            val source = snapshotRegion(region)
            val destination = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888)
            try {
                NativeRasterOps.morphAlpha(source, destination, radius, dilate)
                importRegion(destination, region.left, region.top).forEach { (key, tile) ->
                    val existing = outputTiles[key]
                    if (existing == null) {
                        outputTiles[key] = tile
                    } else {
                        Canvas(existing).drawBitmap(tile, 0f, 0f, Paint().apply { blendMode = BlendMode.SRC_OVER })
                        tile.recycle()
                    }
                }
            } finally {
                source.recycle()
                destination.recycle()
            }
        }
        return SparseAlphaMask(width, height, tileSize, outputTiles)
            .takeUnless(SparseAlphaMask::isEmpty)
    }

    override fun close() {
        tiles.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        tiles.clear()
    }

    private fun drawRegionIntoTile(region: Bitmap, key: StrokeTileKey, paint: Paint, create: Boolean) {
        val left = key.column * tileSize
        val top = key.row * tileSize
        val tileWidth = minOf(tileSize, width - left)
        val tileHeight = minOf(tileSize, height - top)
        val tile = tiles[key] ?: if (create) {
            Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888).also { tiles[key] = it }
        } else return
        Canvas(tile).drawBitmap(
            region,
            Rect(left, top, left + tileWidth, top + tileHeight),
            Rect(0, 0, tileWidth, tileHeight),
            paint,
        )
    }

    private fun snapshotRegion(region: Rect): Bitmap =
        Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            tiles.forEach { (key, tile) ->
                val tileBounds = tileRect(key)
                if (!Rect.intersects(region, tileBounds)) return@forEach
                canvas.drawBitmap(tile, (tileBounds.left - region.left).toFloat(), (tileBounds.top - region.top).toFloat(), null)
            }
        }

    /** Splits a region-sized result back into document-coordinate sparse tiles. */
    private fun importRegion(source: Bitmap, globalLeft: Int, globalTop: Int): Map<StrokeTileKey, Bitmap> {
        val result = linkedMapOf<StrokeTileKey, Bitmap>()
        val region = Rect(globalLeft, globalTop, globalLeft + source.width, globalTop + source.height)
        val firstColumn = region.left / tileSize
        val lastColumn = (region.right - 1) / tileSize
        val firstRow = region.top / tileSize
        val lastRow = (region.bottom - 1) / tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = StrokeTileKey(column, row)
            val tileBounds = tileRect(key)
            val overlap = Rect(tileBounds)
            if (!overlap.intersect(region)) continue
            val tile = Bitmap.createBitmap(tileBounds.width(), tileBounds.height(), Bitmap.Config.ARGB_8888)
            Canvas(tile).drawBitmap(
                source,
                Rect(
                    overlap.left - region.left,
                    overlap.top - region.top,
                    overlap.right - region.left,
                    overlap.bottom - region.top,
                ),
                Rect(
                    overlap.left - tileBounds.left,
                    overlap.top - tileBounds.top,
                    overlap.right - tileBounds.left,
                    overlap.bottom - tileBounds.top,
                ),
                null,
            )
            if (!NativeRasterOps.isAlphaEmpty(tile)) result[key] = tile else tile.recycle()
        }
        return result
    }

    private fun tileRect(key: StrokeTileKey): Rect {
        val left = key.column * tileSize
        val top = key.row * tileSize
        return Rect(left, top, minOf(left + tileSize, width), minOf(top + tileSize, height))
    }

    private fun mergeTouchingRects(input: List<Rect>): List<Rect> {
        val merged = mutableListOf<Rect>()
        input.filterNot(Rect::isEmpty).forEach { candidateValue ->
            var candidate = Rect(candidateValue)
            var index = 0
            while (index < merged.size) {
                val existing = merged[index]
                val touches = candidate.left <= existing.right && candidate.right >= existing.left &&
                    candidate.top <= existing.bottom && candidate.bottom >= existing.top
                if (touches) {
                    candidate.union(existing)
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
            require(tileSize > 0)
            val retained = linkedMapOf<StrokeTileKey, Bitmap>()
            val coordinates = NativeRasterOps.nonEmptyAlphaTiles(source, tileSize)
            check(coordinates.size % 2 == 0) { "Native alpha tile scan returned malformed coordinates" }
            var index = 0
            while (index < coordinates.size) {
                val column = coordinates[index]
                val row = coordinates[index + 1]
                val left = column * tileSize
                val top = row * tileSize
                val tileWidth = minOf(tileSize, source.width - left)
                val tileHeight = minOf(tileSize, source.height - top)
                retained[StrokeTileKey(column, row)] =
                    Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
                index += 2
            }
            return SparseAlphaMask(source.width, source.height, tileSize, retained).takeUnless(SparseAlphaMask::isEmpty)
        }
    }
}

internal enum class AlphaMaskOperation { REPLACE, ADD, SUBTRACT, INTERSECT }
