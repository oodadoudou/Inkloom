package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskTileTransactionRecord
import app.inkloom.engine.v5.surface.PixelRect

data class LayerMaskEdit(
    val snapshot: MaskTileSurfaceSnapshot,
    val dirtyBounds: PixelRect,
    val historyByteSize: Long,
)

data class LayerMaskThumbnail(val width: Int, val height: Int, val alpha: ByteArray)

/** Document-layer-owned editing runtime built on the same sparse alpha truth as Selection. */
class LayerMaskRuntime(
    private val surface: MaskTileSurface,
    enabled: Boolean = true,
    private val maxHistoryByteSize: Long = DEFAULT_HISTORY_BYTE_BUDGET,
    private val maxUndoDepth: Int = DEFAULT_UNDO_DEPTH,
) : AutoCloseable {
    init {
        require(maxHistoryByteSize > 0L)
        require(maxUndoDepth > 0)
    }

    private val undo = ArrayDeque<MaskTileTransactionRecord>()
    private val redo = ArrayDeque<MaskTileTransactionRecord>()
    private var undoBytes = 0L
    private var redoBytes = 0L
    var enabled: Boolean = enabled
        private set

    val snapshot: MaskTileSurfaceSnapshot get() = surface.snapshot()
    val historyByteSize: Long get() = undoBytes + redoBytes

    fun paint(bounds: PixelRect, alpha: ByteArray, rowStride: Int = bounds.width): LayerMaskEdit? =
        edit("paint layer mask") { composeAlpha(bounds, alpha, rowStride, MaskComposeMode.ADD) }

    fun erase(bounds: PixelRect, alpha: ByteArray, rowStride: Int = bounds.width): LayerMaskEdit? =
        edit("erase layer mask") { composeAlpha(bounds, alpha, rowStride, MaskComposeMode.SUBTRACT) }

    fun replace(bounds: PixelRect, alpha: ByteArray, rowStride: Int = bounds.width): LayerMaskEdit? =
        edit("replace layer mask region") { composeAlpha(bounds, alpha, rowStride, MaskComposeMode.REPLACE) }

    fun fill(alpha: Int): LayerMaskEdit? = edit("fill layer mask") { fill(alpha) }

    fun invert(): LayerMaskEdit? = edit("invert layer mask") { invert() }

    fun replaceFromSelection(selection: MaskTileSurfaceSnapshot, inverted: Boolean = false): LayerMaskEdit? {
        require(selection.logicalWidth == surface.logicalWidth && selection.logicalHeight == surface.logicalHeight) {
            "Selection and layer mask geometry must match"
        }
        return edit("selection to layer mask") {
            composeSurface(selection, MaskComposeMode.REPLACE)
            if (inverted) invert()
        }
    }

    fun revealSelection(selection: MaskTileSurfaceSnapshot): LayerMaskEdit? = composeSelection(
        selection,
        MaskComposeMode.ADD,
        "reveal selection in layer mask",
    )

    fun hideSelection(selection: MaskTileSurfaceSnapshot): LayerMaskEdit? = composeSelection(
        selection,
        MaskComposeMode.SUBTRACT,
        "hide selection in layer mask",
    )

    fun copyToSelection(): MaskTileSurfaceSnapshot = surface.snapshot()

    fun undo(): LayerMaskEdit? {
        val record = undo.removeLastOrNull() ?: return null
        undoBytes -= record.retainedByteSize
        surface.apply(record, MaskTileSurface.HistoryDirection.UNDO)
        redo.addLast(record)
        redoBytes += record.retainedByteSize
        return LayerMaskEdit(surface.snapshot(), fullDirty(record), historyByteSize)
    }

    fun redo(): LayerMaskEdit? {
        val record = redo.removeLastOrNull() ?: return null
        redoBytes -= record.retainedByteSize
        surface.apply(record, MaskTileSurface.HistoryDirection.REDO)
        undo.addLast(record)
        undoBytes += record.retainedByteSize
        return LayerMaskEdit(surface.snapshot(), fullDirty(record), historyByteSize)
    }

    fun thumbnail(maxWidth: Int = 128, maxHeight: Int = 128): LayerMaskThumbnail {
        require(maxWidth > 0 && maxHeight > 0)
        val scale = minOf(maxWidth.toFloat() / surface.logicalWidth, maxHeight.toFloat() / surface.logicalHeight, 1f)
        val width = (surface.logicalWidth * scale).toInt().coerceAtLeast(1)
        val height = (surface.logicalHeight * scale).toInt().coerceAtLeast(1)
        val source = surface.snapshot()
        val alpha = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = ((x + .5f) * source.logicalWidth / width).toInt().coerceIn(0, source.logicalWidth - 1)
            val sourceY = ((y + .5f) * source.logicalHeight / height).toInt().coerceIn(0, source.logicalHeight - 1)
            alpha[y * width + x] = source.alphaAt(sourceX, sourceY).toByte()
        }
        return LayerMaskThumbnail(width, height, alpha)
    }

    override fun close() {
        (undo + redo).forEach(MaskTileTransactionRecord::close)
        undo.clear()
        redo.clear()
        undoBytes = 0L
        redoBytes = 0L
    }

    private inline fun edit(
        label: String,
        block: app.inkloom.engine.v5.surface.MaskTileWriteTransaction.() -> Unit,
    ): LayerMaskEdit? {
        val transaction = surface.beginWrite(label)
        return try {
            transaction.block()
            val record = transaction.commit()
            val changed = record.changedTileCount > 0 || record.beforeDefaultAlpha != record.afterDefaultAlpha
            if (!changed) {
                record.close()
                null
            } else {
                clearRedo()
                undo.addLast(record)
                undoBytes += record.retainedByteSize
                trimUndoHistory()
                LayerMaskEdit(surface.snapshot(), fullDirty(record), historyByteSize)
            }
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
    }

    private fun composeSelection(
        selection: MaskTileSurfaceSnapshot,
        mode: MaskComposeMode,
        label: String,
    ): LayerMaskEdit? {
        require(selection.logicalWidth == surface.logicalWidth && selection.logicalHeight == surface.logicalHeight) {
            "Selection and layer mask geometry must match"
        }
        return edit(label) { composeSurface(selection, mode) }
    }

    private fun fullDirty(record: MaskTileTransactionRecord): PixelRect {
        if (record.beforeDefaultAlpha != record.afterDefaultAlpha) {
            return PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        }
        return record.deltaInfo.map { info ->
            val left = info.key.column * surface.tileSize + info.dirtyLocalRect.left
            val top = info.key.row * surface.tileSize + info.dirtyLocalRect.top
            PixelRect(
                left,
                top,
                minOf(surface.logicalWidth, info.key.column * surface.tileSize + info.dirtyLocalRect.right),
                minOf(surface.logicalHeight, info.key.row * surface.tileSize + info.dirtyLocalRect.bottom),
            )
        }.reduceOrNull(PixelRect::union) ?: PixelRect(0, 0, 0, 0)
    }

    private fun clearRedo() {
        redo.forEach(MaskTileTransactionRecord::close)
        redo.clear()
        redoBytes = 0L
    }

    private fun trimUndoHistory() {
        while (undo.size > maxUndoDepth || undoBytes > maxHistoryByteSize) {
            val evicted = undo.removeFirstOrNull() ?: break
            undoBytes -= evicted.retainedByteSize
            evicted.close()
        }
    }

    companion object {
        const val DEFAULT_HISTORY_BYTE_BUDGET = 32L * 1024 * 1024
        const val DEFAULT_UNDO_DEPTH = 128
    }
}
