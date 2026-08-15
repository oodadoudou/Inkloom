package app.inkloom.engine.v3.scene

import app.inkloom.engine.v3.surface.MaskTileSurface
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v3.surface.MaskComposeMode

data class DocumentPageKey(val documentId: String, val pageId: String) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        require(pageId.isNotBlank()) { "pageId must not be blank" }
    }
}

/**
 * Runtime-owned selection state for one document page.
 *
 * The UI receives this state by reference. It may initiate transactions, but it never owns or
 * replaces mask pixels independently of the document runtime.
 */
class DocumentSelectionState internal constructor(
    val key: DocumentPageKey,
    logicalWidth: Int,
    logicalHeight: Int,
    tileSize: Int,
) {
    private val monitor = Any()
    private var reselectSnapshot: MaskTileSurfaceSnapshot? = null
    private var surfaceValue = MaskTileSurface(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        tileSize = tileSize,
        surfaceId = "selection:${key.documentId}:${key.pageId}",
    )

    val logicalWidth: Int get() = synchronized(monitor) { surfaceValue.logicalWidth }
    val logicalHeight: Int get() = synchronized(monitor) { surfaceValue.logicalHeight }
    val isEmpty: Boolean get() = synchronized(monitor) { surfaceValue.isEmpty }
    val revision: Long get() = synchronized(monitor) { surfaceValue.revision }
    val retainedByteSize: Long get() = synchronized(monitor) { surfaceValue.retainedByteSize }

    fun surface(): MaskTileSurface = synchronized(monitor) { surfaceValue }
    fun snapshot(): MaskTileSurfaceSnapshot = synchronized(monitor) { surfaceValue.snapshot() }

    /** Selection history is document state too; the View must not retain deselected alpha. */
    internal fun captureForReselect() = synchronized(monitor) {
        reselectSnapshot = surfaceValue.snapshot().takeUnless(MaskTileSurfaceSnapshot::isEmpty)
    }

    internal fun restoreReselect(): Boolean = synchronized(monitor) {
        val saved = reselectSnapshot ?: return@synchronized false
        replaceFromLocked(saved)
        true
    }

    internal fun discardReselect() = synchronized(monitor) { reselectSnapshot = null }

    /** Replaces alpha through the stable document-owned surface rather than swapping UI storage. */
    internal fun replaceFrom(source: MaskTileSurfaceSnapshot?) = synchronized(monitor) {
        replaceFromLocked(source)
    }

    private fun replaceFromLocked(source: MaskTileSurfaceSnapshot?) {
        if (source != null) require(source.logicalWidth == surfaceValue.logicalWidth && source.logicalHeight == surfaceValue.logicalHeight) {
            "Replacement selection geometry does not match ${surfaceValue.logicalWidth}x${surfaceValue.logicalHeight}"
        }
        surfaceValue.beginWrite("replace document selection").run {
            clear()
            if (source != null && !source.isEmpty) composeSurface(source, MaskComposeMode.ADD)
            commit().close()
        }
    }

    internal fun clear() = synchronized(monitor) {
        if (!surfaceValue.isEmpty) surfaceValue.beginWrite("document deselect").run { clear(); commit().close() }
    }
}

/** Document-lifetime owner for transient per-page selections. */
class DocumentSelectionRuntime(
    private val tileSize: Int = MaskTileSurface.DEFAULT_TILE_SIZE,
) {
    private val states = linkedMapOf<DocumentPageKey, DocumentSelectionState>()

    @Synchronized
    fun stateFor(
        documentId: String,
        pageId: String,
        logicalWidth: Int,
        logicalHeight: Int,
    ): DocumentSelectionState {
        val key = DocumentPageKey(documentId, pageId)
        val existing = states[key]
        if (existing != null && existing.logicalWidth == logicalWidth && existing.logicalHeight == logicalHeight) return existing
        return DocumentSelectionState(key, logicalWidth, logicalHeight, tileSize).also { states[key] = it }
    }

    @Synchronized
    fun clearPage(documentId: String, pageId: String) {
        states[DocumentPageKey(documentId, pageId)]?.clear()
    }

    @Synchronized
    fun releasePage(documentId: String, pageId: String) {
        states.remove(DocumentPageKey(documentId, pageId))
    }

    @Synchronized
    fun releaseDocument(documentId: String) {
        states.keys.removeAll { it.documentId == documentId }
    }

    @Synchronized
    fun clear() = states.clear()

    @Synchronized
    fun retainedByteSize(): Long = states.values.sumOf(DocumentSelectionState::retainedByteSize)

    @Synchronized
    fun pageStateCount(): Int = states.size
}
