package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushStampCountTest {
    @Test fun zeroJitterAlwaysUsesConfiguredCount() {
        assertEquals(9, BrushStampCount.resolve(9, 0f, 1, 0))
        assertEquals(16, BrushStampCount.resolve(99, 0f, 1, 0))
    }

    @Test fun jitterUsesDocumentedMinimumToMaximumRangeDeterministically() {
        val values = (0 until 40).map { BrushStampCount.resolve(10, .5f, 77, it) }
        assertTrue(values.all { it in 5..10 })
        assertEquals(values, (0 until 40).map { BrushStampCount.resolve(10, .5f, 77, it) })
        assertTrue(values.distinct().size > 1)
    }

    @Test fun secondaryBrushUsesAnIndependentDeterministicStream() {
        val primary = (0 until 40).map { BrushStampCount.resolve(10, .8f, 77, it) }
        val secondary = (0 until 40).map { BrushStampCount.resolve(10, .8f, 77, it, dual = true) }
        assertEquals(secondary, (0 until 40).map { BrushStampCount.resolve(10, .8f, 77, it, dual = true) })
        assertTrue(primary != secondary)
    }
}
