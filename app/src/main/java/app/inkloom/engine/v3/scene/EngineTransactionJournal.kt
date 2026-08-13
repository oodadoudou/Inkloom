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

    fun record(edit: EngineTransaction) {
        undo.addLast(edit)
        while (undo.size > capacity.coerceAtLeast(1)) release(undo.removeFirst())
        redo.forEach(::release)
        redo.clear()
    }

    fun takeUndo(): EngineTransaction? = undo.removeLastOrNull()?.also(redo::addLast)

    fun restoreUndo(edit: EngineTransaction) {
        if (redo.lastOrNull() === edit) redo.removeLast()
        undo.addLast(edit)
    }

    fun takeRedo(): EngineTransaction? = redo.removeLastOrNull()?.also(undo::addLast)

    fun restoreRedo(edit: EngineTransaction) {
        if (undo.lastOrNull() === edit) undo.removeLast()
        redo.addLast(edit)
    }

    fun clear() {
        undo.forEach(::release)
        redo.forEach(::release)
        undo.clear()
        redo.clear()
    }

    private fun release(edit: EngineTransaction) {
        if (edit is EngineTransaction.External) edit.dispose()
    }
}
