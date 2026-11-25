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
        // The colour wheel alone, as the colour chips open it; other colour palettes join as tabs when opened.
        assertEquals(setOf(EditorPanel.WHEEL,EditorPanel.LAYERS), preset.layout.open)
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

    private fun rect(preset: NamedPanelLayout, panel: EditorPanel) =
        EditorPanelLayouts.slots(preset.layout, 1280f, 700f).single { (it.node as? PanelNode.Tabs)?.panels?.contains(panel) == true }.rect
    private fun canvas(preset: NamedPanelLayout) = EditorPanelLayouts.slots(preset.layout, 1280f, 700f).single { it.node is PanelNode.Canvas }.rect

    @Test fun saiDocksTwoColumnsLeftOfTheCanvasWithToolsOnTop() {
        val preset = BuiltInWorkspaces.snapshot(BuiltInWorkspace.SAI)
        assertTrue(EditorPanelLayouts.isValid(preset.layout))
        val canvas = canvas(preset)
        val navigator = rect(preset, EditorPanel.NAVIGATOR); val layers = rect(preset, EditorPanel.LAYERS)
        val colors = rect(preset, EditorPanel.WHEEL); val brushes = rect(preset, EditorPanel.BRUSHES)
        assertTrue("all panels left of the canvas", listOf(navigator, layers, colors, brushes).all { it.right <= canvas.x + 1f })
        assertTrue("navigator over layers", navigator.bottom <= layers.y + 1f)
        assertTrue("colour over tools", colors.bottom <= brushes.y + 1f)
        assertTrue("navigator and layers outside, colour beside the canvas", layers.right <= colors.x + 1f)
        assertEquals(ToolbarDock.TOP, preset.toolRail!!.applyTo(AppSettings()).toolbarDock)
        assertFalse(preset.toolRail!!.vertical)
        assertTrue("the canvas keeps most of the window", canvas.width >= 700f)
    }

    @Test fun photoshopStacksColorPropertiesAndLayersOnTheRightWithToolsLeft() {
        val preset = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PHOTOSHOP)
        assertTrue(EditorPanelLayouts.isValid(preset.layout))
        val canvas = canvas(preset)
        val colors = rect(preset, EditorPanel.WHEEL); val properties = rect(preset, EditorPanel.PROPERTIES); val layers = rect(preset, EditorPanel.LAYERS)
        assertTrue("right of the canvas", listOf(colors, properties, layers).all { it.x >= canvas.right - 1f })
        assertTrue("color, then properties, then layers", colors.bottom <= properties.y + 1f && properties.bottom <= layers.y + 1f)
        assertEquals(ToolbarDock.LEFT, preset.toolRail!!.applyTo(AppSettings()).toolbarDock)
        assertTrue(preset.toolRail!!.vertical)
    }

    @Test fun procreateDocksNothingAndRunsToolsAcrossTheTop() {
        val preset = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PROCREATE)
        assertTrue(preset.layout.open.isEmpty())
        assertTrue(EditorPanelLayouts.members(preset.layout.dock).isEmpty())
        assertEquals(ToolbarDock.TOP, preset.toolRail!!.applyTo(AppSettings()).toolbarDock)
        assertFalse(preset.toolRail!!.vertical)
    }
}
