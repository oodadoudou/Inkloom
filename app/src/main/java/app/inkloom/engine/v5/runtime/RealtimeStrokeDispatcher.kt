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
 * DOWN deposits the first point synchronously so contact feedback is immediate. MOVE only copies
 * immutable samples into the controller mailbox and returns; one ordered worker drains bounded
 * batches and publishes immutable preview snapshots. UP only appends an ordered publish command;
 * the worker drains the bounded mailbox and atomically publishes the same delta off the UI thread.
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
    private val drainScheduled = AtomicBoolean(false)
    @Volatile private var activeSurface: PixelTileSurface? = null
    @Volatile private var closed = false

    val isActive: Boolean get() = controller.isActive
    val activeLayerId: String? get() = controller.activeLayerId

    fun begin(
        layerId: String,
        surface: PixelTileSurface,
        paintOp: PaintOp,
        maximumRadius: Float,
        brushRuntimeId: String,
        sample: RawDocumentSample,
        smoothing: Float = 0f,
    ): PaintResult {
        check(!closed) { "Stroke dispatcher is closed" }
        awaitIdle()
        activeSurface = surface
        return controller.begin(layerId, surface, paintOp, maximumRadius, brushRuntimeId, sample, smoothing)
    }

    fun append(samples: List<RawDocumentSample>): Boolean {
        if (closed || samples.isEmpty() || !controller.enqueue(samples)) return false
        scheduleDrain()
        return true
    }

    fun finish(sample: RawDocumentSample?, onPublished: (PublishedPaintDelta) -> Unit): Boolean {
        if (closed || !controller.isActive) return false
        worker.execute {
            val result = controller.finish(sample)
            activeSurface = null
            result?.let(onPublished)
        }
        return true
    }

    fun cancel(): Boolean {
        if (closed || !controller.isActive) return false
        val result = submitAndWait(controller::cancel)
        activeSurface = null
        return result
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
        if (controller.isActive) cancel()
        closed = true
        worker.shutdownNow()
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return
        worker.execute {
            try {
                while (true) {
                    val result = controller.drainPending() ?: break
                    activeSurface?.let { surface -> onDamageReady(surface, result) }
                }
            } finally {
                drainScheduled.set(false)
                // An enqueue can race with the empty observation above. Re-arm after releasing the
                // flag so the last report can never remain stranded until ACTION_UP.
                if (controller.hasPendingInput() && !closed) scheduleDrain()
            }
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
}
