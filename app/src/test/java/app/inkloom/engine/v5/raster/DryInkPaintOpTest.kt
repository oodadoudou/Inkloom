package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.stroke.DirectStrokeSession
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DryInkPaintOpTest {
    @Test fun `dry ink is visible before up and up does not replay earlier geometry`() {
        val surface = PixelTileSurface(512, 512, 64)
        val stroke = DirectStrokeSession(10L, 8f)
        val op = DryInkPaintOp(surface, DryInkDefinition(16f, 0xff102030.toInt(), minimumPressureScale = 1f))
        val paint = op.begin(PaintOpContext(10L, surface.revision, "ink")) as DryInkPaintOp.Session

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 40f, 40f)))
        paint.apply(stroke.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, 100f, 40f)))))
        val beforeUp = paint.previewSnapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, 160, 80))
        assertTrue(beforeUp.any { it != 0 })

        paint.finish(stroke.finish(sample(2, InputPhase.UP, 100f, 40f)))
        val afterUp = paint.previewSnapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, 160, 80))
        assertTrue(beforeUp.contentEquals(afterUp))
        val published = paint.publish()
        assertEquals(2, published.record.changedTileCount)
        assertTrue(afterUp.contentEquals(surface.snapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, 160, 80))))
    }

    @Test fun `eraser removes pixels only inside its own sparse delta`() {
        val surface = PixelTileSurface(256, 256, 64)
        val paintStroke = DirectStrokeSession(1L, 20f)
        val paint = DryInkPaintOp(surface, DryInkDefinition(40f, 0xff000000.toInt(), minimumPressureScale = 1f))
            .begin(PaintOpContext(1L, 0L, "ink")) as DryInkPaintOp.Session
        paint.apply(paintStroke.begin(sample(0, InputPhase.DOWN, 20f, 128f)))
        paint.finish(paintStroke.finish(sample(1, InputPhase.UP, 230f, 128f)))
        paint.publish().record.close()
        val untouched = surface.pixelAt(40, 128)

        val eraserStroke = DirectStrokeSession(2L, 12f)
        val eraser = DryInkPaintOp(surface, DryInkDefinition(24f, 0, minimumPressureScale = 1f, blend = DryBlend.DESTINATION_OUT))
            .begin(PaintOpContext(2L, surface.revision, "eraser")) as DryInkPaintOp.Session
        eraser.apply(eraserStroke.begin(sample(2, InputPhase.DOWN, 128f, 100f)))
        eraser.finish(eraserStroke.finish(sample(3, InputPhase.UP, 128f, 156f)))
        eraser.publish().record.close()

        assertEquals(untouched, surface.pixelAt(40, 128))
        assertNotEquals(untouched, surface.pixelAt(128, 128))
    }

    @Test fun `selection clips deposition at alpha level`() {
        val surface = PixelTileSurface(128, 128, 64)
        val selection = MaskTileSurface(128, 128, 64)
        selection.beginWrite("half").use { tx ->
            tx.replaceAlpha(
                app.inkloom.engine.v5.surface.PixelRect(0, 0, 64, 128),
                ByteArray(64 * 128) { 0xff.toByte() },
            )
            tx.commit().close()
        }
        val stroke = DirectStrokeSession(3L, 20f)
        val paint = DryInkPaintOp(
            surface,
            DryInkDefinition(40f, 0xffff0000.toInt(), minimumPressureScale = 1f),
            selection.snapshot(),
        ).begin(PaintOpContext(3L, 0L, "selected")) as DryInkPaintOp.Session
        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 10f, 64f)))
        paint.finish(stroke.finish(sample(1, InputPhase.UP, 118f, 64f)))
        paint.publish().record.close()
        assertNotEquals(0, surface.pixelAt(32, 64))
        assertEquals(0, surface.pixelAt(96, 64))
    }

    @Test fun `selection is sampled proportionally when its resolution differs from the layer`() {
        val surface = PixelTileSurface(256, 128, 64)
        val selection = MaskTileSurface(64, 32, 64)
        selection.beginWrite("left half").use { tx ->
            tx.replaceAlpha(
                app.inkloom.engine.v5.surface.PixelRect(0, 0, 32, 32),
                ByteArray(32 * 32) { 0xff.toByte() },
            )
            tx.commit().close()
        }
        val stroke = DirectStrokeSession(4L, 16f)
        val paint = DryInkPaintOp(
            surface,
            DryInkDefinition(32f, 0xff000000.toInt(), minimumPressureScale = 1f),
            selection.snapshot(),
        ).begin(PaintOpContext(4L, 0L, "scaled-selection")) as DryInkPaintOp.Session
        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 16f, 64f)))
        paint.finish(stroke.finish(sample(1, InputPhase.UP, 240f, 64f)))
        paint.publish().record.close()
        assertNotEquals(0, surface.pixelAt(64, 64))
        assertEquals(0, surface.pixelAt(192, 64))
    }

    @Test fun `pencil grain is document anchored across separate batches`() {
        fun render(split: Boolean): IntArray {
            val surface = PixelTileSurface(128, 64, 64)
            val stroke = DirectStrokeSession(5L, 8f)
            val paint = DryInkPaintOp(
                surface,
                DryInkDefinition(16f, 0xff202020.toInt(), minimumPressureScale = 1f, pencilGrain = .7f, grainSeed = 99),
            ).begin(PaintOpContext(5L, 0L, "pencil")) as DryInkPaintOp.Session
            paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 8f, 32f)))
            val moves = listOf(sample(1, InputPhase.MOVE, 48f, 32f), sample(2, InputPhase.MOVE, 88f, 32f))
            if (split) moves.forEach { paint.apply(stroke.append(InputSampleBatch.owned(listOf(it)))) }
            else paint.apply(stroke.append(InputSampleBatch.owned(moves)))
            paint.finish(stroke.finish(sample(3, InputPhase.UP, 120f, 32f)))
            paint.publish().record.close()
            return surface.snapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, 128, 64))
        }
        assertTrue(render(false).contentEquals(render(true)))
    }

    @Test fun `opacity and flow pressure remain independent`() {
        fun alpha(pressure: Float): Int {
            val surface = PixelTileSurface(64, 64, 64)
            val stroke = DirectStrokeSession(8L, 10f)
            val paint = DryInkPaintOp(
                surface,
                DryInkDefinition(
                    diameterPixels = 20f,
                    colorArgb = 0xff000000.toInt(),
                    minimumPressureScale = 1f,
                    opacityPressureMinimum = .2f,
                    flowPressureMinimum = .5f,
                ),
            ).begin(PaintOpContext(8L, 0L, "pressure")) as DryInkPaintOp.Session
            paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 32f, 32f, pressure)))
            paint.finish(stroke.finish())
            paint.publish().record.close()
            return surface.pixelAt(32, 32) ushr 24
        }
        assertTrue(alpha(.25f) < alpha(.75f))
    }

    private fun sample(sequence: Long, phase: InputPhase, x: Float, y: Float, pressure: Float = .75f) = InputSample(
        sequence,
        phase,
        InputTool.STYLUS,
        DocumentPoint(x, y),
        sequence,
        sequence,
        pressure,
        0f,
        0f,
    )
}
