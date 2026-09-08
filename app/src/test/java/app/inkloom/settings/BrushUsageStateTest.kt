package app.inkloom.settings

import org.junit.Assert.*
import org.junit.Test

class BrushUsageStateTest {
    @Test fun `pinned collection is unlimited and does not consume recent slots`() {
        var state = BrushUsageState()
        repeat(1024) { state = state.pin("brush-$it") }
        repeat(20) { state = state.used("recent-$it") }
        assertEquals(1024, state.pinnedIds.size)
        assertEquals((19 downTo 12).map { "recent-$it" }, state.recentIds)
        assertEquals(state, state.used("brush-0"))
    }

    @Test fun `factory and imported ids have identical stable history semantics`() {
        val state = BrushUsageState().used("system-hb-pencil").used("imported-1").used("system-hb-pencil")
        assertEquals(listOf("system-hb-pencil", "imported-1"), state.recentIds)
        val pinned = state.pin("imported-1")
        assertEquals(listOf("imported-1", "system-hb-pencil"), pinned.displayedIds(setOf("system-hb-pencil", "imported-1")))
        assertEquals(listOf("system-hb-pencil"), pinned.displayedIds(setOf("system-hb-pencil")))
        assertEquals(listOf("imported-1", "system-hb-pencil"), pinned.unpin("imported-1").recentIds)
        assertEquals(listOf("system-hb-pencil"), pinned.clear("imported-1").recentIds)
        assertTrue(pinned.clear("imported-1").pinnedIds.isEmpty())
    }
}
