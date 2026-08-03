package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePanelTabsTest {
    @Test
    fun malformedOrderIsDeduplicatedAndCompleted() {
        assertEquals(
            listOf(WorkspacePanelKind.LAYERS, WorkspacePanelKind.PAGES, WorkspacePanelKind.BRUSHES),
            WorkspacePanelTabs.normalizeOrder("LAYERS,invalid,LAYERS,PAGES"),
        )
    }

    @Test
    fun movingATabKeepsACompleteStableOrder() {
        val moved = WorkspacePanelTabs.move("PAGES,BRUSHES,LAYERS", WorkspacePanelKind.LAYERS, -1)
        assertEquals("PAGES,LAYERS,BRUSHES", moved)
        assertEquals(moved, WorkspacePanelTabs.move(moved, WorkspacePanelKind.PAGES, -1))
    }

    @Test
    fun dockStackingAndActiveTabsAreIndependent() {
        val settings = AppSettings()
        val stacked = WorkspacePanelTabs.withStacked(settings, WorkspacePanelDock.RIGHT, true)
        val selected = WorkspacePanelTabs.withActive(stacked, WorkspacePanelDock.RIGHT, WorkspacePanelKind.LAYERS)

        assertTrue(WorkspacePanelTabs.isStacked(selected, WorkspacePanelDock.RIGHT))
        assertFalse(WorkspacePanelTabs.isStacked(selected, WorkspacePanelDock.LEFT))
        assertFalse(WorkspacePanelTabs.isStacked(selected, WorkspacePanelDock.FREE))
        assertEquals(WorkspacePanelKind.LAYERS, WorkspacePanelTabs.active(selected, WorkspacePanelDock.RIGHT))

        val floating = WorkspacePanelTabs.withActive(
            WorkspacePanelTabs.withStacked(selected, WorkspacePanelDock.FREE, true),
            WorkspacePanelDock.FREE,
            WorkspacePanelKind.PAGES,
        )
        assertTrue(WorkspacePanelTabs.isStacked(floating, WorkspacePanelDock.FREE))
        assertEquals(WorkspacePanelKind.PAGES, WorkspacePanelTabs.active(floating, WorkspacePanelDock.FREE))
        assertEquals(WorkspacePanelKind.LAYERS, WorkspacePanelTabs.active(floating, WorkspacePanelDock.RIGHT))
    }
}
