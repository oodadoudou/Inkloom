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
}
