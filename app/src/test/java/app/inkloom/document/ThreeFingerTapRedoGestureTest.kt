package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeFingerTapRedoGestureTest {
    @Test fun `one short stationary contact triggers redo`() {
        val gesture = ThreeFingerTapRedoGesture(movementSlop = 12f)
        gesture.start(1_000L, 200f, 160f)

        assertTrue(gesture.finish(1_120L))
        assertFalse(gesture.finish(1_130L))
    }

    @Test fun `translation scaling and long contact do not trigger redo`() {
        val gesture = ThreeFingerTapRedoGesture(movementSlop = 12f)
        gesture.start(1_000L, 200f, 160f)
        gesture.move(240f, 160f, transforming = false)
        assertFalse(gesture.finish(1_100L))

        gesture.start(2_000L, 200f, 160f)
        gesture.move(200f, 160f, transforming = true)
        assertFalse(gesture.finish(2_100L))

        gesture.start(3_000L, 200f, 160f)
        assertFalse(gesture.finish(3_300L))
    }
}
