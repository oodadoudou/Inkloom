package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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

    override fun close() {
        tiles.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        tiles.clear()
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
