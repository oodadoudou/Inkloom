package app.inkloom.engine.v5.input

import app.inkloom.engine.v5.coordinates.DocumentPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBackpressureControllerTest {
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

    private fun sample(sequence: Long, phase: InputPhase, time: Long, x: Float) = InputSample(
        sequence,
        phase,
        InputTool.STYLUS,
        DocumentPoint(x, 0f),
        time,
        time,
        .5f,
        0f,
        0f,
    )
}

