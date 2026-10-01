package app.inkloom.ui

import androidx.compose.ui.unit.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PageJumpPositionTest {
    @Test fun bottomNavigatorPositionsPopupAboveItAndClampsHorizontalEdge() {
        assertEquals(IntOffset(712, 252), PageJumpPosition(IntRect(820, 660, 980, 700), 8).calculatePosition(
            IntRect.Zero, IntSize(1000, 720), LayoutDirection.Ltr, IntSize(280, 400)))
    }
    @Test fun topNavigatorFallsBelowAndRespectsSystemInsets() {
        assertEquals(IntOffset(8, 98), PageJumpPosition(IntRect(8, 50, 168, 90), 8, 24, 24).calculatePosition(
            IntRect.Zero, IntSize(1000, 720), LayoutDirection.Rtl, IntSize(280, 400)))
    }
}
