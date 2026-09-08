package app.inkloom.engine.v5.input

import app.inkloom.engine.v5.coordinates.DocumentPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBackpressureControllerTest {
    @Test fun delayedSourceTimeRemainsVisibleToLatencyAfterModelClockCorrection() {
        val controller = InputBackpressureController()
        val timer = sample(0, InputPhase.MOVE, 40, 10f).copy(enqueuedTimeNanos = 45)
        val history = sample(1, InputPhase.MOVE, 20, 30f)
            .copy(enqueuedTimeNanos = 50, modelTimeNanos = 40)
        val latest = sample(2, InputPhase.MOVE, 55, 50f).copy(enqueuedTimeNanos = 60)
        controller.enqueue(InputSampleBatch.owned(listOf(timer, history, latest)))
        assertEquals(30L, history.queueAgeNanos)
        assertEquals("Delayed history is older than the timer at the queue head", 50L,
            controller.snapshot(70).oldestInputAgeNanos)
        val drained = requireNotNull(controller.drain(3)).samples
        assertEquals(listOf(40L, 20L, 55L), drained.map { it.eventTimeNanos })
        assertEquals(listOf(40L, 40L, 55L), drained.map { it.modelTimeNanos })
        assertEquals(listOf(timer, history, latest), drained)
    }

    @Test fun `300 samples per second for 30 seconds has bounded depth when consumer keeps pace`() {
        val controller = InputBackpressureController(softLimit = 64, hardLimit = 256)
        var sequence = 0L
        var now = 0L
        repeat(30 * 60) {
            val frame = List(5) {
                sample(sequence++, InputPhase.MOVE, now.also { now += 3_333_333L }, sequence.toFloat())
            }
            controller.enqueue(InputSampleBatch.owned(frame))
            controller.drain(5)
        }
        val snapshot = controller.snapshot(now)
        assertEquals(0, snapshot.depth)
        assertTrue(snapshot.peakDepth <= 5)
        assertEquals(0L, snapshot.oldestInputAgeNanos)
    }

    @Test fun `overload coalesces only nearby ordinary moves and preserves up`() {
        val controller = InputBackpressureController(softLimit = 4, hardLimit = 16)
        val values = buildList {
            add(sample(0, InputPhase.DOWN, 0L, 0f))
            for (index in 1L..12L) add(sample(index, InputPhase.MOVE, index, index * .01f))
            add(sample(13, InputPhase.UP, 13L, 1f))
        }
        controller.enqueue(InputSampleBatch.owned(values))
        val drained = generateSequence { controller.drain(16) }.flatMap { it.samples.asSequence() }.toList()
        assertEquals(InputPhase.DOWN, drained.first().phase)
        assertEquals(InputPhase.UP, drained.last().phase)
        assertTrue(controller.snapshot(13L).coalescedMoveCount > 0)
    }

    @Test fun `overload coalesces distant collinear reports but preserves corners and pressure extrema`() {
        val controller = InputBackpressureController(softLimit = 4, hardLimit = 32)
        val values = buildList {
            add(sample(0, InputPhase.DOWN, 0L, 0f))
            for (index in 1L..20L) add(sample(index, InputPhase.MOVE, index, index * 10f))
            add(sample(21, InputPhase.MOVE, 21L, 200f, y = 40f))
            add(sample(22, InputPhase.MOVE, 22L, 210f, y = 80f, pressure = 1f))
            add(sample(23, InputPhase.MOVE, 23L, 220f, y = 120f))
            add(sample(24, InputPhase.UP, 24L, 230f, y = 160f))
        }

        controller.enqueue(InputSampleBatch.owned(values))
        val drained = generateSequence { controller.drain(32) }.flatMap { it.samples.asSequence() }.toList()

        assertTrue("Straight high-speed reports should be bounded", drained.size < values.size)
        assertTrue("The direction corner must survive", drained.any { it.point.x == 200f && it.point.y == 40f })
        assertTrue("The pressure maximum must survive", drained.any { it.pressure == 1f })
        assertEquals(InputPhase.UP, drained.last().phase)
    }

    private fun sample(
        sequence: Long,
        phase: InputPhase,
        time: Long,
        x: Float,
        y: Float = 0f,
        pressure: Float = .5f,
    ) = InputSample(
        sequence,
        phase,
        InputTool.STYLUS,
        DocumentPoint(x, y),
        time,
        time,
        pressure,
        0f,
        0f,
    )
}
