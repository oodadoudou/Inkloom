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
    fun delayedPhysicalHistoryAfterTimerKeepsOrderSensorsAndOneCommit() {
        val surface = PixelTileSurface(128, 64)
        val emitted = mutableListOf<app.inkloom.engine.v5.stroke.StrokeSample>()
        val delegate = DryInkPaintOp(surface, DryInkDefinition(8f, 0xff102030.toInt()))
        val recording = object : app.inkloom.engine.v5.raster.PaintOp by delegate {
            override fun begin(context: app.inkloom.engine.v5.raster.PaintOpContext): app.inkloom.engine.v5.raster.PaintOpSession {
                val session = delegate.begin(context) as app.inkloom.engine.v5.raster.PublishablePaintOpSession
                return object : app.inkloom.engine.v5.raster.PublishablePaintOpSession by session {
                    override fun apply(batch: app.inkloom.engine.v5.stroke.PaintBatch): app.inkloom.engine.v5.raster.PaintResult {
                        emitted.addAll(batch.samples)
                        return session.apply(batch)
                    }
                    override fun finish(batch: app.inkloom.engine.v5.stroke.PaintBatch): app.inkloom.engine.v5.raster.PaintResult {
                        emitted.addAll(batch.samples)
                        return session.finish(batch)
                    }
                }
            }
        }
        val controller = RealtimeStrokeController()
        val raw = listOf(sample(12f, 20f, 10), sample(12f, 20f, 40),
            sample(35f, 25f, 20).copy(pressure = .3f, tiltRadians = .4f, orientationRadians = -.2f),
            sample(60f, 30f, 30), sample(85f, 35f, 50), sample(100f, 40f, 45))
        controller.begin("ink", surface, recording, 5f, "timer-history", raw.first())
        controller.append(raw.subList(1, 4))
        controller.append(listOf(raw[4]))
        val published = requireNotNull(controller.finish(raw.last()))
        assertEquals(listOf(10L, 40L, 40L, 40L, 50L, 50L), emitted.map { it.eventTimeNanos })
        assertEquals(raw.map { it.x to it.y }, emitted.map { it.point.x to it.point.y })
        assertEquals(raw.map { it.pressure }, emitted.map { it.pressure })
        assertEquals(raw.map { it.tiltRadians }, emitted.map { it.tiltRadians })
        assertEquals(raw.map { it.orientationRadians }, emitted.map { it.orientationRadians })
        assertEquals(raw, controller.lastPublishedSamples())
        assertEquals(1L, surface.revision)
        assertNotEquals(0, surface.pixelAt(60, 30))
        assertFalse(controller.isActive)
        published.record.close()
        emitted.clear()
        controller.begin("ink", surface, recording, 5f, "new-contact", sample(20f, 50f, 1))
        requireNotNull(controller.finish(sample(40f, 50f, 2))).record.close()
        assertEquals(listOf(1L, 2L), emitted.map { it.eventTimeNanos })
    }

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
        assertEquals(published.snapshot.revision, controller.awaitingPresentationGeneration())
        assertEquals(published.record.transactionId, controller.awaitingPresentationTransactionId())
        assertEquals(previewPixel, controller.previewSnapshot("ink")?.pixelAt(50, 20))
        assertTrue(controller.onDocumentGenerationPresented("ink", published.snapshot.revision))
        assertEquals(null, controller.previewSnapshot("ink"))
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

    @Test
    fun emptyPublishDoesNotCreateAPresentationBarrier() {
        val surface = PixelTileSurface(64, 64)
        val controller = RealtimeStrokeController()
        controller.begin(
            "ink",
            surface,
            DryInkPaintOp(surface, DryInkDefinition(10f, 0x00000000, opacity = 0f)),
            7f,
            "transparent",
            sample(20f, 20f, 1),
        )

        val published = requireNotNull(controller.finish(sample(20f, 20f, 2)))
        assertEquals(0, published.record.changedTileCount)
        assertEquals(null, controller.awaitingPresentationGeneration())
        assertEquals(null, controller.awaitingPresentationTransactionId())
        assertEquals(null, controller.previewSnapshot("ink"))
        published.record.close()
    }

    @Test
    fun activePresentationPinsCommittedPixelsAndExposesOnlyTheNewContribution() {
        val surface = PixelTileSurface(128, 64, 64)
        surface.beginWrite("old ink").run {
            editTile(app.inkloom.engine.v5.surface.PixelTileKey(0, 0), app.inkloom.engine.v5.surface.PixelRect(0, 0, 64, 64)) { pixels, _ ->
                pixels[8 * 64 + 8] = 0xff556677.toInt()
            }
            commit().close()
        }
        val controller = RealtimeStrokeController()
        controller.begin(
            "ink",
            surface,
            DryInkPaintOp(surface, DryInkDefinition(12f, 0xff102030.toInt(), minimumPressureScale = 1f)),
            8f,
            "pinned",
            sample(80f, 32f, 1),
        )

        val presentation = requireNotNull(controller.activePresentation("ink"))
        assertEquals(0xff556677.toInt(), presentation.backdrop.pixelAt(8, 8))
        assertEquals(0, presentation.delta.pixelAt(8, 8))
        assertEquals(1L, presentation.backdrop.revision)
        assertEquals(1L, surface.revision)
        controller.cancel()
        assertEquals(0xff556677.toInt(), surface.pixelAt(8, 8))
    }

    @Test
    fun productionControllerKeepsThirtySecondThreeHundredRpsInputBounded() {
        val surface = PixelTileSurface(32, 32)
        val controller = RealtimeStrokeController()
        controller.begin(
            "ink",
            surface,
            DryInkPaintOp(surface, DryInkDefinition(1f, 0xff000000.toInt())),
            1f,
            "backpressure-gate",
            sample(16f, 16f, 0L),
        )
        var time = 1L
        repeat(30 * 60) {
            controller.append(List(5) { sample(16f, 16f, time++) })
        }

        val published = requireNotNull(controller.finish(sample(16f, 16f, time)))
        assertEquals(0, controller.backpressureSnapshot().depth)
        assertTrue(controller.backpressureSnapshot().peakDepth <= 5)
        assertEquals("Diagnostics must not retain the whole 9,000-sample stroke", 512, controller.lastPublishedSamples().size)
        published.record.close()
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
