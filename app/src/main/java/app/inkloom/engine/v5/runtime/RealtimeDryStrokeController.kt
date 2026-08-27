package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.raster.PaintOpContext
import app.inkloom.engine.v5.raster.PaintResult
import app.inkloom.engine.v5.raster.PublishedPaintDelta
import app.inkloom.engine.v5.stroke.DirectStrokeSession
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot

/**
 * Production coordinator for one V5 dry stroke.
 *
 * Input is already frozen in document pixels. Every sample is consumed once, preview is the same
 * sparse transaction that will be published, and finish never reconstructs the stroke.
 */
class RealtimeDryStrokeController {
    private var nextTransactionId = 1L
    private var nextInputSequence = 0L
    private var active: Active? = null
    private var lastPublishedSamples: List<RawDocumentSample> = emptyList()

    val isActive: Boolean get() = active != null
    val activeLayerId: String? get() = active?.layerId
    fun lastPublishedSamples(): List<RawDocumentSample> = lastPublishedSamples.toList()

    fun begin(
        layerId: String,
        surface: PixelTileSurface,
        definition: DryInkDefinition,
        selection: MaskTileSurfaceSnapshot?,
        sample: RawDocumentSample,
    ): PaintResult {
        check(active == null) { "A V5 paint gesture is already active" }
        val transactionId = nextTransactionId++
        val stroke = DirectStrokeSession(transactionId, definition.diameterPixels * .5f + 2f)
        val paint = DryInkPaintOp(surface, definition, selection).begin(
            PaintOpContext(transactionId, surface.revision, layerId),
        ) as DryInkPaintOp.Session
        active = Active(layerId, surface, stroke, paint, mutableListOf(sample))
        return paint.apply(stroke.begin(input(sample, InputPhase.DOWN)))
    }

    fun append(samples: List<RawDocumentSample>): PaintResult? {
        val gesture = active ?: return null
        if (samples.isEmpty()) return null
        gesture.samples.addAll(samples)
        val batch = InputSampleBatch.owned(samples.map { input(it, InputPhase.MOVE) })
        return gesture.paint.apply(gesture.stroke.append(batch))
    }

    fun finish(sample: RawDocumentSample?): PublishedPaintDelta? {
        val gesture = active ?: return null
        return try {
            sample?.let(gesture.samples::add)
            val finalBatch = gesture.stroke.finish(sample?.let { input(it, InputPhase.UP) })
            gesture.paint.finish(finalBatch)
            gesture.paint.publish().also { lastPublishedSamples = gesture.samples.toList() }
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
        active?.takeIf { it.layerId == layerId }?.paint?.previewSnapshot()

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
        val stroke: DirectStrokeSession,
        val paint: DryInkPaintOp.Session,
        val samples: MutableList<RawDocumentSample>,
    )
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
