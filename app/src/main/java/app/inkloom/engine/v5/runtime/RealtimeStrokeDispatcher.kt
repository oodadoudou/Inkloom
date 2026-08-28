package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.input.InputBackpressureController
import app.inkloom.engine.v5.input.GeometricMoveCoalescer
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

        data class Move(val samples: BoundedMoveBuffer) : Command
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
    private var pendingMoveSamples = 0
    private var peakPendingMoveSamples = 0
    private var coalescedQueuedMoves = 0L
    private var rejectedQueuedMoves = 0L
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
        val result = synchronized(queueLock) {
            val tail = commands.lastOrNull() as? Command.Move
            val buffer = tail?.samples ?: BoundedMoveBuffer().also { commands.addLast(Command.Move(it)) }
            val before = buffer.size
            val result = buffer.append(samples, MAX_PENDING_MOVE_SAMPLES - pendingMoveSamples)
            val growth = buffer.size - before
            pendingMoveSamples += growth
            peakPendingMoveSamples = maxOf(peakPendingMoveSamples, pendingMoveSamples)
            coalescedQueuedMoves += result.coalesced
            rejectedQueuedMoves += result.rejected
            if (buffer.size == 0 && tail == null) commands.removeLast()
            result to (buffer.size > 0)
        }
        if (result.second) schedulePump()
        return result.first.accepted
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
    data class QueueSnapshot(
        val pendingMoveSamples: Int,
        val peakPendingMoveSamples: Int,
        val coalescedMoveCount: Long,
        val rejectedMoveCount: Long,
    )

    fun queueSnapshot(): QueueSnapshot = synchronized(queueLock) {
        QueueSnapshot(pendingMoveSamples, peakPendingMoveSamples, coalescedQueuedMoves, rejectedQueuedMoves)
    }

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
                        if (commands.isEmpty()) null else commands.removeFirst().also { removed ->
                            if (removed is Command.Move) pendingMoveSamples -= removed.samples.size
                        }
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
        samples.addAll(first.samples.drain())
        while (samples.size < MAX_WORKER_MOVE_BATCH) {
            val next = synchronized(queueLock) {
                val candidate = commands.firstOrNull() as? Command.Move ?: return@synchronized null
                if (samples.size + candidate.samples.size > MAX_WORKER_MOVE_BATCH) null else {
                    pendingMoveSamples -= candidate.samples.size
                    commands.removeFirst() as Command.Move
                }
            } ?: break
            samples.addAll(next.samples.drain())
        }
        val result = controller.append(samples)
        if (result != null) workerSurface?.let { onDamageReady(it, result) }
    }

    /** Worker-owned surface retained only between the consumed Start and Finish/Cancel commands. */
    private var workerSurface: PixelTileSurface? = null

    private class BoundedMoveBuffer {
        data class AppendResult(val accepted: Boolean, val coalesced: Long, val rejected: Long)

        private val pending = ArrayDeque<RawDocumentSample>()
        val size: Int get() = pending.size

        fun append(samples: List<RawDocumentSample>, remainingGlobalCapacity: Int): AppendResult {
            var capacity = remainingGlobalCapacity.coerceAtLeast(0)
            var coalesced = 0L
            var rejected = 0L
            samples.forEach { sample ->
                val previous = pending.lastOrNull()
                val beforePrevious = if (pending.size >= 2) pending.elementAt(pending.size - 2) else null
                if (pending.size >= SOFT_PENDING_MOVE_SAMPLES && sample.canReplace(previous, beforePrevious)) {
                    pending.removeLast()
                    pending.addLast(sample)
                    coalesced++
                } else if (pending.size < MAX_PENDING_MOVE_SAMPLES && capacity > 0) {
                    pending.addLast(sample)
                    capacity--
                } else {
                    rejected++
                }
            }
            return AppendResult(rejected == 0L, coalesced, rejected)
        }

        fun drain(): List<RawDocumentSample> = List(pending.size) { pending.removeFirst() }

        private fun RawDocumentSample.canReplace(
            previous: RawDocumentSample?,
            beforePrevious: RawDocumentSample?,
        ): Boolean {
            previous ?: return false
            if (tool != previous.tool) return false
            beforePrevious ?: return squaredDistance(previous) < .25f
            if (beforePrevious.tool != tool) return false
            return GeometricMoveCoalescer.canReplace(
                before = app.inkloom.engine.v5.coordinates.DocumentPoint(beforePrevious.x, beforePrevious.y),
                middle = app.inkloom.engine.v5.coordinates.DocumentPoint(previous.x, previous.y),
                after = app.inkloom.engine.v5.coordinates.DocumentPoint(x, y),
                beforePressure = beforePrevious.pressure,
                middlePressure = previous.pressure,
                afterPressure = pressure,
                beforeTilt = beforePrevious.tiltRadians,
                middleTilt = previous.tiltRadians,
                afterTilt = tiltRadians,
            )
        }

        private fun RawDocumentSample.squaredDistance(other: RawDocumentSample): Float {
            val dx = x - other.x
            val dy = y - other.y
            return dx * dx + dy * dy
        }
    }

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
        const val SOFT_PENDING_MOVE_SAMPLES = 64
        const val MAX_PENDING_MOVE_SAMPLES = 256
    }
}
