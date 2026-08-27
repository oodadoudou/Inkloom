package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeStrokeControllerTest {
    @Test
    fun previewAndPublishedPixelsAreTheSameRenderProduct() {
        val surface = PixelTileSurface(128, 64)
        val controller = RealtimeStrokeController()
        controller.begin(
            "ink",
            surface,
            DryInkPaintOp(surface, DryInkDefinition(12f, 0xff102030.toInt())),
            8f,
            "test-ink",
            sample(12f, 20f, 1),
        )
        controller.append(listOf(sample(60f, 20f, 2), sample(100f, 30f, 3)))
        val preview = requireNotNull(controller.previewSnapshot("ink"))
        val previewPixel = preview.pixelAt(50, 20)
        assertNotEquals(0, previewPixel)

        val published = requireNotNull(controller.finish(sample(100f, 30f, 4)))
        assertEquals(previewPixel, published.snapshot.pixelAt(50, 20))
        assertFalse(controller.isActive)
        assertNotNull(published.dirtyBounds)
        published.record.close()
    }

    @Test
    fun cancelLeavesCommittedBackdropUntouched() {
        val surface = PixelTileSurface(64, 64)
        val controller = RealtimeStrokeController()
        controller.begin(
            "ink",
            surface,
            DryInkPaintOp(surface, DryInkDefinition(10f, 0xff000000.toInt())),
            7f,
            "test-ink",
            sample(20f, 20f, 1),
        )
        assertTrue(controller.cancel())
        assertEquals(0, surface.snapshot().pixelAt(20, 20))
    }

    private fun sample(x: Float, y: Float, time: Long) = RawDocumentSample(
        x = x,
        y = y,
        eventTimeNanos = time,
        enqueuedTimeNanos = time,
        pressure = 1f,
        tiltRadians = 0f,
        orientationRadians = 0f,
        tool = InputTool.STYLUS,
    )
}
