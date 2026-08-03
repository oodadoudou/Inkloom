package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerGuideTest {
    @Test fun `projection constrains points to rotated ruler in physical canvas space`() {
        val horizontal = RulerGuide.project(RulerGuideState(centerY = .4f), .8f, .9f, 1_000f, 2_000f)
        assertEquals(.8f, horizontal.x, .0001f)
        assertEquals(.4f, horizontal.y, .0001f)

        val diagonal = RulerGuide.project(RulerGuideState(angleDegrees = 45f), .8f, .5f, 1_000f, 2_000f)
        val dx = (diagonal.x - .5f) * 1_000f
        val dy = (diagonal.y - .5f) * 2_000f
        assertEquals(dx, dy, .01f)
    }

    @Test fun `rotation snaps to configured angle and normalizes`() {
        val state = RulerGuideState(centerX = .5f, centerY = .5f, snapDegrees = 15f)
        assertEquals(30f, RulerGuide.rotateToward(state, 900f, 720f, 1_000f, 1_000f).angleDegrees, .001f)
        assertEquals(-180f, RulerGuide.rotateToward(state, 100f, 500f, 1_000f, 1_000f).angleDegrees, .001f)
    }

    @Test fun `move and malformed state stay bounded`() {
        val malformed = RulerGuideState(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, 0f)
        val moved = RulerGuide.moveBy(malformed, 9_999f, -9_999f, 1_000f, 1_000f)
        assertEquals(1f, moved.centerX, 0f)
        assertEquals(0f, moved.centerY, 0f)
        assertTrue(moved.angleDegrees.isFinite())
        assertTrue(moved.snapDegrees in 1f..90f)
    }

    @Test fun `rotation handle wins over ruler body hit region`() {
        val state = RulerGuideState()
        val handle = RulerGuide.rotationHandle(state, 1_000f, 1_000f)
        assertEquals(RulerGestureMode.ROTATE, RulerGuide.hitTest(state, handle.x, handle.y, 1_000f, 1_000f, 40f))
        assertEquals(RulerGestureMode.MOVE, RulerGuide.hitTest(state, 500f, 500f, 1_000f, 1_000f, 40f))
        assertNull(RulerGuide.hitTest(state, 500f, 700f, 1_000f, 1_000f, 40f))
    }
}
