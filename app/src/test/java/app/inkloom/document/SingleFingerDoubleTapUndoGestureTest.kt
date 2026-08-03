package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFingerDoubleTapUndoGestureTest {
    @Test fun `two short nearby taps trigger exactly once`() {
        val gesture = SingleFingerDoubleTapUndoGesture(movementSlop = 12f)

        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_220L, 105f, 102f)
        assertTrue(gesture.finish(1_300L))
        gesture.start(1_440L, 105f, 102f)
        assertFalse(gesture.finish(1_500L))
    }

    @Test fun `drag long press transform and cancellation clear the sequence`() {
        val gesture = SingleFingerDoubleTapUndoGesture(movementSlop = 12f)

        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_200L, 100f, 100f)
        gesture.move(140f, 100f, transforming = false)
        assertFalse(gesture.finish(1_260L))

        gesture.start(1_400L, 100f, 100f)
        assertFalse(gesture.finish(1_700L))
        gesture.start(1_800L, 100f, 100f)
        gesture.move(100f, 100f, transforming = true)
        assertFalse(gesture.finish(1_840L))

        gesture.start(2_000L, 100f, 100f)
        assertFalse(gesture.finish(2_080L))
        gesture.cancel()
        gesture.start(2_200L, 100f, 100f)
        assertFalse(gesture.finish(2_260L))
    }

    @Test fun `far apart taps do not form one double tap`() {
        val gesture = SingleFingerDoubleTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L, 100f, 100f)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_200L, 200f, 200f)
        assertFalse(gesture.finish(1_260L))
    }
}
