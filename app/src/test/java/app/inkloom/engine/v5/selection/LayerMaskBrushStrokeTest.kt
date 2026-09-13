package app.inkloom.engine.v5.selection

import app.inkloom.brushpresetv5.MotionFilteringSettings
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RealtimeStrokeController
import app.inkloom.engine.v5.raster.PaintOp
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class LayerMaskBrushStrokeTest {
    private fun sample(x: Float, y: Float, time: Long) = RawDocumentSample(x, y, time, time, 1f, 0f, 0f, InputTool.STYLUS)
    private fun start(mask: MaskTileSurface, hiding: Boolean, opacity: Float = 1f): LayerMaskBrushStroke {
        val coverage = PixelTileSurface(mask.logicalWidth, mask.logicalHeight, tileSize = mask.tileSize)
        return LayerMaskBrushStroke(mask, coverage, hiding).also {
            it.begin(DryInkPaintOp(coverage, DryInkDefinition(8f, -1, opacity = opacity, minimumPressureScale = 1f)),
                6f, "mask-test", sample(8f, 32f, 1), 0f, 0f, MotionFilteringSettings(), MotionFilteringSettings())
        }
    }

    @Test fun sparseFastStrokeMatchesDenseInputWithoutGapsAndHistoryRestoresDefault() {
        fun draw(dense: Boolean): MaskTileSurfaceSnapshot {
            val mask = MaskTileSurface(256, 64, tileSize = 64)
            val brush = start(mask, false)
            if (dense) (9..240).forEach { brush.append(listOf(sample(it.toFloat(), 32f, it.toLong()))) }
            else brush.append(listOf(sample(240f, 32f, 240)))
            val preview = brush.preview
            brush.finish(sample(240f, 32f, 241)).use { record ->
                assertArrayEquals(preview.copyAlpha(PixelRect(0, 0, 256, 64)), mask.snapshot().copyAlpha(PixelRect(0, 0, 256, 64)))
                mask.apply(record, MaskTileSurface.HistoryDirection.UNDO)
                assertEquals(0, mask.snapshot().tileCount)
                mask.apply(record, MaskTileSurface.HistoryDirection.REDO)
            }
            return mask.snapshot()
        }
        val sparse = draw(false); val dense = draw(true)
        for (x in 8..239) assertEquals("Gap at $x", 255, sparse.alphaAt(x, 32))
        assertArrayEquals(dense.copyAlpha(PixelRect(0, 0, 256, 64)), sparse.copyAlpha(PixelRect(0, 0, 256, 64)))
    }

    @Test fun opacityDoesNotReaccumulateOnEachPreviewButSeparateContactsDoAccumulate() {
        val mask = MaskTileSurface(256, 64, tileSize = 64, initialDefaultAlpha = 255)
        val first = start(mask, true, .5f)
        first.append(listOf(sample(220f, 32f, 2)))
        val middle = first.preview.alphaAt(100, 32)
        repeat(6) { first.append(listOf(sample(220f, 32f, 3L + it))) }
        assertEquals(middle, first.preview.alphaAt(100, 32))
        first.finish(null).close()
        assertTrue(middle in 126..128)
        val second = start(mask, true, .5f)
        second.append(listOf(sample(220f, 32f, 20)))
        second.finish(null).close()
        assertTrue(mask.snapshot().alphaAt(100, 32) in 62..65)
        assertEquals(255, mask.snapshot().alphaAt(100, 5))
    }

    @Test fun cancelledRevealLeavesMaskUnchangedAndPreviewImmutable() {
        val mask = MaskTileSurface(8192, 8192, tileSize = 64)
        val brush = start(mask, false)
        val initial = brush.preview
        brush.append(listOf(sample(240f, 32f, 2)))
        assertEquals(0, initial.alphaAt(200, 32))
        assertEquals(255, brush.preview.alphaAt(200, 32))
        brush.cancel()
        assertEquals(0, mask.snapshot().tileCount)
        assertEquals(0, mask.snapshot().alphaAt(200, 32))
    }
}

/** The selected V5 brush deposits coverage; CSP-style masks use alpha, never the selected RGB. */
private class LayerMaskBrushStroke(
    private val mask: MaskTileSurface,
    val coverage: PixelTileSurface,
    private val hiding: Boolean,
) {
    private val projection = LayerMaskBrushCoverage(mask, hiding)
    private val controller = RealtimeStrokeController()
    private var closed = false
    val preview get() = projection.preview

    fun begin(op: PaintOp, radius: Float, runtimeId: String, sample: RawDocumentSample,
        smoothing: Float, globalSmoothing: Float, globalMotion: MotionFilteringSettings,
        brushMotion: MotionFilteringSettings) {
        val result = controller.begin(coverage.surfaceId, coverage, op, radius, runtimeId, sample,
            smoothing, globalSmoothing, globalMotion, brushMotion)
        project(controller.previewSnapshot(coverage.surfaceId), result.dirtyBounds)
    }

    fun append(samples: List<RawDocumentSample>) {
        val result = controller.append(samples) ?: return
        project(controller.previewSnapshot(coverage.surfaceId), result.dirtyBounds)
    }

    fun finish(sample: RawDocumentSample?): MaskTileTransactionRecord {
        check(!closed)
        val published = controller.finish(sample)
        closed = true
        return try { projection.finish(published?.snapshot, published?.dirtyBounds) }
        finally { published?.record?.close() }
    }

    fun cancel() {
        if (closed) return
        closed = true
        controller.cancel()
        projection.cancel()
    }

    private fun project(snapshot: PixelTileSurfaceSnapshot?, dirty: PixelRect?) = projection.project(snapshot, dirty)
}

