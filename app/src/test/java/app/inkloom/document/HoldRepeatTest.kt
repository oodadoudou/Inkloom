package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldRepeatTest {
    @Test fun repeatsStartSlowSpeedUpAndLevelOff() {
        val intervals = (0..30).map(HoldRepeat::intervalMs)
        assertEquals(250L, intervals.first())
        assertTrue(intervals.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(70L, intervals.last())
    }
}
