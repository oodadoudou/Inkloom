package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*

/** A selected-pixel transfer keeps its live source transaction for atomic host layer history. */
class SelectionLayerTransfer private constructor(
    val source: PixelTileSurface,
    val destination: PixelTileSurface,
    private val sourceEdit: RasterSelectionEdit?,
) : AutoCloseable {
    fun undoSource() { sourceEdit?.let { source.apply(it.record, PixelTileSurface.HistoryDirection.UNDO) } }
    fun redoSource() { sourceEdit?.let { source.apply(it.record, PixelTileSurface.HistoryDirection.REDO) } }
    override fun close() { sourceEdit?.record?.close() }

    companion object {
        fun create(source: PixelTileSurface, selection: MaskTileSurfaceSnapshot, cut: Boolean): SelectionLayerTransfer? {
            val content = RasterSelectionContent.copy(source.snapshot(), selection) ?: return null
            val destination = PixelTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize,
                pixelFormat = source.pixelFormat)
            val pasted = RasterSelectionContent.paste(destination, content, content.sourceLeft, content.sourceTop) ?: return null
            if (pasted.record.changedTileCount == 0) { pasted.record.close(); return null }
            pasted.record.close()
            val sourceEdit = if (cut) RasterSelectionContent.clear(source, selection) else null
            return SelectionLayerTransfer(source, destination, sourceEdit)
        }
    }
}
