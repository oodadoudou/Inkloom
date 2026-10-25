package app.inkloom.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class HoverTooltipPositionTest {
    private val screen = IntRect(0, 48, 2560, 1552)
    private val name = IntSize(120, 40)

    @Test fun aRailToolOnTheLeftNamesItselfToItsRightSoTheNextToolStaysVisible() {
        assertEquals(IntOffset(76, 380), hoverTooltipPosition(IntRect(6, 368, 70, 432), screen, name, 6, beside = true))
    }

    @Test fun aRailDockedOnTheRightNamesItsToolsToTheirLeft() {
        assertEquals(IntOffset(2364, 380), hoverTooltipPosition(IntRect(2490, 368, 2554, 432), screen, name, 6, beside = true))
    }

    @Test fun aTopBarButtonNamesItselfBelowAndABottomBarButtonAbove() {
        assertEquals(IntOffset(240, 134), hoverTooltipPosition(IntRect(268, 64, 332, 128), screen, name, 6, beside = false))
        assertEquals(IntOffset(1220, 1394), hoverTooltipPosition(IntRect(1248, 1440, 1312, 1504), screen, name, 6, beside = false))
    }

    @Test fun theNameStaysOnScreenAtTheEdges() {
        assertEquals(IntOffset(2440, 134), hoverTooltipPosition(IntRect(2500, 64, 2556, 128), screen, name, 6, beside = false))
        assertEquals(IntOffset(76, 48), hoverTooltipPosition(IntRect(6, 40, 70, 60), screen, name, 6, beside = true))
    }
}
