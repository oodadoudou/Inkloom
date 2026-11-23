package app.inkloom.engine.v5.android

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

enum class PixelMemoryPressure { UI_HIDDEN, MODERATE, CRITICAL }

data class PixelMemoryTrimResult(
    val releasedCpuBytes: Long = 0L,
    val releasedGpuBytes: Long = 0L,
)

interface PixelMemoryTrimmable {
    /** Presentation owners run before decoded resources retained by their display lists. */
    val memoryTrimPriority: Int get() = 50
    fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult
}

/** Process-wide dispatcher for rebuildable V5 presentation cache memory. Document truth is never registered. */
object PixelMemoryPressureCoordinator {
    private data class Entry(
        val id: Long,
        val owner: WeakReference<PixelMemoryTrimmable>,
    )

    data class Summary(
        val pressure: PixelMemoryPressure,
        val ownerCount: Int,
        val failedOwnerCount: Int,
        val releasedCpuBytes: Long,
        val releasedGpuBytes: Long,
    )

    class Registration constructor(private val id: Long) : AutoCloseable {
        override fun close() = PixelMemoryPressureCoordinator.unregister(id)
    }

    private val nextId = AtomicLong()
    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun register(owner: PixelMemoryTrimmable): Registration {
        removeCollectedOwners()
        val id = nextId.incrementAndGet()
        entries += Entry(id, WeakReference(owner))
        return Registration(id)
    }

    fun onTrimMemory(level: Int): Summary? = pressureForTrimLevel(level)?.let(::trim)

    fun trim(pressure: PixelMemoryPressure): Summary {
        val owners = synchronized(this) {
            removeCollectedOwners()
            entries.mapNotNull { entry -> entry.owner.get()?.let { entry.id to it } }
                .sortedWith(compareBy<Pair<Long, PixelMemoryTrimmable>> { it.second.memoryTrimPriority }.thenBy { it.first })
        }
        var failures = 0
        var cpuBytes = 0L
        var gpuBytes = 0L
        owners.forEach { (_, owner) ->
            runCatching { owner.trimPixelMemory(pressure) }
                .onSuccess { result ->
                    cpuBytes += result.releasedCpuBytes.coerceAtLeast(0L)
                    gpuBytes += result.releasedGpuBytes.coerceAtLeast(0L)
                }
                .onFailure { failures++ }
        }
        return Summary(pressure, owners.size, failures, cpuBytes, gpuBytes)
    }

    @Synchronized
    private fun removeCollectedOwners() {
        entries.removeAll { it.owner.get() == null }
    }

    @Synchronized
    private fun unregister(id: Long) {
        entries.removeAll { it.id == id }
    }

    private fun pressureForTrimLevel(level: Int): PixelMemoryPressure? = when {
        level >= TRIM_COMPLETE -> PixelMemoryPressure.CRITICAL
        level == TRIM_UI_HIDDEN -> PixelMemoryPressure.UI_HIDDEN
        level >= TRIM_RUNNING_LOW -> PixelMemoryPressure.MODERATE
        else -> null
    }

    private const val TRIM_RUNNING_LOW = 10
    private const val TRIM_UI_HIDDEN = 20
    private const val TRIM_COMPLETE = 80
}
