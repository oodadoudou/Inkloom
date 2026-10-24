package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingToolbarPlacementTest {
    @Test fun freePaletteStaysFullyVisibleAfterArbitraryDragging() {
        val position = FloatingToolbarPlacement.clamp(4_000f, -900f, 1_200f, 800f, vertical = false)

        assertEquals(832f, position.xDp, .001f)
        assertEquals(8f, position.yDp, .001f)
    }

    @Test fun verticalPaletteUsesItsActualLongEdgeForBounds() {
        val position = FloatingToolbarPlacement.clamp(4_000f, 1_000f, 1_200f, 800f, vertical = true)

        assertEquals(1_136f, position.xDp, .001f)
        assertEquals(432f, position.yDp, .001f)
    }

    @Test fun defaultHorizontalPositionIsCenteredNearTheTop() {
        val position = FloatingToolbarPlacement.defaultPosition(1_200f, 800f, vertical = false)

        assertEquals(420f, position.xDp, .001f)
        assertEquals(16f, position.yDp, .001f)
    }

    @Test fun malformedPersistedCoordinatesRecoverInsideTheCanvas() {
        val position = FloatingToolbarPlacement.clamp(Float.NaN, Float.POSITIVE_INFINITY, 1_200f, 800f, vertical = false)

        assertEquals(8f, position.xDp, .001f)
        assertEquals(8f, position.yDp, .001f)
    }

    @Test fun measuredRailBoundsAndReleasePositionChooseOnlyItsOwnEdges() {
        val position = FloatingToolbarPlacement.clamp(4_000f, 1_000f, 1_200f, 800f,
            vertical = true, measuredWidthDp = 88f, measuredHeightDp = 600f)
        assertEquals(1_104f, position.xDp, .001f)
        assertEquals(192f, position.yDp, .001f)
        assertEquals(ToolbarDock.RIGHT, FloatingToolbarPlacement.snapTarget(position, 1_200f, 800f,
            vertical = true, measuredWidthDp = 88f, measuredHeightDp = 600f))
        assertEquals(ToolbarDock.FREE, FloatingToolbarPlacement.snapTarget(
            FloatingToolbarPosition(300f, 8f), 1_200f, 800f,
            vertical = true, measuredWidthDp = 88f, measuredHeightDp = 600f))
        // A docked toolbar sits flush along its edge from the start; undocking starts it floating there.
        val left = FloatingToolbarPlacement.dockOrigin(ToolbarDock.LEFT, 1_200f, 800f, 88f, 600f)
        assertEquals(FloatingToolbarPlacement.MARGIN_DP, left.xDp, .001f)
        assertEquals(FloatingToolbarPlacement.MARGIN_DP, left.yDp, .001f)
        val right = FloatingToolbarPlacement.dockOrigin(ToolbarDock.RIGHT, 1_200f, 800f, 88f, 600f)
        assertEquals(1_200f - 88f - FloatingToolbarPlacement.MARGIN_DP, right.xDp, .001f)
        val bottom = FloatingToolbarPlacement.dockOrigin(ToolbarDock.BOTTOM, 1_200f, 800f, 360f, 56f)
        assertEquals(800f - 56f - FloatingToolbarPlacement.MARGIN_DP, bottom.yDp, .001f)
    }
}
