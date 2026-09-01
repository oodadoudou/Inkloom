package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputBackpressureController
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.raster.PaintOp
import app.inkloom.engine.v5.raster.PaintOpContext
import app.inkloom.engine.v5.raster.PaintPresentationKind
import app.inkloom.engine.v5.raster.PaintResult
import app.inkloom.engine.v5.raster.PublishablePaintOpSession
import app.inkloom.engine.v5.raster.PublishedPaintDelta
import app.inkloom.engine.v5.stroke.DirectStrokeSession
import app.inkloom.engine.v5.stroke.CausalStrokeSession
import app.inkloom.engine.v5.stroke.StrokeSession
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect

enum class ActiveStrokePresentationBlend { SOURCE_OVER, DESTINATION_OUT, SOURCE_ATOP }

data class ActiveStrokePresentation(
    val backdrop: PixelTileSurfaceSnapshot,
    val delta: PixelTileSurfaceSnapshot,
    val blend: ActiveStrokePresentationBlend,
    val dirtyBounds: PixelRect,
)

/**
 * Production coordinator for one V5 realtime paint stroke.
 *
 * Input is already frozen in document pixels. Every sample is consumed once, preview is the same
 * sparse transaction that will be published, and finish never reconstructs the stroke.
 */
class RealtimeStrokeController {
    private var nextTransactionId = 1L
    private var nextInputSequence = 0L
    @Volatile private var active: Active? = null
    @Volatile private var landed: Landed? = null
    private var lastPublishedSamples: List<RawDocumentSample> = emptyList()
    private var lastBackpressureSnapshot = InputBackpressureController.Snapshot(0, 0, 0L, 0L)

    val isActive: Boolean get() = active != null
    val activeLayerId: String? get() = active?.layerId
    fun lastPublishedSamples(): List<RawDocumentSample> = lastPublishedSamples.toList()
    fun backpressureSnapshot(): InputBackpressureController.Snapshot = lastBackpressureSnapshot
    fun begin(
        layerId: String,
        surface: PixelTileSurface,
        paintOp: PaintOp,
        maximumRadius: Float,
        brushRuntimeId: String,
        sample: RawDocumentSample,
        smoothing: Float = 0f,
    ): PaintResult {
        check(active == null) { "A V5 paint gesture is already active" }
        landed = null
        val transactionId = nextTransactionId++
        val stroke: StrokeSession = if (smoothing > 0f) {
            CausalStrokeSession(transactionId, maximumRadius, smoothing.coerceIn(0f, 1f))
        } else {
            DirectStrokeSession(transactionId, maximumRadius)
        }
        val paint = paintOp.begin(
            PaintOpContext(transactionId, surface.revision, brushRuntimeId),
        ) as? PublishablePaintOpSession ?: error("Production PaintOp must publish its active delta")
        active = Active(
            layerId,
            surface,
            surface.snapshot(),
            stroke,
            paint,
            when (paint.presentationKind) {
                PaintPresentationKind.ERASE_COVERAGE -> ActiveStrokePresentationBlend.DESTINATION_OUT
                PaintPresentationKind.SOURCE_ATOP_DELTA -> ActiveStrokePresentationBlend.SOURCE_ATOP
                else -> ActiveStrokePresentationBlend.SOURCE_OVER
            },
            InputBackpressureController(),
            ArrayDeque<RawDocumentSample>(TRACE_SAMPLE_LIMIT).apply { addLast(sample) },
        )
        return paint.apply(stroke.begin(input(sample, InputPhase.DOWN))).also { result ->
            active?.apply {
                include(result.dirtyBounds)
                publishPreview()
            }
        }
    }

    /** Enqueues immutable document samples without doing geometry or raster work on the caller. */
    fun enqueue(samples: List<RawDocumentSample>): Boolean {
        val gesture = active ?: return false
        if (samples.isEmpty()) return false
        samples.forEach(gesture::rememberForDiagnostics)
        samples.chunked(MAX_PROCESSING_BATCH_SAMPLES).forEach { sourceChunk ->
            gesture.mailbox.enqueue(InputSampleBatch.owned(sourceChunk.map { input(it, InputPhase.MOVE) }))
        }
        return true
    }

    /** Consumes at most one bounded mailbox batch. This is the Stroke Worker entry point. */
    fun drainPending(): PaintResult? {
        val gesture = active ?: return null
        val pending = gesture.mailbox.drain(MAX_PROCESSING_BATCH_SAMPLES) ?: return null
        return gesture.paint.apply(gesture.stroke.append(pending)).also { result ->
            gesture.include(result.dirtyBounds)
            gesture.publishPreview()
            lastBackpressureSnapshot = gesture.mailbox.snapshot(pending.samples.last().enqueuedTimeNanos)
        }
    }

    fun append(samples: List<RawDocumentSample>): PaintResult? {
        if (!enqueue(samples)) return null
        var result: PaintResult? = null
        while (true) {
            val next = drainPending() ?: break
            result = result?.merge(next) ?: next
        }
        return result
    }

