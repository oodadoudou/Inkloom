package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainBehaviorTest {
    @Test fun disabledOffsetIsStableOrigin() {
        assertEquals(GrainOffset(0f, 0f), GrainBehavior.offset(10, false))
    }

    @Test fun enabledOffsetIsDeterministicPerStroke() {
        val first = GrainBehavior.offset(1234, true)
        assertEquals(first, GrainBehavior.offset(1234, true))
        assertNotEquals(first, GrainBehavior.offset(1235, true))
        assertTrue(first.x in 0f..1f && first.y in 0f..1f)
    }

    @Test fun movingGrainKeepsItsTipLocalPhaseWhileTexturizedGrainUsesPageSpace() {
        val base = GrainOffset(.125f, .25f)
        val movingA = GrainBehavior.sample(base, movement = 1f, zoom = 1f, scalePercent = 100f, centerX = 100f, centerY = 80f, diameter = 40f)
        val movingB = GrainBehavior.sample(base, movement = 1f, zoom = 1f, scalePercent = 100f, centerX = 220f, centerY = 180f, diameter = 40f)
        assertEquals(movingA.offset, movingB.offset)

        val fixedA = GrainBehavior.sample(base, movement = 0f, zoom = 1f, scalePercent = 100f, centerX = 100f, centerY = 80f, diameter = 40f)
        val fixedB = GrainBehavior.sample(base, movement = 0f, zoom = 1f, scalePercent = 100f, centerX = 130f, centerY = 110f, diameter = 40f)
        assertNotEquals(fixedA.offset, fixedB.offset)
    }

    @Test fun zoomIsIndependentFromAuthoredScale() {
        val fixedSmall = GrainBehavior.sample(GrainOffset(0f, 0f), 1f, 0f, 220f, 0f, 0f, 20f)
        val fixedLarge = GrainBehavior.sample(GrainOffset(0f, 0f), 1f, 0f, 220f, 0f, 0f, 200f)
        assertTrue(fixedSmall.scalePercent > fixedLarge.scalePercent)
        assertEquals(220f, fixedSmall.scalePercent * 20f / 100f, 1f)
        assertEquals(220f, fixedLarge.scalePercent * 200f / 100f, 1f)

        val following = GrainBehavior.sample(GrainOffset(0f, 0f), 1f, 1f, 220f, 0f, 0f, 20f)
        assertEquals(220f, following.scalePercent, .0001f)
    }
}
