package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInWorkspaceTest {
    @Test fun `procreate workspace starts with popovers hidden and left controls visible`() {
        val original = AppSettings(fingerDrawing = true, penSize = 29f)

        val restored = BuiltInWorkspaces.apply(original, BuiltInWorkspace.STUDIO)

        assertFalse(restored.showPageRail)
        assertFalse(restored.showBrushPanel)
        assertFalse(restored.showLayerPanel)
        assertEquals(WorkspacePanelDock.FREE, restored.pagePanelDock)
        assertEquals(WorkspacePanelDock.FREE, restored.brushPanelDock)
        assertEquals(WorkspacePanelDock.FREE, restored.layerPanelDock)
        assertEquals(18f, restored.brushPanelYDp)
        assertEquals(18f, restored.layerPanelYDp)
        assertEquals(ToolbarDock.LEFT, restored.toolbarDock)
        assertTrue(restored.verticalToolbar)
        assertTrue(restored.fingerDrawing)
        assertEquals(29f, restored.penSize)
    }

    @Test fun `legacy note and drawing aliases resolve to the creative workspace`() {
        val note = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.NOTE)
        val drawing = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.DRAWING)

        assertEquals(BuiltInWorkspaces.snapshot(BuiltInWorkspace.STUDIO), note.captureToolboxWorkspace())
        assertEquals(BuiltInWorkspaces.snapshot(BuiltInWorkspace.STUDIO), drawing.captureToolboxWorkspace())
    }

    @Test fun `reading and minimal layouts remain available as presentation presets`() {
        val reading = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.READING)
        val minimal = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.MINIMAL)

        assertFalse(reading.showToolbar)
        assertTrue(reading.showPageRail)
        assertFalse(minimal.showPageRail)
        assertFalse(minimal.showBrushPanel)
        assertFalse(minimal.showLayerPanel)
        assertTrue(minimal.showToolbar)
    }

    @Test fun `reset restores default workspace only`() {
        val customized = AppSettings(
            language = UiLanguage.SIMPLIFIED_CHINESE,
            pressureSensitivity = 1.8f,
            showPageRail = false,
            showBrushPanel = true,
            brushPanelDock = WorkspacePanelDock.BOTTOM,
            toolbarDock = ToolbarDock.RIGHT,
        )

        val restored = BuiltInWorkspaces.apply(customized, BuiltInWorkspace.RESET)

        assertEquals(BuiltInWorkspaces.snapshot(BuiltInWorkspace.STUDIO), restored.captureToolboxWorkspace())
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, restored.language)
        assertEquals(1.8f, restored.pressureSensitivity)
    }

    @Test fun `applying a preset to portrait does not overwrite landscape layout`() {
        val landscape = AppSettings(showPageRail = false, brushPanelDock = WorkspacePanelDock.BOTTOM)
            .recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE)
        val portrait = landscape.withWorkspaceFor(WorkspaceOrientation.PORTRAIT)
        val updatedPortrait = BuiltInWorkspaces.apply(portrait, BuiltInWorkspace.READING)
            .recordWorkspaceFor(WorkspaceOrientation.PORTRAIT)

        assertEquals(false, updatedPortrait.workspaceLayouts.landscape?.showPageRail)
        assertEquals(WorkspacePanelDock.BOTTOM, updatedPortrait.workspaceLayouts.landscape?.brushPanelDock)
        assertEquals(true, updatedPortrait.workspaceLayouts.portrait?.showPageRail)
        assertEquals(false, updatedPortrait.workspaceLayouts.portrait?.showToolbar)
    }
}