    fun finish(sample: RawDocumentSample?): PublishedPaintDelta? {
        val gesture = active ?: return null
        return try {
            sample?.let(gesture::rememberForDiagnostics)
            while (true) {
                drainPending() ?: break
            }
            val finalBatch = gesture.stroke.finish(sample?.let { input(it, InputPhase.UP) })
            gesture.include(gesture.paint.finish(finalBatch).dirtyBounds)
            // No frame can consume another active preview between this worker-owned finish and
            // publication. Avoid cloning/counting all dirty delta tiles a second time on pen-up.
            gesture.paint.publish().also { published ->
                lastBackpressureSnapshot = gesture.mailbox.snapshot(
                    sample?.enqueuedTimeNanos ?: gesture.samples.lastOrNull()?.enqueuedTimeNanos ?: 0L,
                )
                lastPublishedSamples = gesture.samples.toList()
                landed = if (published.record.changedTileCount > 0 && published.record.transactionId > 0L) {
                    Landed(
                        gesture.layerId,
                        published.snapshot.revision,
                        published.record.transactionId,
                        published.snapshot,
                    )
                } else null
            }
        } finally {
            active = null
        }
    }

    fun cancel(): Boolean {
        val gesture = active ?: return false
        active = null
        gesture.paint.cancel()
        gesture.stroke.cancel()
        return true
    }

    fun previewSnapshot(layerId: String): PixelTileSurfaceSnapshot? =
        active?.takeIf { it.layerId == layerId }?.previewSnapshot()
            ?: landed?.takeIf { it.layerId == layerId }?.snapshot

    fun activePresentation(layerId: String): ActiveStrokePresentation? {
        val gesture = active?.takeIf { it.layerId == layerId } ?: return null
        if (gesture.paint.presentationKind == PaintPresentationKind.COMPOSITE_REPLACEMENT) return null
        val dirty = gesture.currentDirtyBounds() ?: return null
        return ActiveStrokePresentation(
            backdrop = gesture.backdrop,
            delta = gesture.previewSnapshot(),
            blend = gesture.presentationBlend,
            dirtyBounds = dirty,
        )
    }

    fun landedSnapshot(layerId: String): PixelTileSurfaceSnapshot? =
        landed?.takeIf { it.layerId == layerId }?.snapshot

    fun activeCompositeSnapshot(layerId: String): PixelTileSurfaceSnapshot? =
        active?.takeIf {
            it.layerId == layerId && it.paint.presentationKind == PaintPresentationKind.COMPOSITE_REPLACEMENT
        }?.previewSnapshot()

    /** Releases the landed owner only after the stable View has submitted that generation. */
    fun onDocumentGenerationPresented(layerId: String, generation: Long): Boolean {
        val value = landed ?: return false
        if (value.layerId != layerId || generation < value.generation) return false
        landed = null
        return true
    }

    fun awaitingPresentationGeneration(): Long? = landed?.generation
    fun awaitingPresentationTransactionId(): Long? = landed?.transactionId

    private fun input(sample: RawDocumentSample, phase: InputPhase): InputSample = InputSample(
        sequence = nextInputSequence++,
        phase = phase,
        tool = sample.tool,
        point = DocumentPoint(sample.x, sample.y),
        eventTimeNanos = sample.eventTimeNanos,
        enqueuedTimeNanos = maxOf(sample.eventTimeNanos, sample.enqueuedTimeNanos),
        pressure = sample.pressure.coerceIn(0f, 1f),
        tiltRadians = sample.tiltRadians,
        orientationRadians = sample.orientationRadians,
    )

    private data class Active(
        val layerId: String,
        val surface: PixelTileSurface,
        val backdrop: PixelTileSurfaceSnapshot,
        val stroke: StrokeSession,
        val paint: PublishablePaintOpSession,
        val presentationBlend: ActiveStrokePresentationBlend,
        val mailbox: InputBackpressureController,
        val samples: ArrayDeque<RawDocumentSample>,
    ) {
        @Volatile private var dirtyBounds: PixelRect? = null
        @Volatile private var latestPreview: PixelTileSurfaceSnapshot = paint.previewSnapshot()

        @Synchronized
        fun include(bounds: PixelRect?) {
            if (bounds != null) dirtyBounds = dirtyBounds?.union(bounds) ?: bounds
        }

        fun currentDirtyBounds(): PixelRect? = dirtyBounds

        /** Called only by the ordered stroke worker after a complete batch. */
        fun publishPreview() {
            latestPreview = paint.previewSnapshot()
        }

        fun previewSnapshot(): PixelTileSurfaceSnapshot = latestPreview

        @Synchronized
        fun rememberForDiagnostics(sample: RawDocumentSample) {
            if (samples.size == TRACE_SAMPLE_LIMIT) samples.removeFirst()
            samples.addLast(sample)
        }
    }

    private data class Landed(
        val layerId: String,
        val generation: Long,
        val transactionId: Long,
        val snapshot: PixelTileSurfaceSnapshot,
    )

    private fun PaintResult.merge(other: PaintResult): PaintResult {
        require(transactionId == other.transactionId)
        return other.copy(
            changed = changed || other.changed,
            dirtyBounds = when {
                dirtyBounds == null -> other.dirtyBounds
                other.dirtyBounds == null -> dirtyBounds
                else -> dirtyBounds.union(other.dirtyBounds)
            },
        )
    }

    private companion object {
        const val MAX_PROCESSING_BATCH_SAMPLES = 64
        const val TRACE_SAMPLE_LIMIT = 512
    }
}

data class RawDocumentSample(
    val x: Float,
    val y: Float,
    val eventTimeNanos: Long,
    val enqueuedTimeNanos: Long,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val tool: InputTool,
)
