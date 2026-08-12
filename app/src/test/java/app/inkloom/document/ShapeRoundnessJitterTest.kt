package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeRoundnessJitterTest {
    @Test fun zeroAmountsLeaveTheTipUnchanged() {
        assertEquals(ShapeScaleFactors(1f, 1f), ShapeRoundnessJitter.factors(41, 7, 0f, 0f))
    }

    @Test fun eachAxisIsDeterministicIndependentAndOnlyCompresses() {
        val first = ShapeRoundnessJitter.factors(41, 7, .8f, .6f)
        val repeated = ShapeRoundnessJitter.factors(41, 7, .8f, .6f)
        val nextStamp = ShapeRoundnessJitter.factors(41, 8, .8f, .6f)

        assertEquals(first, repeated)
        assertNotEquals(first, nextStamp)
        assertTrue(first.horizontal in .2f..1f)
        assertTrue(first.vertical in .4f..1f)
        assertNotEquals(first.horizontal, first.vertical)
    }

    @Test fun dualBrushUsesAnIndependentDeterministicSequence() {
        val primary = ShapeRoundnessJitter.factors(93, 5, 1f, 1f)
        val dual = ShapeRoundnessJitter.factors(93, 5, 1f, 1f, dual = true)

        assertNotEquals(primary, dual)
        assertEquals(dual, ShapeRoundnessJitter.factors(93, 5, 1f, 1f, dual = true))
    }
}
