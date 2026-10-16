package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSwipeTest {
    private val width = 2560f
    private val density = 2f

    @Test fun aQuickHorizontalFlickTurnsThePage() {
        assertEquals(PageSwipe.Direction.NEXT, PageSwipe.classify(-300f, 20f, 180, width, density))
        assertEquals(PageSwipe.Direction.PREVIOUS, PageSwipe.classify(300f, -20f, 180, width, density))
    }

    @Test fun aLongerHorizontalSwipeAlsoTurnsWithinTheTimeLimit() {
        assertEquals(PageSwipe.Direction.NEXT, PageSwipe.classify(-700f, 60f, 650, width, density))
    }

    @Test fun shortSlowDiagonalOrLongDragsStayPans() {
        assertNull("too short", PageSwipe.classify(-80f, 0f, 80, width, density))
        assertNull("too slow for its distance", PageSwipe.classify(-300f, 0f, 700, width, density))
        assertNull("not horizontal enough", PageSwipe.classify(-400f, 300f, 200, width, density))
        assertNull("a deliberate slow drag", PageSwipe.classify(-1200f, 0f, 1200, width, density))
    }

    @Test fun invalidInputNeverTurns() {
        assertNull(PageSwipe.classify(Float.NaN, 0f, 100, width, density))
        assertNull(PageSwipe.classify(-300f, 0f, 0, width, density))
        assertNull(PageSwipe.classify(-300f, 0f, 100, 0f, density))
    }
}
