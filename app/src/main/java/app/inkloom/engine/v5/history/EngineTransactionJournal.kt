package app.inkloom.engine.v5.history

/** One chronological undo entry over authoritative V5 document state. */
class EngineTransaction(
    val undo: () -> Unit,
    val redo: () -> Unit,
    val dispose: () -> Unit = {},
)

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
    fun takeRedo(): EngineTransaction? = redo.removeLastOrNull()?.also(undo::addLast)

    /** Undo and redo steps currently held. */
    val size: Int @Synchronized get() = undo.size + redo.size

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

    private fun release(edit: EngineTransaction) = edit.dispose()
}
