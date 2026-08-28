package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.input.InputBackpressureController
import app.inkloom.engine.v5.raster.PaintOp
import app.inkloom.engine.v5.raster.PaintResult
import app.inkloom.engine.v5.raster.PublishedPaintDelta
import app.inkloom.engine.v5.surface.PixelTileSurface
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production thread boundary for realtime paint.
 *
 * The input thread only appends immutable commands. One worker consumes DOWN/MOVE/UP in order,
 * drains bounded MOVE batches and publishes immutable preview snapshots. Even rapid consecutive
 * contacts therefore cannot make a later DOWN wait for the previous stroke's pen-up raster work.
 */
class RealtimeStrokeDispatcher(
    private val onDamageReady: (PixelTileSurface, PaintResult) -> Unit,
    private val controller: RealtimeStrokeController = RealtimeStrokeController(),
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Inkloom-V5-Stroke").apply {
            priority = Thread.NORM_PRIORITY + 1
            isDaemon = true
        }
    }
    private sealed interface Command {
        data class Start(
            val layerId: String,
            val surface: PixelTileSurface,
            val paintOp: PaintOp,
            val maximumRadius: Float,
            val brushRuntimeId: String,
            val sample: RawDocumentSample,
            val smoothing: Float,
        ) : Command

        data class Move(val samples: List<RawDocumentSample>) : Command
        data class Finish(
            val sample: RawDocumentSample?,
            val onPublished: (PublishedPaintDelta) -> Unit,
        ) : Command
        data object Cancel : Command
    }

    private val queueLock = Any()
    private val commands = ArrayDeque<Command>()
    private val pumpScheduled = AtomicBoolean(false)
    private val contactOpen = AtomicBoolean(false)
    @Volatile private var contactLayerId: String? = null
    @Volatile private var closed = false

    val isActive: Boolean get() = contactOpen.get()
    val activeLayerId: String? get() = contactLayerId ?: controller.activeLayerId

    fun begin(
        layerId: String,
        surface: PixelTileSurface,
        paintOp: PaintOp,
        maximumRadius: Float,
        brushRuntimeId: String,
        sample: RawDocumentSample,
        smoothing: Float = 0f,
    ): Boolean {
        check(!closed) { "Stroke dispatcher is closed" }
        if (!contactOpen.compareAndSet(false, true)) return false
        contactLayerId = layerId
        enqueue(Command.Start(layerId, surface, paintOp, maximumRadius, brushRuntimeId, sample, smoothing))
        return true
    }

    fun append(samples: List<RawDocumentSample>): Boolean {
        if (closed || samples.isEmpty() || !contactOpen.get()) return false
        enqueue(Command.Move(samples.toList()))
        return true
    }

    fun finish(sample: RawDocumentSample?, onPublished: (PublishedPaintDelta) -> Unit): Boolean {
        if (closed || !contactOpen.compareAndSet(true, false)) return false
        contactLayerId = null
        enqueue(Command.Finish(sample, onPublished))
        return true
    }

    fun cancel(): Boolean {
        if (closed || !contactOpen.compareAndSet(true, false)) return false
        contactLayerId = null
        enqueue(Command.Cancel)
        awaitIdle()
        return true
    }

    fun previewSnapshot(layerId: String) = controller.previewSnapshot(layerId)
    fun activePresentation(layerId: String) = controller.activePresentation(layerId)
    fun landedSnapshot(layerId: String) = controller.landedSnapshot(layerId)
    fun activeCompositeSnapshot(layerId: String) = controller.activeCompositeSnapshot(layerId)
    fun onDocumentGenerationPresented(layerId: String, generation: Long) =
        controller.onDocumentGenerationPresented(layerId, generation)
    fun awaitingPresentationGeneration() = controller.awaitingPresentationGeneration()
    fun awaitingPresentationTransactionId() = controller.awaitingPresentationTransactionId()
    fun lastPublishedSamples() = controller.lastPublishedSamples()
    fun backpressureSnapshot(): InputBackpressureController.Snapshot = controller.backpressureSnapshot()

    /** Test/diagnostic barrier; production presentation remains callback-driven. */
    fun awaitIdle() {
        if (!closed) submitAndWait { Unit }
    }

    override fun close() {
        if (closed) return
        if (contactOpen.get()) cancel() else awaitIdle()
        if (controller.isActive) submitAndWait(controller::cancel)
        closed = true
        worker.shutdownNow()
    }

    private fun enqueue(command: Command) {
        synchronized(queueLock) { commands.addLast(command) }
        schedulePump()
    }

    private fun schedulePump() {
        if (!pumpScheduled.compareAndSet(false, true)) return
        worker.execute {
            try {
                while (true) {
                    val command = synchronized(queueLock) {
                        if (commands.isEmpty()) null else commands.removeFirst()
                    } ?: break
                    when (command) {
                        is Command.Start -> {
                            workerSurface = command.surface
                            val result = controller.begin(
                                command.layerId,
                                command.surface,
                                command.paintOp,
                                command.maximumRadius,
                                command.brushRuntimeId,
                                command.sample,
                                command.smoothing,
                            )
                            onDamageReady(command.surface, result)
                        }
                        is Command.Move -> consumeMoves(command)
                        is Command.Finish -> {
                            controller.finish(command.sample)?.let(command.onPublished)
                            workerSurface = null
                        }
                        Command.Cancel -> {
                            controller.cancel()
                            workerSurface = null
                        }
                    }
                }
            } finally {
                pumpScheduled.set(false)
                // A producer can append after the empty observation and before the flag release.
                // Re-arm only after publishing false so no command can remain stranded.
                if (synchronized(queueLock) { commands.isNotEmpty() } && !closed) schedulePump()
            }
        }
    }

    private fun consumeMoves(first: Command.Move) {
        val samples = ArrayList<RawDocumentSample>(MAX_WORKER_MOVE_BATCH)
        samples.addAll(first.samples)
        while (samples.size < MAX_WORKER_MOVE_BATCH) {
            val next = synchronized(queueLock) {
                val candidate = commands.firstOrNull() as? Command.Move ?: return@synchronized null
                if (samples.size + candidate.samples.size > MAX_WORKER_MOVE_BATCH) null
                else commands.removeFirst() as Command.Move
            } ?: break
            samples.addAll(next.samples)
        }
        val result = controller.append(samples)
        if (result != null) workerSurface?.let { onDamageReady(it, result) }
    }

    /** Worker-owned surface retained only between the consumed Start and Finish/Cancel commands. */
    private var workerSurface: PixelTileSurface? = null

    private fun <T> submitAndWait(block: () -> T): T {
        val future: Future<T> = worker.submit<T> { block() }
        return try {
            future.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    private companion object {
        const val MAX_WORKER_MOVE_BATCH = 256
    }
}
