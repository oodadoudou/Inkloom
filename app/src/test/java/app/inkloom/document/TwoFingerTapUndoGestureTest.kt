package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerTapUndoGestureTest {
    @Test fun `two completed stationary taps trigger undo`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_220L, 102f, 101f)
        assertTrue(gesture.finish(1_300L))
    }

    @Test fun `pinch or translation never counts as an undo tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        gesture.move(101f, 100f, scaling = true)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_200L, 100f, 100f)
        gesture.move(140f, 100f, scaling = false)
        assertFalse(gesture.finish(1_280L))
    }

    @Test fun `long contact never counts as a tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_400L))
    }
}
