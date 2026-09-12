package app.inkloom.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPaletteMenuPositionTest {
    private val screen = IntRect(0, 24, 2560, 1552)

    @Test fun nestedSwatchMenuIncludesContainingPopupOffset() {
        val actual = colorPaletteMenuPosition(IntRect(80, 300, 112, 332), IntOffset(1632, 80),
            screen, IntSize(280, 120), LayoutDirection.Ltr, 8)
        assertEquals(IntOffset(1712, 412), actual)
    }

    @Test fun correctedAnchorFlipsAboveWithoutReversingParentOffset() {
        val actual = colorPaletteMenuPosition(IntRect(758, 1100, 790, 1132), IntOffset(1632, 300),
            screen, IntSize(300, 400), LayoutDirection.Ltr, 8)
        assertEquals(IntOffset(2122, 1000), actual)
    }

    @Test fun rtlStartsAtCorrectedRightEdge() {
        val actual = colorPaletteMenuPosition(IntRect(300, 200, 332, 232), IntOffset(1632, 80),
            screen, IntSize(280, 120), LayoutDirection.Rtl, 8)
        assertEquals(IntOffset(1684, 312), actual)
    }

    @Test fun ordinaryFloatingWindowKeepsWindowRelativeCoordinates() {
        val actual = colorPaletteMenuPosition(IntRect(100, 80, 132, 112), IntOffset.Zero,
            IntRect(0, 0, 800, 600), IntSize(200, 120), LayoutDirection.Ltr, 8)
        assertEquals(IntOffset(100, 112), actual)
    }

    @Test fun clippedVisibleFrameConstrainsBothEdges() {
        val actual = colorPaletteMenuPosition(IntRect(-100, -100, -68, -68), IntOffset(600, 300),
            IntRect(800, 400, 1500, 1000), IntSize(280, 240), LayoutDirection.Ltr, 8)
        assertEquals(IntOffset(808, 408), actual)
    }

    @Test fun oversizedMenuHasDeterministicTopLeftWithoutInvalidClampRange() {
        val actual = colorPaletteMenuPosition(IntRect(40, 50, 60, 70), IntOffset.Zero,
            IntRect(0, 0, 100, 80), IntSize(200, 160), LayoutDirection.Ltr, 8)
        assertEquals(IntOffset(8, 8), actual)
    }
}
