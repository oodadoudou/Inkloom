package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInWorkspaceTest {
    @Test fun `procreate workspace starts with popovers hidden and left controls visible`() {
        val original = AppSettings(fingerDrawing = true, penSize = 29f)

        val restored = original.applyToolboxWorkspace(BuiltInWorkspaces.snapshot(BuiltInWorkspace.STUDIO))

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

    @Test fun `reading and minimal layouts remain available as presentation presets`() {
        val reading = AppSettings().applyToolboxWorkspace(BuiltInWorkspaces.snapshot(BuiltInWorkspace.READING))
        val minimal = AppSettings().applyToolboxWorkspace(BuiltInWorkspaces.snapshot(BuiltInWorkspace.MINIMAL))

        assertFalse(reading.showToolbar)
        assertTrue(reading.showPageRail)
        assertFalse(minimal.showPageRail)
        assertFalse(minimal.showBrushPanel)
        assertFalse(minimal.showLayerPanel)
        assertTrue(minimal.showToolbar)
    }

    @Test fun `applying a preset to portrait does not overwrite landscape layout`() {
        val landscape = AppSettings(showPageRail = false, brushPanelDock = WorkspacePanelDock.BOTTOM)
            .recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE)
        val portrait = landscape.withWorkspaceFor(WorkspaceOrientation.PORTRAIT)
        val updatedPortrait = portrait.applyToolboxWorkspace(BuiltInWorkspaces.snapshot(BuiltInWorkspace.READING))
            .recordWorkspaceFor(WorkspaceOrientation.PORTRAIT)

        assertEquals(false, updatedPortrait.workspaceLayouts.landscape?.showPageRail)
        assertEquals(WorkspacePanelDock.BOTTOM, updatedPortrait.workspaceLayouts.landscape?.brushPanelDock)
        assertEquals(true, updatedPortrait.workspaceLayouts.portrait?.showPageRail)
        assertEquals(false, updatedPortrait.workspaceLayouts.portrait?.showToolbar)
    }
}
