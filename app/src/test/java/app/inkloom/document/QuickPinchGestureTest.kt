package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPinchGestureTest {
    @Test fun aFastPinchInFitsTheCanvas() {
        val gesture = QuickPinchGesture()
        gesture.start(1_000L, 600f)
        assertTrue(gesture.finish(1_200L, 240f, rotationDegrees = 4f))
        assertFalse("fires once", gesture.finish(1_210L, 240f, rotationDegrees = 4f))
    }

    @Test fun slowPinchesSmallPinchesPinchesOutAndTurnsStayZooming() {
        val gesture = QuickPinchGesture()
        gesture.start(1_000L, 600f); assertFalse(gesture.finish(1_600L, 240f, 0f))
        gesture.start(2_000L, 600f); assertFalse(gesture.finish(2_150L, 480f, 0f))
        gesture.start(3_000L, 300f); assertFalse(gesture.finish(3_150L, 700f, 0f))
        gesture.start(4_000L, 600f); assertFalse(gesture.finish(4_150L, 200f, 45f))
        gesture.start(5_000L, 600f); gesture.cancel(); assertFalse(gesture.finish(5_100L, 200f, 0f))
    }
}
