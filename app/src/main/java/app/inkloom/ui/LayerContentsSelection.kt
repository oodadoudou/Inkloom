package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import app.inkloom.engine.v5.surface.*

/** Bounded alpha sampling: a sparse 8K page must not allocate a 256 MB page bitmap. */
internal object LayerContentsSelection {
    fun sample(width: Int, height: Int, regions: List<PixelRect>, draw: (Canvas) -> Unit): MaskTileSurfaceSnapshot {
        val size = 256
        val page = PixelRect(0, 0, width, height)
        val keys = linkedSetOf<PixelTileKey>()
        regions.forEach { region ->
            val clipped = region.intersect(page)
            if (!clipped.isEmpty) for (row in clipped.top / size..(clipped.bottom - 1) / size)
                for (column in clipped.left / size..(clipped.right - 1) / size) keys += PixelTileKey(column, row)
        }
        val mask = MaskTileSurface(width, height)
        if (keys.isEmpty()) return mask.snapshot()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val alpha = ByteArray(size * size)
        try {
            mask.beginWrite("select layer contents").use { write ->
                keys.forEach { key ->
                    val left = key.column * size; val top = key.row * size
                    val bounds = PixelRect(left, top, minOf(left + size, width), minOf(top + size, height))
                    bitmap.eraseColor(0)
                    val canvas = Canvas(bitmap)
                    canvas.clipRect(0, 0, bounds.width, bounds.height)
                    canvas.translate(-left.toFloat(), -top.toFloat())
                    draw(canvas)
                    bitmap.getPixels(pixels, 0, size, 0, 0, bounds.width, bounds.height)
                    for (y in 0 until bounds.height) for (x in 0 until bounds.width) {
                        alpha[y * size + x] = (pixels[y * size + x] ushr 24).toByte()
                    }
                    write.replaceAlpha(bounds, alpha, size)
                }
                write.commit().close()
            }
        } finally { bitmap.recycle() }
        return mask.snapshot()
    }
}
