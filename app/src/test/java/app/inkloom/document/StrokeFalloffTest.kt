package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFalloffTest {
    @Test fun disabledFalloffDoesNotAllocateFactors() {
        assertNull(StrokeFalloff.factors(listOf(StrokePoint(0f, 0f)), 400f, 600f, 20f, 0f))
    }

    @Test fun opacityDecaysMonotonicallyByPhysicalPathDistance() {
        val factors = requireNotNull(StrokeFalloff.factors(
            listOf(StrokePoint(0f, .5f), StrokePoint(.25f, .5f), StrokePoint(.5f, .5f), StrokePoint(1f, .5f)),
            400f,
            600f,
            40f,
            .75f,
        ))

        assertEquals(1f, factors.first(), .0001f)
        assertTrue((0 until factors.lastIndex).all { index -> factors[index + 1] < factors[index] })
        assertTrue(factors.last() < .01f)
    }
}
