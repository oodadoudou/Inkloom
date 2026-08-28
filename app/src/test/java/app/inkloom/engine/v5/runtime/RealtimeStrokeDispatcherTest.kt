package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.surface.PixelTileSurface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeStrokeDispatcherTest {
    @Test
    fun moveRasterAndPublicationRunOnTheOrderedStrokeWorker() {
        val surface = PixelTileSurface(256, 128)
        val damageThread = AtomicReference<String>()
        val publishThread = AtomicReference<String>()
        val published = CountDownLatch(1)
        RealtimeStrokeDispatcher(
            onDamageReady = { _, _ -> damageThread.compareAndSet(null, Thread.currentThread().name) },
        ).use { dispatcher ->
            dispatcher.begin(
                layerId = "ink",
                surface = surface,
                paintOp = DryInkPaintOp(surface, DryInkDefinition(16f, 0xff102030.toInt())),
                maximumRadius = 10f,
                brushRuntimeId = "worker-test",
                sample = sample(20f, 60f, 1L),
            )
            assertTrue(dispatcher.append(listOf(sample(100f, 60f, 2L), sample(180f, 60f, 3L))))
            dispatcher.awaitIdle()

            assertTrue(requireNotNull(damageThread.get()).startsWith("Inkloom-V5-Stroke"))
            assertNotEquals(0, requireNotNull(dispatcher.previewSnapshot("ink")).pixelAt(90, 60))
            assertEquals("Committed pixels stay immutable until publish", 0L, surface.revision)

            assertTrue(dispatcher.finish(sample(220f, 60f, 4L)) { result ->
                publishThread.set(Thread.currentThread().name)
                result.record.close()
                published.countDown()
            })
            assertTrue(published.await(2, TimeUnit.SECONDS))
            assertTrue(requireNotNull(publishThread.get()).startsWith("Inkloom-V5-Stroke"))
            assertFalse(dispatcher.isActive)
            assertEquals(1L, surface.revision)
            assertTrue(surface.tileCount > 0)
        }
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
