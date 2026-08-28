package app.inkloom.engine.v5.stroke

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectStrokeSessionTest {
    @Test fun `each physical sample is emitted exactly once and finish does no replay`() {
        val session = DirectStrokeSession(91L, maximumRadius = 3f)
        val down = session.begin(sample(0, InputPhase.DOWN))
        val move = session.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE), sample(2, InputPhase.MOVE))))
        val up = session.finish(sample(3, InputPhase.UP))
        assertEquals(listOf(0L), down.samples.map(StrokeSample::inputSequence))
        assertEquals(listOf(1L, 2L), move.samples.map(StrokeSample::inputSequence))
        assertEquals(listOf(3L), up.samples.map(StrokeSample::inputSequence))
        assertTrue(up.terminal)
    }

    @Test fun `predicted samples never become document paint batches`() {
        val session = DirectStrokeSession(12L, maximumRadius = 1f)
        session.begin(sample(0, InputPhase.DOWN))
        val result = session.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, predicted = true))))
        assertTrue(result.samples.isEmpty())
        assertFalse(result.terminal)
    }

    @Test fun `causal smoothing emits each sample once and never rewrites earlier geometry`() {
        val session = CausalStrokeSession(13L, maximumRadius = 2f, smoothing = .5f)
        val down = session.begin(sample(0, InputPhase.DOWN))
        val firstMove = session.append(InputSampleBatch.owned(listOf(sample(10, InputPhase.MOVE))))
        val emittedBeforeUp = firstMove.samples.single()
        val secondMove = session.append(InputSampleBatch.owned(listOf(sample(20, InputPhase.MOVE))))
        val up = session.finish(sample(30, InputPhase.UP))

        assertEquals(DocumentPoint(0f, 0f), down.samples.single().point)
        assertTrue(emittedBeforeUp.point.x < 10f)
        assertTrue(kotlin.math.hypot(10f - emittedBeforeUp.point.x, 10f - emittedBeforeUp.point.y) <= 2.251f)
        assertEquals(emittedBeforeUp, firstMove.samples.single())
        assertEquals(listOf(20L), secondMove.samples.map(StrokeSample::inputSequence))
        assertEquals(listOf(30L), up.samples.map(StrokeSample::inputSequence))
        assertTrue(up.terminal)
    }

    @Test fun `causal smoothing has a strict spatial lag bound during a fast stroke`() {
        val session = CausalStrokeSession(14L, maximumRadius = 200f, smoothing = 1f)
        session.begin(sample(0, InputPhase.DOWN))

        val output = session.append(InputSampleBatch.owned(listOf(
            sample(1_000, InputPhase.MOVE),
            sample(2_000, InputPhase.MOVE),
            sample(3_000, InputPhase.MOVE),
        )))

        output.samples.forEach { emitted ->
            val raw = emitted.inputSequence.toFloat()
            assertTrue("Realtime correction trailed by ${raw - emitted.point.x}px", raw - emitted.point.x <= 4.001f)
        }
    }

    private fun sample(sequence: Long, phase: InputPhase, predicted: Boolean = false) = InputSample(
        sequence,
        phase,
        InputTool.STYLUS,
        DocumentPoint(sequence.toFloat(), sequence.toFloat()),
        sequence,
        sequence,
        .5f,
        0f,
        0f,
        predicted,
    )
}
