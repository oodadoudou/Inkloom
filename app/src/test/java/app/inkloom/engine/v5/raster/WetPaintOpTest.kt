package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.stroke.DirectStrokeSession
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetPaintOpTest {
    @Test fun `picked pigment is carried along the live path`() {
        val surface = PixelTileSurface(256, 128, 64)
        surface.beginWrite("red source").use { tx ->
            tx.editTile(PixelTileKey(0, 0), PixelRect(16, 48, 48, 64)) { pixels, stride ->
                for (y in 48 until 64) for (x in 16 until 48) pixels[y * stride + x] = 0xffff0000.toInt()
            }
            tx.commit().close()
        }
        val stroke = DirectStrokeSession(41L, 8f)
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(28f, 0xff000000.toInt(), dilution = .9f, charge = .1f, pull = 1f),
        ).begin(PaintOpContext(41L, surface.revision, "wet-pull")) as WetPaintOp.Session

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 32f, 56f)))
        paint.apply(stroke.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, 180f, 56f)))))

        val preview = paint.previewSnapshot()
        assertTrue((preview.pixelAt(150, 56) ushr 24) > 0)
        assertTrue((preview.pixelAt(150, 56) ushr 16 and 0xff) > (preview.pixelAt(150, 56) and 0xff))
        paint.cancel()
        assertEquals(0, surface.pixelAt(150, 56))
    }

    @Test fun `terminal batch publishes the exact preview without replay`() {
        val surface = PixelTileSurface(256, 128, 64)
        val stroke = DirectStrokeSession(42L, 8f)
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(24f, 0xff2040a0.toInt(), charge = .8f, attack = .8f),
        ).begin(PaintOpContext(42L, surface.revision, "wet-deposit")) as WetPaintOp.Session
        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 20f, 64f)))
        paint.apply(stroke.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, 180f, 64f)))))
        val beforeUp = paint.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))
        paint.finish(stroke.finish(sample(2, InputPhase.UP, 180f, 64f)))
        val afterUp = paint.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))
        assertTrue(beforeUp.contentEquals(afterUp))
        val published = paint.publish()
        assertTrue(afterUp.contentEquals(surface.snapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))))
        published.record.close()
    }

    private fun sample(sequence: Long, phase: InputPhase, x: Float, y: Float) = InputSample(
        sequence = sequence,
        phase = phase,
        tool = InputTool.STYLUS,
        point = DocumentPoint(x, y),
        eventTimeNanos = sequence,
        enqueuedTimeNanos = sequence,
        pressure = 1f,
        tiltRadians = 0f,
        orientationRadians = 0f,
    )
}
