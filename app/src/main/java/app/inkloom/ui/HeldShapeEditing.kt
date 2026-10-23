package app.inkloom.ui

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.raster.PaintOp
import app.inkloom.engine.v5.raster.PaintOpContext
import app.inkloom.engine.v5.raster.PublishablePaintOpSession
import app.inkloom.engine.v5.runtime.HeldShape
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Edit shape after draw and hold: the regular line, curve, arc, circle or ellipse just drawn keeps
 * its handles. Dragging one repaints the stroke with the same brush over the pixels that were
 * under it; each finished drag is one undo step.
 */
internal class HeldShapeEditor(
    val layerId: String,
    private val surface: PixelTileSurface,
    /** The layer before the stroke, to restore what the old shape covered. */
    private val backdrop: PixelTileSurfaceSnapshot,
    private val paintOp: PaintOp,
    private val runtimeId: String,
    private val maximumRadius: Float,
    shape: HeldShape,
    private val template: List<RawDocumentSample>,
    painted: PixelRect,
) {
    var shape: HeldShape = shape
        private set
    private var painted = painted
    private var dragHandle = -1
    private var dragStartShape: HeldShape? = null
    private var dragStartPainted: PixelRect? = null
    private var preview: Pair<PixelTileTransactionRecord, PixelTileTransactionRecord?>? = null
    private var previewBounds: PixelRect? = null
    private var transactions = 1L

    val dragging: Boolean get() = dragHandle >= 0

    fun handleAt(x: Float, y: Float, radius: Float): Int? =
        shape.handles().withIndex().filter { (_, p) -> hypot(p.first - x, p.second - y) <= radius }.minByOrNull { (_, p) -> hypot(p.first - x, p.second - y) }?.index

    fun beginDrag(handle: Int) {
        dragHandle = handle; dragStartShape = shape; dragStartPainted = painted
    }

    /** Repaints the stroke with the handle at (x, y); returns the pixels that changed. */
    fun dragTo(x: Float, y: Float): PixelRect? {
        val start = dragStartShape ?: return null
        undoPreview()
        val next = start.withHandle(dragHandle, x, y)
        val samples = next.samples(template)
        val bounds = boundsOf(samples) ?: return null
        val region = (dragStartPainted ?: painted).union(bounds).intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
        if (region.isEmpty) return null
        val restore = surface.beginWrite("edit shape: restore").run {
            replacePremultipliedPixels(region, backdrop.copyPremultipliedPixels(region))
            commit()
        }
        val paint = runCatching { paintStroke(samples) }.getOrElse { failure ->
            surface.apply(restore, PixelTileSurface.HistoryDirection.UNDO); restore.close(); throw failure
        }
        preview = restore to paint
        previewBounds = region
        shape = next; painted = bounds
        return region
    }

    /** Ends the drag; the result is the undoable edit, or null when nothing changed. */
    fun endDrag(): ShapeEdit? {
        dragHandle = -1; dragStartShape = null; dragStartPainted = null
        val records = preview ?: return null
        val bounds = previewBounds ?: return null
        preview = null; previewBounds = null
        return ShapeEdit(records.first, records.second, bounds)
    }

    fun cancelDrag(): PixelRect? {
        val bounds = previewBounds
        undoPreview()
        dragStartShape?.let { shape = it }
        dragStartPainted?.let { painted = it }
        dragHandle = -1; dragStartShape = null; dragStartPainted = null
        return bounds
    }

    private fun undoPreview() {
        val (restore, paint) = preview ?: return
        paint?.let { surface.apply(it, PixelTileSurface.HistoryDirection.UNDO); it.close() }
        surface.apply(restore, PixelTileSurface.HistoryDirection.UNDO); restore.close()
        preview = null; previewBounds = null
    }

    private fun paintStroke(samples: List<RawDocumentSample>): PixelTileTransactionRecord? {
        val transaction = transactions++
        val session = paintOp.begin(PaintOpContext(transaction, surface.revision, runtimeId)) as PublishablePaintOpSession
        session.apply(PaintBatch(transaction, 0L, samples.mapIndexed { i, s ->
            StrokeSample(i.toLong(), DocumentPoint(s.x, s.y), s.eventTimeNanos, s.pressure.coerceIn(0f, 1f), s.tiltRadians, s.orientationRadians)
        }, null, false))
        session.finish(PaintBatch.empty(transaction, 1L, true))
        val published = session.publish()
        if (published.record.changedTileCount == 0) { published.record.close(); return null }
        return published.record
    }

    private fun boundsOf(samples: List<RawDocumentSample>): PixelRect? {
        if (samples.isEmpty()) return null
        val pad = maximumRadius + 2f
        return PixelRect(floor(samples.minOf { it.x } - pad).toInt().coerceAtLeast(0), floor(samples.minOf { it.y } - pad).toInt().coerceAtLeast(0),
            ceil(samples.maxOf { it.x } + pad).toInt().coerceAtMost(surface.logicalWidth), ceil(samples.maxOf { it.y } + pad).toInt().coerceAtMost(surface.logicalHeight))
            .takeUnless { it.isEmpty }
    }
}

/** One finished shape adjustment: restore the old stroke's pixels, then paint the new one. */
internal class ShapeEdit(val restore: PixelTileTransactionRecord, val paint: PixelTileTransactionRecord?, val bounds: PixelRect)
