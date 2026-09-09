package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.inkloom.document.PageElement
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.*

internal class MixedSelectionSource(val elements: List<PageElement>, val bitmap: Bitmap, val release: () -> Unit = {})

/** Reversible rasterization precedes the transform, so partial image selections retain their outside pixels. */
internal class MixedSelectionDraft(val source: MixedSelectionSource, val surface: PixelTileSurface) : AutoCloseable {
    val bounds=PixelRect(0,0,surface.logicalWidth,surface.logicalHeight)
    val record: PixelTileTransactionRecord
    init {
        val rendered=PixelTileBitmapAdapter.render(surface.snapshot())
        val merged=if(rendered.isMutable) rendered else try {
            requireNotNull(rendered.copy(requireNotNull(rendered.config),true))
        } finally { rendered.recycle() }
        try {
            Canvas(merged).drawBitmap(source.bitmap,null,Rect(0,0,merged.width,merged.height),Paint(Paint.FILTER_BITMAP_FLAG))
            val snapshot=PixelTileBitmapAdapter.import(merged,surface.tileSize).snapshot()
            surface.beginWrite("rasterize mixed selection source").use { write ->
                for(key in snapshot.tileKeys + surface.snapshot().tileKeys) {
                    val l=key.column*surface.tileSize; val t=key.row*surface.tileSize
                    val b=PixelRect(l,t,minOf(l+surface.tileSize,surface.logicalWidth),minOf(t+surface.tileSize,surface.logicalHeight))
                    val local=PixelRect(0,0,b.width,b.height)
                    if(surface.pixelFormat.isHighPrecision) {
                        val pixels=snapshot.copyPremultipliedF16(b)
                        write.editTileF16(key,local) { out,stride -> for(y in 0 until b.height) pixels.copyInto(out,y*stride,y*b.width,(y+1)*b.width) }
                    } else write.replacePremultipliedPixels(b,snapshot.copyPremultipliedPixels(b))
                }
                record=write.commit()
            }
        } finally { merged.recycle() }
    }
    fun undo() = surface.apply(record,PixelTileSurface.HistoryDirection.UNDO)
    fun redo() = surface.apply(record,PixelTileSurface.HistoryDirection.REDO)
    override fun close() { record.close(); source.bitmap.recycle(); source.release() }
}
