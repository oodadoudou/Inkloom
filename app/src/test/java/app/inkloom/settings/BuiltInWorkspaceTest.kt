package app.inkloom.settings

import org.junit.Assert.*
import org.junit.Test

class BuiltInWorkspaceTest {
    @Test fun studioStartsWithPopoversHiddenAndLeftControlsVisible() {
        val original=AppSettings(fingerDrawing=true,penSize=29f)
        val preset=BuiltInWorkspaces.snapshot(BuiltInWorkspace.STUDIO)
        val restored=preset.toolRail!!.applyTo(original)
        assertTrue(preset.layout.open.isEmpty());assertTrue(EditorPanelLayouts.isValid(preset.layout))
        assertEquals(ToolbarDock.LEFT,restored.toolbarDock);assertTrue(restored.verticalToolbar)
        assertTrue(restored.fingerDrawing);assertEquals(29f,restored.penSize)
    }
    @Test fun readingUsesTheSameTreeWithPagesDockedAndControlsHidden() {
        val preset=BuiltInWorkspaces.snapshot(BuiltInWorkspace.READING)
        assertEquals(setOf(EditorPanel.PAGES),preset.layout.open)
        assertTrue(EditorPanel.PAGES in EditorPanelLayouts.members(preset.layout.dock))
        assertFalse(preset.toolRail!!.visible)
    }
}
