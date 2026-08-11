package app.inkloom.document

/** One chronological undo stream for vector strokes and host-owned page elements such as fills. */
sealed interface CanvasEdit {
    data class Strokes(val edit: StrokeEdit) : CanvasEdit

    class External(
        val undo: () -> Unit,
        val redo: () -> Unit,
        val dispose: () -> Unit = {},
    ) : CanvasEdit
}

class CanvasEditHistory(private val capacity: Int = 100) {
    private val undo = ArrayDeque<CanvasEdit>()
    private val redo = ArrayDeque<CanvasEdit>()

    fun record(edit: CanvasEdit) {
        undo.addLast(edit)
        while (undo.size > capacity.coerceAtLeast(1)) release(undo.removeFirst())
        redo.forEach(::release)
        redo.clear()
    }

    fun takeUndo(): CanvasEdit? = undo.removeLastOrNull()?.also(redo::addLast)

    fun restoreUndo(edit: CanvasEdit) {
        if (redo.lastOrNull() === edit) redo.removeLast()
        undo.addLast(edit)
    }

    fun takeRedo(): CanvasEdit? = redo.removeLastOrNull()?.also(undo::addLast)

    fun restoreRedo(edit: CanvasEdit) {
        if (undo.lastOrNull() === edit) undo.removeLast()
        redo.addLast(edit)
    }

    fun clear() {
        undo.forEach(::release)
        redo.forEach(::release)
        undo.clear()
        redo.clear()
    }

    private fun release(edit: CanvasEdit) {
        if (edit is CanvasEdit.External) edit.dispose()
    }
}
