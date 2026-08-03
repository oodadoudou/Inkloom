package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInWorkspaceTest {
    @Test fun `drawing workspace exposes brush and layer panels without pages`() {
        val original = AppSettings(fingerDrawing = true, penSize = 29f)

        val restored = BuiltInWorkspaces.apply(original, BuiltInWorkspace.DRAWING)

        assertFalse(restored.showPageRail)
        assertTrue(restored.showBrushPanel)
        assertTrue(restored.showLayerPanel)
        assertEquals(WorkspacePanelDock.LEFT, restored.brushPanelDock)
        assertEquals(WorkspacePanelDock.RIGHT, restored.layerPanelDock)
        assertEquals(ToolbarDock.LEFT, restored.toolbarDock)
        assertTrue(restored.verticalToolbar)
        assertTrue(restored.fingerDrawing)
        assertEquals(29f, restored.penSize)
    }

    @Test fun `note reading and minimal workspaces have distinct visibility`() {
        val note = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.NOTE)
        val reading = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.READING)
        val minimal = BuiltInWorkspaces.apply(AppSettings(), BuiltInWorkspace.MINIMAL)

        assertTrue(note.showPageRail)
        assertTrue(note.showToolbar)
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

        assertEquals(AppSettings().captureToolboxWorkspace(), restored.captureToolboxWorkspace())
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
