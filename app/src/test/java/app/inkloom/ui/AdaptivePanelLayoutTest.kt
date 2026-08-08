package app.inkloom.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePanelLayoutTest {
    @Test
    fun sidePanelWidthTracksWindowAndStaysWithinUsableLimits() {
        assertEquals(240f, AdaptivePanelLayout.sidePanelWidthDp(700), 0f)
        assertEquals(345.6f, AdaptivePanelLayout.sidePanelWidthDp(1280), 0.01f)
        assertEquals(420f, AdaptivePanelLayout.sidePanelWidthDp(2400), 0f)
    }

    @Test
    fun automaticBrushLayoutFollowsTheActualPanelWidth() {
        assertEquals(BrushLibraryLayout.COMPACT, AdaptivePanelLayout.brushLayout(BrushLibraryLayout.AUTO, 240f, false))
        assertEquals(BrushLibraryLayout.LIST, AdaptivePanelLayout.brushLayout(BrushLibraryLayout.AUTO, 300f, false))
        assertEquals(BrushLibraryLayout.GRID, AdaptivePanelLayout.brushLayout(BrushLibraryLayout.AUTO, 360f, false))
        assertEquals(BrushLibraryLayout.GRID, AdaptivePanelLayout.brushLayout(BrushLibraryLayout.AUTO, 240f, true))
        assertEquals(BrushLibraryLayout.LIST, AdaptivePanelLayout.brushLayout(BrushLibraryLayout.LIST, 240f, true))
    }
}
