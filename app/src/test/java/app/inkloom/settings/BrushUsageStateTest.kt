package app.inkloom.settings

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BrushUsageStateTest {
    @Test fun `borrowing a paint tip remembers an independent eraser choice`() {
        val state = BrushUsageState().used("ink", eraser = false)
            .borrowPaintTipForEraser("ink")
        val restored = Json.decodeFromString<BrushUsageState>(Json.encodeToString(state))
        assertEquals("ink", restored.lastPaintBrushId)
        assertEquals("ink", restored.lastEraserBrushId)
        assertTrue(restored.eraserUsesPaintTip)
        val ordinaryEraser = restored.used("soft eraser", eraser = true)
        assertEquals("soft eraser", ordinaryEraser.lastEraserBrushId)
        assertFalse(ordinaryEraser.eraserUsesPaintTip)
    }

    @Test fun `borrowed smudge tip survives reload and a normal blend selection replaces it`() {
        val state = BrushUsageState().used("ink", eraser = false).borrowPaintTipForSmudge("ink")
        val restored = Json.decodeFromString<BrushUsageState>(Json.encodeToString(state))
        assertEquals("ink", restored.lastSmudgeBrushId)
        assertTrue(restored.smudgeUsesPaintTip)
        assertFalse(restored.useSmudgeBrush("factory-blend").smudgeUsesPaintTip)
    }

    @Test fun currentListPinsAreIndependentUnlimitedAndRestoreSourceOrder() {
        val ids = (0..1023).map { "brush-$it" }
        var state = BrushUsageState()
        ids.forEach { state = state.toggleCategoryPin("CSP/Ink", it) }
        assertEquals(ids.reversed(), state.orderInCategory("CSP/Ink", ids))
        assertEquals(ids, state.orderInCategory("All", ids))
        assertTrue(state.pinnedIds.isEmpty())
        state = Json.decodeFromString<BrushUsageState>(Json.encodeToString(state)).used(ids.first())
        assertEquals(ids.reversed(), state.orderInCategory("CSP/Ink", ids))
        ids.forEach { state = state.toggleCategoryPin("CSP/Ink", it) }
        assertEquals(ids, state.orderInCategory("CSP/Ink", ids))
        assertTrue(state.categoryPins.isEmpty())
    }

    @Test fun browsingLocationSurvivesSerializationAndToolChanges() {
        val state = BrushUsageState(category = "CSP/Ink", scrollPositions = mapOf(
            "list:CSP/Ink" to BrushLibraryScrollPosition(25, 37),
            "grid:CSP/Ink" to BrushLibraryScrollPosition(12, 9)),
            categoryScroll = BrushLibraryScrollPosition(7, 11), lastViewedBrushId = "paint")
        val restored = Json.decodeFromString<BrushUsageState>(Json.encodeToString(state))
        assertEquals(state, restored)
        val after = restored.used("eraser", true).used("paint", false).pin("paint")
        assertEquals(state.category, after.category)
        assertEquals(state.scrollPositions, after.scrollPositions)
        assertEquals(state.categoryScroll, after.categoryScroll)
        assertEquals("paint", after.lastViewedBrushId)
        assertNull(Json.decodeFromString<BrushUsageState>("{\"category\":\"Pencil\"}").lastViewedBrushId)
    }

    @Test fun `tool return choices remain independent even for pinned factory brushes`() {
        val state = BrushUsageState().pin("system-hb-pencil")
            .used("system-hb-pencil", eraser = false).used("soft-eraser", eraser = true)
        assertEquals("system-hb-pencil", state.lastPaintBrushId)
        assertEquals("soft-eraser", state.lastEraserBrushId)
        assertEquals(listOf("soft-eraser"), state.recentIds)
        assertEquals("system-hb-pencil", state.clear("system-hb-pencil").lastPaintBrushId)
    }

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
