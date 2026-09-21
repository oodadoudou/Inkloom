package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerTapUndoGestureTest {
    @Test fun `one completed stationary two finger tap triggers undo`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L,1_030L,100f,100f,200f,100f)
        assertTrue(gesture.finish(1_080L))
    }

    @Test fun `pinch or translation never counts as an undo tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L,1_030L,100f,100f,200f,100f)
        gesture.move(101f,100f,201f,100f,transforming = true)
        assertFalse(gesture.finish(1_080L))
        gesture.start(1_200L,1_230L,100f,100f,200f,100f)
        gesture.move(140f,100f,240f,100f,transforming = false)
        assertFalse(gesture.finish(1_280L))
    }

    @Test fun `long contact never counts as a tap`() {
        val gesture = TwoFingerTapUndoGesture(movementSlop = 12f)
        gesture.start(1_000L,1_030L,100f,100f,200f,100f)
        assertFalse(gesture.finish(1_400L))
    }

    @Test fun `one moving contact or a late second finger never becomes undo`() {
        val gesture=TwoFingerTapUndoGesture(movementSlop=12f)
        gesture.start(1_000L,1_030L,100f,100f,200f,100f)
        gesture.move(106f,100f,200f,100f,transforming=false)
        assertFalse(gesture.finish(1_100L))
        gesture.start(2_000L,2_180L,100f,100f,200f,100f)
        assertFalse(gesture.finish(2_210L))
    }
}
