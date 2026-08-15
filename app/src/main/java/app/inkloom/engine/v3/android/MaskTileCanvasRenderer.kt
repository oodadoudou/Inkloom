package app.inkloom.engine.v3.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey

/** Android presentation adapter that materializes at most one mask tile at a time. */
internal object MaskTileCanvasRenderer {
    fun draw(snapshot: MaskTileSurfaceSnapshot, canvas: Canvas, destination: RectF, paint: Paint? = null) {
        val columns = (snapshot.logicalWidth + snapshot.tileSize - 1) / snapshot.tileSize
        val rows = (snapshot.logicalHeight + snapshot.tileSize - 1) / snapshot.tileSize
        val keys = if (snapshot.defaultAlpha == 0) snapshot.tileInfo.map { it.key } else buildList(columns * rows) {
            for (row in 0 until rows) for (column in 0 until columns) add(PixelTileKey(column, row))
        }
        val scaleX = destination.width() / snapshot.logicalWidth
        val scaleY = destination.height() / snapshot.logicalHeight
        keys.forEach { key ->
            val left = key.column * snapshot.tileSize
            val top = key.row * snapshot.tileSize
            val bounds = PixelRect(
                left,
                top,
                minOf(left + snapshot.tileSize, snapshot.logicalWidth),
                minOf(top + snapshot.tileSize, snapshot.logicalHeight),
            )
            val alpha = snapshot.copyAlpha(bounds)
            val pixels = IntArray(alpha.size) { index ->
                (alpha[index].toInt().and(0xff) shl 24) or (Color.WHITE and 0x00ffffff)
            }
            val tile = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
            try {
                tile.setPixels(pixels, 0, bounds.width, 0, 0, bounds.width, bounds.height)
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
}
