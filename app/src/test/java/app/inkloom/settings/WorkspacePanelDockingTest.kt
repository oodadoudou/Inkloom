package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspacePanelDockingTest {
    @Test fun nearestEligibleEdgeWinsAndBottomWinsExactCornerTies() {
        assertEquals(WorkspacePanelDock.LEFT, WorkspacePanelDocking.target(WorkspacePanelBounds(8f, 100f, 300f, 400f), 1_200f, 800f))
        assertEquals(WorkspacePanelDock.RIGHT, WorkspacePanelDocking.target(WorkspacePanelBounds(892f, 100f, 300f, 400f), 1_200f, 800f))
        assertEquals(WorkspacePanelDock.BOTTOM, WorkspacePanelDocking.target(WorkspacePanelBounds(400f, 392f, 300f, 400f), 1_200f, 800f))
        assertEquals(WorkspacePanelDock.BOTTOM, WorkspacePanelDocking.target(WorkspacePanelBounds(892f, 392f, 300f, 400f), 1_200f, 800f))
    }

    @Test fun panelAwayFromEdgesHasNoDockTarget() {
        assertNull(WorkspacePanelDocking.target(WorkspacePanelBounds(300f, 100f, 300f, 400f), 1_200f, 800f))
        assertNull(WorkspacePanelDocking.target(WorkspacePanelBounds(8f, 8f, 300f, 400f), 0f, 800f))
    }

    @Test fun dragDirectionDisambiguatesAFullHeightPanelAtACorner() {
        val fullHeightAtLeft = WorkspacePanelBounds(8f, 8f, 330f, 784f)

        assertEquals(WorkspacePanelDock.LEFT, WorkspacePanelDocking.target(fullHeightAtLeft, 1_200f, 800f, -80f, 5f))
        assertEquals(WorkspacePanelDock.BOTTOM, WorkspacePanelDocking.target(fullHeightAtLeft, 1_200f, 800f, 5f, 80f))
        assertNull(WorkspacePanelDocking.target(fullHeightAtLeft, 1_200f, 800f, 80f, 5f))
    }

    @Test fun previewBoundsStayInsideTheWorkspace() {
        assertEquals(WorkspacePanelBounds(8f, 8f, 336f, 784f), WorkspacePanelDocking.previewBounds(WorkspacePanelDock.LEFT, 1_200f, 800f))
        assertEquals(WorkspacePanelBounds(856f, 8f, 336f, 784f), WorkspacePanelDocking.previewBounds(WorkspacePanelDock.RIGHT, 1_200f, 800f))
        assertEquals(WorkspacePanelBounds(8f, 520f, 1_184f, 272f), WorkspacePanelDocking.previewBounds(WorkspacePanelDock.BOTTOM, 1_200f, 800f))
        assertNull(WorkspacePanelDocking.previewBounds(WorkspacePanelDock.FREE, 1_200f, 800f))
    }
}
