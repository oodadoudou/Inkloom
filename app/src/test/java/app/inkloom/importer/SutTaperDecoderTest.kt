package app.inkloom.importer

import app.inkloom.document.BrushTaperMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SutTaperDecoderTest {
    @Test
    fun specifyLengthPreservesAbsoluteValueAndUnit() {
        val value = SutTaperDecoder.decode(0, true, 20f, 30f, 0, true, 15f, 18f, 2, true, .18f)
        assertEquals(BrushTaperMode.DISTANCE, value.mode)
        assertEquals(20f, value.startLength, .0001f)
        assertEquals(2, value.endLengthUnit)
        assertEquals(.18f, value.endMinimum, .0001f)
    }

    @Test
    fun percentageConvertsSliderValuesToFractions() {
        val value = SutTaperDecoder.decode(1, true, 15f, 18f, 2, true, 25f, 40f, 2, true, .2f)
        assertEquals(BrushTaperMode.PERCENTAGE, value.mode)
        assertEquals(.18f, value.startLength, .0001f)
        assertEquals(.4f, value.endLength, .0001f)
        assertTrue(value.startEnabled)
    }

    @Test
    fun fadeDoesNotEnableUnsupportedStartingPhase() {
        val value = SutTaperDecoder.decode(2, true, 50f, 20f, 0, true, 60f, 35f, 0, true, .35f)
        assertEquals(BrushTaperMode.FADE, value.mode)
        assertFalse(value.startEnabled)
        assertTrue(value.endEnabled)
    }

    @Test
    fun retainedRangesDoNotActivateAnUncheckedSizeTarget() {
        val value = SutTaperDecoder.decode(0, true, 20f, 30f, 0, true, 20f, 30f, 0, false, 0f)
        assertFalse(value.startEnabled)
        assertFalse(value.endEnabled)
    }
}
