package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwoPointerRotationGestureTest {
    @Test fun `rotation follows the angle between both fingers`() {
        val gesture = TwoPointerRotationGesture()
        gesture.begin(0f, 0f, 100f, 0f)

        val delta = gesture.update(50f, -50f, 50f, 50f)

        assertEquals(90f, delta!!, .001f)
    }

    @Test fun `crossing the angle boundary does not jump by a full turn`() {
        val gesture = TwoPointerRotationGesture()
        gesture.begin(0f, 0f, -100f, 1.7455f)

        val delta = gesture.update(0f, 0f, -100f, -1.7455f)

        assertEquals(2f, delta!!, .01f)
    }

    @Test fun `reset requires a fresh two-finger anchor`() {
        val gesture = TwoPointerRotationGesture()
        gesture.begin(0f, 0f, 100f, 0f)
        gesture.update(0f, 0f, 100f, .5f)

        gesture.reset()

        assertNull(gesture.update(0f, 0f, 0f, 100f))
    }
}
