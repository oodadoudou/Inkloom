package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuousPageStripTest {
    private val heights = listOf(1000f, 1000f, 600f, 1000f)
    private val gap = 40f

    @Test fun centreDistancesStackPagesWithGaps() {
        assertEquals(1040f, ContinuousPageStrip.centerDistance(heights, gap, 0, 1), .01f)
        assertEquals(500f + 40f + 300f, ContinuousPageStrip.centerDistance(heights, gap, 1, 2), .01f)
        assertEquals(-(500f + 1000f + 300f + 80f), ContinuousPageStrip.centerDistance(heights, gap, 2, 0), .01f)
    }

    @Test fun theActivePageStaysUntilANeighbourIsCloserToTheWindowCentre() {
        // Scrolled up by less than half the distance to the next centre: page 0 stays active.
        assertNull(ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 1f, -500f))
        val next = ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 1f, -540f)!!
        assertEquals(1, next.index)
        assertEquals(-540f + 1040f, next.rebasedPanY, .01f)
        // Scrolling back down past the midpoint returns to page 0, keeping the view still.
        val back = ContinuousPageStrip.switchAfterScroll(heights, gap, 1, 1f, 530f)!!
        assertEquals(0, back.index)
        assertEquals(530f - 1040f, back.rebasedPanY, .01f)
    }

    @Test fun zoomScalesTheDistances() {
        assertNull(ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 2f, -1000f))
        assertEquals(1, ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 2f, -1100f)!!.index)
    }

    @Test fun aLongFlingCanSkipSeveralPages() {
        val switch = ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 1f, -2900f)!!
        assertEquals(3, switch.index)
        assertEquals(-2900f + ContinuousPageStrip.centerDistance(heights, gap, 0, 3), switch.rebasedPanY, .01f)
    }

    @Test fun onlyNeighboursInsideTheWindowAreShown() {
        val shown = ContinuousPageStrip.visibleNeighbours(heights, gap, 1, 1f, 0f, windowHeight = 1200f)
        assertEquals(listOf(0, 2), shown.map { it.index })
        val zoomed = ContinuousPageStrip.visibleNeighbours(heights, gap, 1, 3f, 0f, windowHeight = 1200f)
        assertEquals(emptyList<Int>(), zoomed.map { it.index })
    }

    @Test fun invalidInputSwitchesNothing() {
        assertNull(ContinuousPageStrip.switchAfterScroll(heights, gap, 9, 1f, -900f))
        assertNull(ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 0f, -900f))
        assertNull(ContinuousPageStrip.switchAfterScroll(heights, gap, 0, 1f, Float.NaN))
    }
}
