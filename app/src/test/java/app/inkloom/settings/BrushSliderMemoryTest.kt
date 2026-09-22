package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushSliderMemoryTest {
    @Test fun fourSortedMarksCanBeSnappedBypassedAndRemoved() {
        val marks = listOf(.75f, .25f, .5f, .9f).fold(emptyList<Float>(), BrushSliderMemory::toggle)
        assertEquals(listOf(.25f, .5f, .75f, .9f), marks)
        assertEquals(marks, BrushSliderMemory.toggle(marks, .1f))
        assertEquals(.5f, BrushSliderMemory.snap(marks, .525f), .0001f)
        assertEquals(.525f, BrushSliderMemory.snap(marks, .525f, bypass = true), .0001f)
        assertEquals(listOf(.25f, .75f, .9f), BrushSliderMemory.toggle(marks, .51f))
    }
}
