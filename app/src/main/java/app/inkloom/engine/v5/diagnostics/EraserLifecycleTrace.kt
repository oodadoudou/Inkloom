package app.inkloom.engine.v5.diagnostics

import java.util.concurrent.ConcurrentHashMap

/**
 * Debug/test trace for the temporary-eraser and V5 transaction boundary.
 *
 * Disabled production builds pay only the branch at each trace point. The trace intentionally
 * records lifecycle facts rather than pixels so a device report can distinguish a real commit
 * from a transient DESTINATION_OUT preview that was later canceled.
 */
internal object EraserLifecycleTrace {
    enum class Stage {
        BEGIN,
        RESTORE_DEFERRED,
        FINISH_QUEUED,
        COMMITTED,
        CANCELLED,
        RESTORE_DISPATCHED,
    }

    data class Event(
        val strokeId: String,
        val stage: Stage,
        val persistentTool: String,
        val effectiveTool: String,
        val transactionId: Long? = null,
        val touchedTiles: Int = 0,
        val beforeGeneration: Long? = null,
        val afterGeneration: Long? = null,
    )

    @Volatile var enabled: Boolean = false
    private val events = ConcurrentHashMap<String, MutableList<Event>>()

    fun record(event: Event) {
        if (!enabled) return
        events.getOrPut(event.strokeId) { mutableListOf() }.let { timeline ->
            synchronized(timeline) { timeline += event }
        }
    }

    fun timeline(strokeId: String): List<Event> = events[strokeId]?.let { timeline ->
        synchronized(timeline) { timeline.toList() }
    }.orEmpty()

    fun reset() = events.clear()
}
