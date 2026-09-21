package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class SymmetryGuideTest {
    @Test fun centerAndRotationHandleAreIndependent() {
        val state = SymmetryGuideState(.25f, .75f, 0f)
        assertEquals(SymmetryGuideGesture.MOVE, SymmetryGuide.hitTest(state, 100f, 150f, 400f, 200f, 20f, 80f))
        assertEquals(SymmetryGuideGesture.ROTATE, SymmetryGuide.hitTest(state, 180f, 150f, 400f, 200f, 20f, 80f))
    }

    @Test fun moveAndRotateUseCanvasNormalizedCoordinates() {
        val moved = SymmetryGuide.moveTo(SymmetryGuideState(), 100f, 50f, 400f, 200f)
        assertEquals(.25f, moved.centerX)
        assertEquals(.25f, moved.centerY)
        assertEquals(90f, SymmetryGuide.rotateToward(moved, 100f, 100f, 400f, 200f).angleDegrees, .001f)
    }
}
