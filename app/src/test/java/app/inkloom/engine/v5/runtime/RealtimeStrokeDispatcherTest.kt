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
    fun idleBarrierIncludesFinishAcrossPumpRescheduling() {
        val published = java.util.concurrent.atomic.AtomicInteger()
        RealtimeStrokeDispatcher(onDamageReady = { _, _ -> }).use { dispatcher ->
            // Freeze the reachable gap after the pump observes an empty command queue but
            // before its finally block clears pumpScheduled and rearms pending commands.
            // Reflection keeps a scheduling hook out of the production contact path.
            val field = RealtimeStrokeDispatcher::class.java.getDeclaredField("pumpScheduled").apply { isAccessible = true }
            val scheduled = field.get(dispatcher) as java.util.concurrent.atomic.AtomicBoolean
            scheduled.set(true)
            val surface = PixelTileSurface(32, 32)
            assertTrue(dispatcher.begin("ink", surface,
                DryInkPaintOp(surface, DryInkDefinition(4f, 0xff102030.toInt())),
                3f, "barrier", sample(12f, 12f, 1L)))
            assertTrue(dispatcher.finish(sample(16f, 12f, 2L)) { result ->
                result.record.close()
                published.incrementAndGet()
            })
            val entered = CountDownLatch(1)
            val returned = CountDownLatch(1)
            val waiter = Thread {
                entered.countDown()
                dispatcher.awaitIdle()
                returned.countDown()
            }.apply { start() }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertFalse("Idle barrier overtook an already queued Finish", returned.await(250, TimeUnit.MILLISECONDS))
            } finally {
                scheduled.set(false)
                RealtimeStrokeDispatcher::class.java.getDeclaredMethod("schedulePump").apply { isAccessible = true }.invoke(dispatcher)
                waiter.join(2_000)
            }
            assertEquals(0L, returned.count)
            assertEquals(1, published.get())
        }
    }

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

    @Test
    fun consecutiveContactsQueueWithoutWaitingForThePreviousPublish() {
        val surface = PixelTileSurface(256, 128)
        val published = CountDownLatch(2)
        RealtimeStrokeDispatcher(onDamageReady = { _, _ -> }).use { dispatcher ->
            repeat(2) { index ->
                assertTrue(dispatcher.begin(
                    layerId = "ink",
                    surface = surface,
                    paintOp = DryInkPaintOp(surface, DryInkDefinition(14f, 0xff000000.toInt())),
                    maximumRadius = 9f,
                    brushRuntimeId = "rapid-$index",
                    sample = sample(30f + index * 100f, 60f, index * 3L + 1L),
                ))
                assertTrue(dispatcher.finish(sample(32f + index * 100f, 60f, index * 3L + 2L)) { result ->
                    result.record.close()
                    published.countDown()
                })
            }

            assertTrue(published.await(2, TimeUnit.SECONDS))
            dispatcher.awaitIdle()
            assertEquals(2L, surface.revision)
        }
    }

    @Test
    fun blockedWorkerKeepsThirtySecondThreeHundredRpsProductionQueueBounded() {
        val surface = PixelTileSurface(10_000, 32)
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val published = CountDownLatch(1)
        RealtimeStrokeDispatcher(onDamageReady = { _, _ ->
            if (workerStarted.count == 1L) {
                workerStarted.countDown()
                assertTrue(releaseWorker.await(2, TimeUnit.SECONDS))
            }
        }).use { dispatcher ->
            assertTrue(dispatcher.begin(
                layerId = "ink",
                surface = surface,
                paintOp = DryInkPaintOp(surface, DryInkDefinition(1f, 0xff000000.toInt())),
                maximumRadius = 1f,
                brushRuntimeId = "blocked-worker-backpressure",
                sample = sample(0f, 16f, 0L),
            ))
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS))

            var sequence = 1L
            repeat(30 * 60) {
                val reports = List(5) {
                    val extremum = sequence == 4_500L
                    sample(
                        sequence.toFloat(),
                        if (extremum) 24f else 16f,
                        sequence * 3_333_333L,
                        pressure = if (extremum) 1f else .5f,
                    ).also { sequence++ }
                }
                assertTrue("Smooth physical reports must coalesce without rejection", dispatcher.append(reports))
            }
            val queued = dispatcher.queueSnapshot()
            assertTrue(queued.pendingMoveSamples <= 256)
            assertTrue(queued.peakPendingMoveSamples <= 256)
            assertTrue("The production queue must coalesce the 9,000-report backlog", queued.coalescedMoveCount > 8_000)
            assertEquals(0L, queued.rejectedMoveCount)

            assertTrue(dispatcher.finish(sample(9_001f, 16f, sequence * 3_333_333L, pressure = .5f)) { result ->
                result.record.close()
                published.countDown()
            })
            releaseWorker.countDown()
            assertTrue(published.await(2, TimeUnit.SECONDS))
            assertEquals(1L, surface.revision)
            assertTrue("A pressure/direction extremum must survive queue coalescing", dispatcher.lastPublishedSamples().any {
                it.x == 4_500f && it.y == 24f && it.pressure == 1f
            })
        }
    }

    @Test
    fun replacementKeepsOldPreviewUntilReadyAndPublishesOnlyTheLatestDraft() {
        val surface = PixelTileSurface(256, 128)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val begins = java.util.concurrent.atomic.AtomicInteger()
        val published = java.util.concurrent.atomic.AtomicInteger()
        val damage = AtomicReference<app.inkloom.engine.v5.surface.PixelRect>()
        val base = DryInkPaintOp(surface, DryInkDefinition(6f, 0xff102030.toInt()))
        val paint = object : app.inkloom.engine.v5.raster.PaintOp {
            override val capabilities = base.capabilities
            override fun begin(context: app.inkloom.engine.v5.raster.PaintOpContext): app.inkloom.engine.v5.raster.PaintOpSession {
                if (begins.incrementAndGet() == 2) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return base.begin(context)
            }
        }
        RealtimeStrokeDispatcher(onDamageReady = { _, result -> damage.set(result.dirtyBounds) }).use { dispatcher ->
            dispatcher.begin("ink", surface, paint, 5f, "same-brush", sample(20f, 25f, 1))
            dispatcher.append(listOf(sample(200f, 25f, 2)))
            dispatcher.awaitIdle()
            val original = requireNotNull(dispatcher.activePresentation("ink"))
            assertTrue(dispatcher.replaceActive("ink", surface, paint, 5f, "same-brush",
                listOf(sample(20f, 60f, 1), sample(200f, 60f, 2))))
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertEquals(original, dispatcher.activePresentation("ink"))
                assertEquals(0L, surface.revision)
                repeat(30) { i ->
                    assertTrue(dispatcher.replaceActive("ink", surface, paint, 5f, "same-brush",
                        listOf(sample(20f, 65f + i, 1), sample(200f, 65f + i, 2))))
                }
                assertTrue(dispatcher.finish(null) { result -> result.record.close(); published.incrementAndGet() })
            } finally { release.countDown() }
            dispatcher.awaitIdle()
            assertEquals("Only the newest queued replacement is rasterized", 3, begins.get())
            assertEquals(1, published.get())
            assertEquals(1L, surface.revision)
            assertEquals(0, surface.snapshot().pixelAt(100, 25))
            assertEquals(0, surface.snapshot().pixelAt(100, 60))
            assertNotEquals(0, surface.snapshot().pixelAt(100, 94))
        }
    }

    @Test
    fun cancellingARegularizedDraftPreservesCommittedPixels() {
        val surface = PixelTileSurface(256, 128)
        val paint = DryInkPaintOp(surface, DryInkDefinition(6f, 0xff102030.toInt()))
        val damage = AtomicReference<app.inkloom.engine.v5.surface.PixelRect>()
        RealtimeStrokeDispatcher(onDamageReady = { _, result -> damage.set(result.dirtyBounds) }).use { dispatcher ->
            dispatcher.begin("ink", surface, paint, 5f, "same", sample(20f, 25f, 1))
            dispatcher.append(listOf(sample(200f, 25f, 2)))
            dispatcher.awaitIdle()
            dispatcher.replaceActive("ink", surface, paint, 5f, "same",
                listOf(sample(20f, 90f, 1), sample(200f, 90f, 2)))
            dispatcher.awaitIdle()
            val bounds = requireNotNull(damage.get())
            assertTrue("Damage clears the previous freehand preview", bounds.top <= 25 && bounds.bottom >= 90)
            dispatcher.cancel()
            assertEquals(0L, surface.revision)
            assertEquals(0, surface.tileCount)
            assertFalse(dispatcher.isActive)
        }
    }

    private fun sample(x: Float, y: Float, time: Long, pressure: Float = 1f) = RawDocumentSample(
        x = x,
        y = y,
        eventTimeNanos = time,
        enqueuedTimeNanos = time,
        pressure = pressure,
        tiltRadians = 0f,
        orientationRadians = 0f,
        tool = InputTool.STYLUS,
    )
}
