package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspacePanelGroupingTest {
    @Test fun overlappingFloatingPanelSelectsTheStrongestCandidate() {
        val dragged = WorkspacePanelBounds(300f, 100f, 300f, 500f)
        val candidates = mapOf(
            WorkspacePanelKind.PAGES to WorkspacePanelBounds(320f, 120f, 280f, 480f),
            WorkspacePanelKind.LAYERS to WorkspacePanelBounds(500f, 100f, 300f, 500f),
        )

        assertEquals(WorkspacePanelKind.PAGES, WorkspacePanelGrouping.target(WorkspacePanelKind.BRUSHES, dragged, candidates))
    }

    @Test fun weakOverlapAndTheDraggedPanelItselfAreIgnored() {
        val dragged = WorkspacePanelBounds(300f, 100f, 300f, 500f)
        val candidates = mapOf(
            WorkspacePanelKind.BRUSHES to dragged,
            WorkspacePanelKind.LAYERS to WorkspacePanelBounds(480f, 100f, 300f, 500f),
        )

        assertNull(WorkspacePanelGrouping.target(WorkspacePanelKind.BRUSHES, dragged, candidates))
        assertEquals(.4f, WorkspacePanelGrouping.overlapRatio(dragged, candidates.getValue(WorkspacePanelKind.LAYERS)), .0001f)
    }

    @Test fun invalidOrZeroSizedBoundsNeverGroup() {
        assertEquals(0f, WorkspacePanelGrouping.overlapRatio(
            WorkspacePanelBounds(0f, 0f, 0f, 200f),
            WorkspacePanelBounds(0f, 0f, 100f, 200f),
        ), .0001f)
    }
}
