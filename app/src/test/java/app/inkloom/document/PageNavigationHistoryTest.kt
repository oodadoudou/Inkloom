package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageNavigationHistoryTest {
    private val all = setOf("a", "b", "c", "d")

    @Test fun backReturnsToThePageAJumpLeftAndForwardRedoesIt() {
        val history = PageNavigationHistory().jumped(from = "a", to = "c")
        assertEquals("a", history.backTarget("c", all))
        val (target, afterBack) = requireNotNull(history.goBack("c", all))
        assertEquals("a", target)
        assertNull(afterBack.backTarget("a", all))
        assertEquals("c", afterBack.forwardTarget("a", all))
        val (forward, afterForward) = requireNotNull(afterBack.goForward("a", all))
        assertEquals("c", forward)
        assertEquals("a", afterForward.backTarget("c", all))
        assertNull(afterForward.forwardTarget("c", all))
    }

    @Test fun aNewJumpClearsForwardHistory() {
        val (_, backed) = requireNotNull(PageNavigationHistory().jumped("a", "c").goBack("c", all))
        val jumped = backed.jumped("a", "d")
        assertNull(jumped.forwardTarget("d", all))
        assertEquals("a", jumped.backTarget("d", all))
    }

    @Test fun jumpingToTheCurrentPageOrRepeatingTheSameOriginAddsNothing() {
        assertEquals(PageNavigationHistory(), PageNavigationHistory().jumped("a", "a"))
        val history = PageNavigationHistory().jumped("a", "b").jumped("b", "a").jumped("a", "c")
        assertEquals(listOf("a", "b", "a"), history.back)
        assertEquals(listOf("a"), PageNavigationHistory().jumped("a", "b").jumped("a", "c").back)
    }

    @Test fun deletedPagesAndTheCurrentPageAreSkipped() {
        val history = PageNavigationHistory().jumped("a", "b").jumped("b", "c").jumped("c", "d")
        val remaining = setOf("a", "c", "d")
        val (target, after) = requireNotNull(history.goBack("d", remaining))
        assertEquals("c", target)
        assertEquals("a", after.backTarget("c", remaining))
        assertNull(PageNavigationHistory(back = listOf("x", "d")).goBack("d", all))
        assertFalse(PageNavigationHistory(back = listOf("x")).canGoBack("a", all))
    }

    @Test fun historyIsBoundedToTheMostRecentJumps() {
        var history = PageNavigationHistory()
        repeat(PageNavigationHistory.MAX_ENTRIES + 10) { index -> history = history.jumped("p$index", "p${index + 1}") }
        assertEquals(PageNavigationHistory.MAX_ENTRIES, history.back.size)
        assertEquals("p${PageNavigationHistory.MAX_ENTRIES + 9}", history.back.last())
    }

    @Test fun retainDropsDeletedPagesAndCollapsesNeighbours() {
        val history = PageNavigationHistory(back = listOf("a", "b", "a", "c"), forward = listOf("d", "b"))
        val retained = history.retain(setOf("a", "c", "d"))
        assertEquals(listOf("a", "c"), retained.back)
        assertEquals(listOf("d"), retained.forward)
        assertTrue(retained.canGoForward("x", setOf("a", "c", "d")))
    }
}
