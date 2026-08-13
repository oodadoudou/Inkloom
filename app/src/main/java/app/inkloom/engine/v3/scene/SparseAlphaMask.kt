package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.document.StrokeTileKey

/**
 * Authoritative sparse alpha plane for selections and layer masks. Only tiles containing non-zero
 * alpha are retained. Full bitmaps are explicit snapshots for legacy CPU operations/export and
 * are never the source of truth.
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
            val row = IntArray(tileSize)
            var top = 0
            while (top < source.height) {
                var left = 0
                val tileHeight = minOf(tileSize, source.height - top)
                while (left < source.width) {
                    val tileWidth = minOf(tileSize, source.width - left)
                    var nonEmpty = false
                    for (y in 0 until tileHeight) {
                        source.getPixels(row, 0, tileWidth, left, top + y, tileWidth, 1)
                        var x = 0
                        while (x < tileWidth && Color.alpha(row[x]) == 0) x++
                        if (x < tileWidth) {
                            nonEmpty = true
                            break
                        }
                    }
                    if (nonEmpty) {
                        retained[StrokeTileKey(left / tileSize, top / tileSize)] =
                            Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
                    }
                    left += tileSize
                }
                top += tileSize
            }
            return SparseAlphaMask(source.width, source.height, tileSize, retained).takeUnless(SparseAlphaMask::isEmpty)
        }
    }
}
