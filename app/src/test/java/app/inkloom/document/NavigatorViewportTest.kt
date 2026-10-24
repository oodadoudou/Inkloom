package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorViewportTest {
    private val view = 800f to 1000f
    private val window = 1200f to 1000f

    @Test fun theOutlineIsTheWholeWindowAroundTheFittedPage() {
        val outline = ViewportTransform.visibleOutline(ViewportState(1f, 0f, 0f), window.first, window.second, view.first, view.second)
        // The 800-px page sits in the middle of the 1200-px window, so the window reaches 200 px past each side.
        assertEquals(-200f, outline[0], .01f); assertEquals(0f, outline[1], .01f)
        assertEquals(1000f, outline[4], .01f); assertEquals(1000f, outline[5], .01f)
    }

    @Test fun zoomingInShrinksTheOutlineAroundWhatIsShown() {
        val zoomed = ViewportState(2f, 0f, 0f)
        val outline = ViewportTransform.visibleOutline(zoomed, window.first, window.second, view.first, view.second)
        assertEquals(100f, outline[0], .01f); assertEquals(250f, outline[1], .01f)
        assertEquals(700f, outline[4], .01f); assertEquals(750f, outline[5], .01f)
    }

    @Test fun centringOnAPointBringsItToTheWindowCentre() {
        listOf(ViewportState(2f, 0f, 0f), ViewportState(3f, 40f, -20f, 30f), ViewportState(2f, 0f, 0f, 0f, mirrored = true)).forEach { start ->
            val moved = ViewportTransform.centerOn(start, 200f, 300f, view.first, view.second)
            val (x, y) = ViewportTransform.pageToView(200f, 300f, view.first, view.second, moved)
            assertEquals("$start", view.first / 2f, x, .01f); assertEquals("$start", view.second / 2f, y, .01f)
            assertEquals(start.scale, moved.scale, 0f); assertEquals(start.rotationDegrees, moved.rotationDegrees, 0f)
        }
    }
}
