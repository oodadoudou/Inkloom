package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerTapUndoGestureTest {
    @Test fun `one completed stationary two finger tap triggers undo`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        assertTrue(gesture.finish(1_080L))
    }

    @Test fun `pinch or translation never counts as an undo tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        gesture.move(101f, 100f, transforming = true)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_200L, 100f, 100f)
        gesture.move(140f, 100f, transforming = false)
        assertFalse(gesture.finish(1_280L))
    }

    @Test fun `long contact never counts as a tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_400L))
    }
}
