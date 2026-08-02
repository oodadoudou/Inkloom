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
}
