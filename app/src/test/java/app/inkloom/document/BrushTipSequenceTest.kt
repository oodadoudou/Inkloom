package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrushTipSequenceTest {
    @Test fun repeatAndReverseFollowCspOrder() {
        assertEquals(listOf(0, 1, 2, 0, 1, 2), (0..5).map { BrushTipSequence.index(BrushTipSequence.REPEAT, it, 3, 7) })
        assertEquals(listOf(0, 1, 2, 1, 0, 1, 2), (0..6).map { BrushTipSequence.index(BrushTipSequence.REVERSE, it, 3, 7) })
    }

    @Test fun finiteModesStopButRibbonRemainsContinuous() {
        assertEquals(listOf(0, 1, 2, 2, 2), (0..4).map { BrushTipSequence.index(BrushTipSequence.DO_NOT_REPEAT, it, 3, 7) })
        assertNull(BrushTipSequence.index(BrushTipSequence.ONE_TIME_ONLY, 3, 3, 7))
        assertEquals(0, BrushTipSequence.index(BrushTipSequence.ONE_TIME_ONLY, 3, 1, 7, ribbon = true))
    }

    @Test fun randomModesAreDeterministic() {
        val first = (0..20).map { BrushTipSequence.index(BrushTipSequence.RANDOM, it, 4, 901) }
        val second = (0..20).map { BrushTipSequence.index(BrushTipSequence.RANDOM, it, 4, 901) }
        assertEquals(first, second)
        val cycle = (0..3).map { BrushTipSequence.index(BrushTipSequence.ONE_RANDOM_CYCLE, it, 4, 901) }
        assertEquals(setOf(0, 1, 2, 3), cycle.filterNotNull().toSet())
        assertNull(BrushTipSequence.index(BrushTipSequence.ONE_RANDOM_CYCLE, 4, 4, 901))
    }
}
