package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.surface.*

/** Worker-owned adapter. The baseline is captured when DOWN reaches the worker, after prior UP. */
internal class LayerMaskBrushCoverage(private val mask: MaskTileSurface, private val hiding: Boolean) {
    private val original by lazy { mask.snapshot() }
    private val transaction by lazy { original; mask.beginWrite(if (hiding) "erase layer mask" else "paint layer mask") }
    val preview get() = transaction.previewSnapshot()
    fun finish(snapshot: PixelTileSurfaceSnapshot?, dirty: PixelRect?): MaskTileTransactionRecord {
        project(snapshot, dirty)
        return transaction.commit()
    }
    fun cancel() { transaction.cancel() }
    fun project(snapshot: PixelTileSurfaceSnapshot?, dirty: PixelRect?) {
        if (snapshot == null || dirty == null || dirty.isEmpty) return
        val tileSize = original.tileSize
        for (row in dirty.top / tileSize..(dirty.bottom - 1) / tileSize) {
            for (column in dirty.left / tileSize..(dirty.right - 1) / tileSize) {
                val key = PixelTileKey(column, row)
                val left = column * tileSize
                val top = row * tileSize
                val roi = PixelRect(maxOf(left, dirty.left), maxOf(top, dirty.top),
                    minOf(left + tileSize, dirty.right, original.logicalWidth),
                    minOf(top + tileSize, dirty.bottom, original.logicalHeight))
                if (roi.isEmpty) continue
                snapshot.readRgba8Tile(key) { rgba ->
                    val alpha = original.copyAlpha(roi)
                    for (y in roi.top until roi.bottom) for (x in roi.left until roi.right) {
                        val index = (y - roi.top) * roi.width + x - roi.left
                        val before = alpha[index].toInt() and 255
                        val amount = rgba[(y - top) * tileSize + x - left] ushr 24
                        // Coverage is cumulative within a contact. Always apply it to the original
                        // mask, otherwise every preview would repeatedly darken the same pixels.
                        alpha[index] = (if (hiding) (before * (255 - amount) + 127) / 255
                            else before + ((255 - before) * amount + 127) / 255).toByte()
                    }
                    transaction.composeAlpha(roi, alpha, mode = MaskComposeMode.REPLACE)
                }
            }
        }
    }
}
