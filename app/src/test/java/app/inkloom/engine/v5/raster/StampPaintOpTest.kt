package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampPaintOpTest {
    @Test
    fun activePreviewAndPublicationShareExactlyOneSparseTransaction() {
        val surface = PixelTileSurface(160, 80)
        val definition = definition()
        val session = StampPaintOp(surface, definition).begin(PaintOpContext(1, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(1, 0, false, sample(0, 12f, 35f)))
        session.apply(batch(1, 1, false, sample(1, 120f, 35f)))
        val preview = session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 160, 80))
        session.finish(batch(1, 2, true, sample(2, 120f, 35f)))
        val published = session.publish()
        assertArrayEquals(preview, published.snapshot.copyPremultipliedPixels(PixelRect(0, 0, 160, 80)))
        assertNotEquals(0, published.snapshot.pixelAt(64, 35))
        assertEquals(1L, surface.revision)
        published.record.close()
    }

    @Test
    fun fractionalSpacingIsInvariantToMotionEventBatchBoundaries() {
        val once = draw(listOf(listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))))
        val split = draw(listOf(
            listOf(sample(1, 42f, 28f)),
            listOf(sample(2, 76f, 35f)),
            listOf(sample(3, 122f, 24f)),
        ))
        assertArrayEquals(once, split)
    }

    @Test
    fun finishDoesNotEvolveOrRebuildMaterial() {
        val surface = PixelTileSurface(96, 64)
        val session = StampPaintOp(surface, definition()).begin(PaintOpContext(7, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(7, 0, false, sample(0, 20f, 20f)))
        session.apply(batch(7, 1, false, sample(1, 70f, 20f)))
        val beforeFinish = session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 64))
        val finish = session.finish(batch(7, 2, true, sample(2, 70f, 20f)))
        assertTrue(!finish.changed)
        assertArrayEquals(beforeFinish, session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 64)))
        session.publish().record.close()
    }

    @Test
    fun texturedEraserOnlyRemovesItsOwnFootprint() {
        val surface = PixelTileSurface(64, 64)
        val fill = surface.beginWrite("fill")
        fill.editTile(app.inkloom.engine.v5.surface.PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ ->
            pixels.fill(0xff102030.toInt())
        }
        fill.commit().close()
        val eraser = definition().copy(blend = DryBlend.DESTINATION_OUT, colorArgb = 0)
        val session = StampPaintOp(surface, eraser).begin(PaintOpContext(9, surface.revision, "eraser")) as StampPaintOp.Session
        session.apply(batch(9, 0, false, sample(0, 32f, 32f)))
        session.finish(PaintBatch.empty(9, 1, terminal = true))
        val published = session.publish()
        assertEquals(0, published.snapshot.pixelAt(32, 32))
        assertEquals(0xff102030.toInt(), published.snapshot.pixelAt(3, 3))
        published.record.close()
    }

    private fun draw(moveBatches: List<List<StrokeSample>>): IntArray {
        val surface = PixelTileSurface(144, 64)
        val session = StampPaintOp(surface, definition()).begin(PaintOpContext(1, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(1, 0, false, sample(0, 12f, 30f)))
        moveBatches.forEachIndexed { index, samples -> session.apply(batch(1, index + 1L, false, *samples.toTypedArray())) }
        session.finish(PaintBatch.empty(1, moveBatches.size + 1L, terminal = true))
        val published = session.publish()
        return published.snapshot.copyPremultipliedPixels(PixelRect(0, 0, 144, 64)).also { published.record.close() }
    }

    private fun definition() = StampBrushDefinition(
        diameterPixels = 14f,
        colorArgb = 0xff102030.toInt(),
        spacingRatio = .24f,
        tips = listOf(BrushTipMask.solidCircle(32, .8f)),
        renderSeed = 43,
    )

    private fun sample(sequence: Long, x: Float, y: Float) = StrokeSample(
        sequence,
        DocumentPoint(x, y),
        sequence * 1_000_000L,
        1f,
        0f,
        0f,
    )

    private fun batch(transaction: Long, ordinal: Long, terminal: Boolean, vararg samples: StrokeSample) = PaintBatch(
        transaction,
        ordinal,
        samples.toList(),
        null,
        terminal,
    )
}

