package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

/** Lazy first-write backup for legacy Bitmap tools during sparse-surface migration. */
internal class BitmapTileMemento(
    private val source: Bitmap,
    private val tileSize: Int = 128,
) : AutoCloseable {
    private data class SavedTile(val left: Int, val top: Int, val bitmap: Bitmap)

    private val saved = linkedMapOf<Long, SavedTile>()
    private val restorePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    val retainedByteSize: Long
        get() = saved.values.sumOf { it.bitmap.allocationByteCount.toLong() }

    fun capture(bounds: Rect) {
        if (source.isRecycled || bounds.isEmpty) return
        val clipped = Rect(bounds).apply { intersect(0, 0, source.width, source.height) }
        if (clipped.isEmpty) return
        val firstColumn = clipped.left / tileSize
        val lastColumn = (clipped.right - 1) / tileSize
        val firstRow = clipped.top / tileSize
        val lastRow = (clipped.bottom - 1) / tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = (row.toLong() shl 32) or (column.toLong() and 0xffffffffL)
            if (key in saved) continue
            val left = column * tileSize
            val top = row * tileSize
            val width = minOf(tileSize, source.width - left)
            val height = minOf(tileSize, source.height - top)
            saved[key] = SavedTile(
                left,
                top,
                Bitmap.createBitmap(source, left, top, width, height),
            )
        }
    }

    fun restore(canvas: Canvas) {
        saved.values.forEach { tile ->
            canvas.drawBitmap(tile.bitmap, tile.left.toFloat(), tile.top.toFloat(), restorePaint)
        }
    }

    /** Overlays the gesture-start pixels for [documentRegion] into a region-local scratch canvas. */
    fun drawOriginalRegion(canvas: Canvas, documentRegion: Rect) {
        if (documentRegion.isEmpty) return
        saved.values.forEach { tile ->
            val overlap = Rect(
                maxOf(tile.left, documentRegion.left),
                maxOf(tile.top, documentRegion.top),
                minOf(tile.left + tile.bitmap.width, documentRegion.right),
                minOf(tile.top + tile.bitmap.height, documentRegion.bottom),
            )
            if (overlap.isEmpty) return@forEach
            val sourceRect = Rect(
                overlap.left - tile.left,
                overlap.top - tile.top,
                overlap.right - tile.left,
                overlap.bottom - tile.top,
            )
            val destinationRect = Rect(
                overlap.left - documentRegion.left,
                overlap.top - documentRegion.top,
                overlap.right - documentRegion.left,
                overlap.bottom - documentRegion.top,
            )
            canvas.drawBitmap(tile.bitmap, sourceRect, destinationRect, restorePaint)
        }
    }

    override fun close() {
        saved.values.forEach { tile -> tile.bitmap.recycle() }
        saved.clear()
    }
}
