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
    @Test fun paintingDocksColorsAboveLayersBesideTheCanvasWithoutCoveringIt() {
        val preset=BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING)
        assertTrue(EditorPanelLayouts.isValid(preset.layout))
        assertTrue(preset.layout.open.containsAll(setOf(EditorPanel.WHEEL,EditorPanel.PALETTES,EditorPanel.MIX,EditorPanel.NEARBY,EditorPanel.LAYERS)))
        val docked=EditorPanelLayouts.members(preset.layout.dock)
        assertTrue(EditorPanel.WHEEL in docked);assertTrue(EditorPanel.LAYERS in docked)
        assertTrue(preset.toolRail!!.visible);assertTrue(preset.toolRail!!.vertical)
        val slots=EditorPanelLayouts.slots(preset.layout,1280f,700f)
        val canvas=slots.single { it.node is PanelNode.Canvas }.rect
        val colors=slots.single { (it.node as? PanelNode.Tabs)?.panels?.contains(EditorPanel.WHEEL)==true }.rect
        val layers=slots.single { (it.node as? PanelNode.Tabs)?.panels?.contains(EditorPanel.LAYERS)==true }.rect
        assertTrue("Panels sit right of the canvas",colors.x>=canvas.x+canvas.width-1f&&layers.x>=canvas.x+canvas.width-1f)
        assertTrue("Colors above layers",colors.y+colors.height<=layers.y+1f)
        assertTrue("The canvas keeps most of the window",canvas.width>=800f)
    }
}
