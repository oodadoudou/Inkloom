package app.inkloom.engine.v3.scene

import app.inkloom.document.StrokeEdit

/** One chronological undo stream for vector strokes and host-owned page elements such as fills. */
sealed interface EngineTransaction {
    val affectedLayerIds: Set<String>

    data class Strokes(val edit: StrokeEdit) : EngineTransaction {
        override val affectedLayerIds: Set<String> =
            (edit.before + edit.after).mapTo(linkedSetOf()) { it.stroke.layerId }
    }

    class External(
        val undo: () -> Unit,
        val redo: () -> Unit,
        val dispose: () -> Unit = {},
        override val affectedLayerIds: Set<String> = emptySet(),
    ) : EngineTransaction
}

class EngineTransactionJournal(private val capacity: Int = 100) {
    private val undo = ArrayDeque<EngineTransaction>()
    private val redo = ArrayDeque<EngineTransaction>()

    @Synchronized
    fun record(edit: EngineTransaction) {
        val released = mutableListOf<EngineTransaction>()
        undo.addLast(edit)
        while (undo.size > capacity.coerceAtLeast(1)) released += undo.removeFirst()
        released += redo
        redo.clear()
        released.forEach(::release)
    }

    @Synchronized
    fun takeUndo(): EngineTransaction? = undo.removeLastOrNull()?.also(redo::addLast)

    @Synchronized
    fun restoreUndo(edit: EngineTransaction) {
        if (redo.lastOrNull() === edit) redo.removeLast()
        undo.addLast(edit)
    }

    @Synchronized
    fun takeRedo(): EngineTransaction? = redo.removeLastOrNull()?.also(undo::addLast)

    @Synchronized
    fun restoreRedo(edit: EngineTransaction) {
        if (undo.lastOrNull() === edit) undo.removeLast()
        redo.addLast(edit)
    }

    @Synchronized
    fun clear() {
        // Clear ownership before invoking disposal callbacks. A callback may synchronously close
        // a raster checkpoint or re-enter editor teardown; iterating the live deque used to throw
        // ConcurrentModificationException and crash Activity destruction.
        val released = undo.toList() + redo.toList()
        undo.clear()
        redo.clear()
        released.forEach(::release)
    }

    private fun release(edit: EngineTransaction) {
        if (edit is EngineTransaction.External) edit.dispose()
    }
}
