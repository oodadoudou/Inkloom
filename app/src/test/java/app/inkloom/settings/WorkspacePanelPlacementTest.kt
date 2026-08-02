package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePanelPlacementTest {
    @Test fun floatingPanelCannotBeDraggedOutsideTheWorkspace() {
        val result = WorkspacePanelPlacement.clamp(4_000f, -500f, 330f, 600f, 1_200f, 800f, 160f)

        assertEquals(862f, result.xDp, .001f)
        assertEquals(8f, result.yDp, .001f)
        assertEquals(330f, result.widthDp, .001f)
        assertEquals(600f, result.heightDp, .001f)
    }

    @Test fun resizeRespectsPanelAndWorkspaceLimits() {
        val source = WorkspacePanelBounds(700f, 300f, 330f, 400f)
        val result = WorkspacePanelPlacement.resize(source, 1_000f, 1_000f, 1_200f, 800f, 160f)

        assertEquals(520f, result.widthDp, .001f)
        assertEquals(784f, result.heightDp, .001f)
        assertEquals(672f, result.xDp, .001f)
        assertEquals(8f, result.yDp, .001f)
    }

    @Test fun malformedValuesRecoverToVisibleFiniteBounds() {
        val result = WorkspacePanelPlacement.clamp(Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, 1_000f, 700f, 150f)

        assertEquals(8f, result.xDp, .001f)
        assertEquals(8f, result.yDp, .001f)
        assertEquals(150f, result.widthDp, .001f)
        assertEquals(220f, result.heightDp, .001f)
    }

    @Test fun narrowWorkspaceKeepsEntirePanelRecoverable() {
        val result = WorkspacePanelPlacement.clamp(50f, 50f, 400f, 500f, 120f, 180f, 160f)

        assertEquals(8f, result.xDp, .001f)
        assertEquals(8f, result.yDp, .001f)
        assertEquals(104f, result.widthDp, .001f)
        assertEquals(164f, result.heightDp, .001f)
    }
}
