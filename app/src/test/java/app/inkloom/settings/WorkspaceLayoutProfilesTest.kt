package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutProfilesTest {
    @Test fun `records and restores independent landscape and portrait workspaces`() {
        val landscape = AppSettings(
            showPageRail = false,
            showBrushPanel = true,
            brushPanelDock = WorkspacePanelDock.FREE,
            brushPanelXDp = 840f,
            toolbarDock = ToolbarDock.LEFT,
        ).recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE)
        val portrait = landscape.copy(
            showPageRail = true,
            showBrushPanel = false,
            brushPanelDock = WorkspacePanelDock.RIGHT,
            brushPanelXDp = 24f,
            toolbarDock = ToolbarDock.BOTTOM,
        ).recordWorkspaceFor(WorkspaceOrientation.PORTRAIT)

        val restoredLandscape = portrait.withWorkspaceFor(WorkspaceOrientation.LANDSCAPE)
        assertFalse(restoredLandscape.showPageRail)
        assertTrue(restoredLandscape.showBrushPanel)
        assertEquals(WorkspacePanelDock.FREE, restoredLandscape.brushPanelDock)
        assertEquals(840f, restoredLandscape.brushPanelXDp)
        assertEquals(ToolbarDock.LEFT, restoredLandscape.toolbarDock)

        val restoredPortrait = restoredLandscape.withWorkspaceFor(WorkspaceOrientation.PORTRAIT)
        assertTrue(restoredPortrait.showPageRail)
        assertFalse(restoredPortrait.showBrushPanel)
        assertEquals(WorkspacePanelDock.RIGHT, restoredPortrait.brushPanelDock)
        assertEquals(24f, restoredPortrait.brushPanelXDp)
        assertEquals(ToolbarDock.BOTTOM, restoredPortrait.toolbarDock)
    }

    @Test fun `missing second orientation uses defaults without overwriting saved orientation`() {
        val landscape = AppSettings(showPageRail = false, brushPanelXDp = 999f)
            .recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE)

        val portrait = landscape.withWorkspaceFor(WorkspaceOrientation.PORTRAIT)

        assertTrue(portrait.showPageRail)
        assertEquals(720f, portrait.brushPanelXDp)
        assertEquals(999f, portrait.workspaceLayouts.landscape?.brushPanelXDp)
        assertEquals(720f, portrait.workspaceLayouts.portrait?.brushPanelXDp)
    }

    @Test fun `codec round trip clamps workspaces and rejects malformed input`() {
        val profiles = WorkspaceLayoutProfiles(
            landscape = AppSettings(brushPanelXDp = 99999f).captureToolboxWorkspace(),
            portrait = AppSettings(toolbarOffsetYDp = -99999f).captureToolboxWorkspace(),
        )

        val restored = WorkspaceLayoutProfilesCodec.decode(WorkspaceLayoutProfilesCodec.encode(profiles))

        assertEquals(2400f, restored.landscape?.brushPanelXDp)
        assertEquals(-1200f, restored.portrait?.toolbarOffsetYDp)
        assertEquals(WorkspaceLayoutProfiles(), WorkspaceLayoutProfilesCodec.decode("not json"))
        assertNull(WorkspaceLayoutProfilesCodec.decode("{}").portrait)
    }
}
