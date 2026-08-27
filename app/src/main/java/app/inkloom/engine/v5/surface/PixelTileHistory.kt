package app.inkloom.engine.v5.surface

/**
 * Byte- and count-bounded chronological history for committed pixel tile transactions.
 *
 * Records are applied before moving between stacks, so a stale/out-of-order record cannot corrupt
 * stack order. Eviction closes records immediately, releasing their retained tile generations.
 * The latest non-empty edit is retained even when it alone exceeds [maxRetainedBytes], matching the
 * user expectation that the operation they just performed can be undone at least once.
 */
class PixelTileHistory(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxRetainedBytes: Long = DEFAULT_MAX_RETAINED_BYTES,
) : AutoCloseable {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxRetainedBytes > 0L) { "maxRetainedBytes must be positive" }
    }

    private data class Entry(
        val surface: PixelTileSurface,
        val record: PixelTileTransactionRecord,
    )

    private val undoEntries = ArrayDeque<Entry>()
    private val redoEntries = ArrayDeque<Entry>()
    private var undoBytes = 0L
    private var redoBytes = 0L

    val undoCount: Int get() = synchronized(this) { undoEntries.size }
    val redoCount: Int get() = synchronized(this) { redoEntries.size }
    val retainedByteSize: Long get() = synchronized(this) { undoBytes + redoBytes }

    /** Returns false for an empty commit, which is released rather than added to history. */
    @Synchronized
    fun record(surface: PixelTileSurface, record: PixelTileTransactionRecord): Boolean {
        require(record.surfaceId == surface.surfaceId) { "Transaction belongs to another surface" }
        record.requireOpen()
        if (record.changedTileCount == 0) {
            record.close()
            return false
        }
        surface.requireRecordCurrent(record, PixelTileSurface.HistoryDirection.UNDO)

        clearRedo()
        undoEntries.addLast(Entry(surface, record))
        undoBytes += record.retainedByteSize
        trimUndo()
        return true
    }

    /** Applies exactly one transaction and returns its diagnostic label, or null if unavailable. */
    @Synchronized
    fun undo(): String? {
        val entry = undoEntries.lastOrNull() ?: return null
        entry.surface.apply(entry.record, PixelTileSurface.HistoryDirection.UNDO)
        undoEntries.removeLast()
        undoBytes -= entry.record.retainedByteSize
        redoEntries.addLast(entry)
        redoBytes += entry.record.retainedByteSize
        return entry.record.label
    }

    /** Applies exactly one transaction and returns its diagnostic label, or null if unavailable. */
    @Synchronized
    fun redo(): String? {
        val entry = redoEntries.lastOrNull() ?: return null
        entry.surface.apply(entry.record, PixelTileSurface.HistoryDirection.REDO)
        redoEntries.removeLast()
        redoBytes -= entry.record.retainedByteSize
        undoEntries.addLast(entry)
        undoBytes += entry.record.retainedByteSize
        return entry.record.label
    }

    @Synchronized
    fun clear() {
        undoEntries.forEach { it.record.close() }
        redoEntries.forEach { it.record.close() }
        undoEntries.clear()
        redoEntries.clear()
        undoBytes = 0L
        redoBytes = 0L
    }

    override fun close() = clear()

    private fun clearRedo() {
        redoEntries.forEach { it.record.close() }
        redoEntries.clear()
        redoBytes = 0L
    }

    private fun trimUndo() {
        while (undoEntries.size > 1 &&
            (undoEntries.size > maxEntries || undoBytes > maxRetainedBytes)
        ) {
            val evicted = undoEntries.removeFirst()
            undoBytes -= evicted.record.retainedByteSize
            evicted.record.close()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 1_000
        const val DEFAULT_MAX_RETAINED_BYTES = 128L * 1024 * 1024
    }
}
