package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample

/** Passes every batch to [delegate] and keeps the samples it painted, for vector layers. */
class RecordingPaintOp(private val delegate: PaintOp) : PaintOp {
    private val recorded = mutableListOf<StrokeSample>()
    override val capabilities: PaintOpCapabilities get() = delegate.capabilities

    /** The samples painted so far, in order. */
    fun samples(): List<StrokeSample> = synchronized(recorded) { recorded.toList() }

    override fun begin(context: PaintOpContext): PaintOpSession {
        synchronized(recorded) { recorded.clear() }
        val session = delegate.begin(context) as? PublishablePaintOpSession ?: error("Vector recording needs a publishable paint operation")
        return object : PublishablePaintOpSession by session {
            override fun apply(batch: PaintBatch): PaintResult = session.apply(batch).also { record(batch) }
            override fun finish(batch: PaintBatch): PaintResult = session.finish(batch).also { record(batch) }
        }
    }

    private fun record(batch: PaintBatch) = synchronized(recorded) { recorded += batch.samples }
}
