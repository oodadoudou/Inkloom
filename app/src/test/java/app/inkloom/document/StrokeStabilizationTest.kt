package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeStabilizationTest {
    @Test fun disabledSpeedAdjustmentPreservesConfiguredValue() {
        assertEquals(.4f, StrokeStabilization.smoothing(.4f, false, StrokeStabilization.REDUCE_WHEN_FAST, 1f), .0001f)
    }

    @Test fun cspSpeedModesMoveSmoothingInExpectedDirection() {
        val slow = StrokeStabilization.smoothing(.4f, true, StrokeStabilization.INCREASE_WHEN_SLOW, 0f)
        val fast = StrokeStabilization.smoothing(.4f, true, StrokeStabilization.REDUCE_WHEN_FAST, 1f)
        assertTrue(slow > .4f)
        assertTrue(fast < .4f)
    }
}
