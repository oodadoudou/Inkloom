package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubGestureTest {
    private fun scrub(gesture: ScrubGesture, xs: List<Float>, start: Long = 0L, step: Long = 40L): List<Boolean> {
        gesture.start(start, xs.first())
        return xs.drop(1).mapIndexed { index, x -> gesture.move(start + (index + 1) * step, x) }
    }

    @Test fun sideToSideStrokesClearOnceAfterThreeTurns() {
        val fired = scrub(ScrubGesture(minimumLeg = 50f), listOf(0f, 30f, 80f, 40f, -10f, 40f, 90f, 30f, -20f, 40f))
        assertEquals(1, fired.count { it })
        assertTrue(fired[6])
    }

    @Test fun aPanJitterOrSlowScrubIsNotAClear() {
        assertFalse(scrub(ScrubGesture(minimumLeg = 50f), listOf(0f, 80f, 160f, 240f, 320f)).any { it })
        assertFalse(scrub(ScrubGesture(minimumLeg = 50f), listOf(0f, 20f, 0f, 20f, 0f, 20f, 0f, 20f)).any { it })
        assertFalse(scrub(ScrubGesture(minimumLeg = 50f), listOf(0f, 80f, 0f, 80f, 0f), step = 600L).any { it })
    }
}
