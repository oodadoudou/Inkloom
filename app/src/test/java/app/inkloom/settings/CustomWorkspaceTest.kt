package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWorkspaceTest {
    @Test fun `capture stores complete independent landscape and portrait layouts`() {
        val settings = AppSettings(
            showPageRail = false,
            showBrushPanel = true,
            brushPanelDock = WorkspacePanelDock.LEFT,
        ).recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE).copy(
            showPageRail = true,
            showBrushPanel = false,
            showLayerPanel = true,
            layerPanelDock = WorkspacePanelDock.BOTTOM,
        )

        val profile = CustomWorkspaces.capture("workspace-1", "  Drawing   desk  ", settings, WorkspaceOrientation.PORTRAIT)

        assertEquals("Drawing desk", profile.name)
        assertFalse(profile.layouts.landscape!!.showPageRail)
        assertTrue(profile.layouts.landscape!!.showBrushPanel)
        assertTrue(profile.layouts.portrait!!.showPageRail)
        assertTrue(profile.layouts.portrait!!.showLayerPanel)
        assertEquals(WorkspacePanelDock.BOTTOM, profile.layouts.portrait!!.layerPanelDock)
    }

    @Test fun `apply selects requested orientation and preserves unrelated preferences and library`() {
        val profile = CustomWorkspaceProfile(
            "workspace-1",
            "Focus",
            WorkspaceLayoutProfiles(
                landscape = AppSettings(showBrushPanel = true, brushPanelDock = WorkspacePanelDock.LEFT).captureToolboxWorkspace(),
                portrait = AppSettings(showPageRail = false, showLayerPanel = true, toolbarDock = ToolbarDock.BOTTOM).captureToolboxWorkspace(),
            ),
        )
        val settings = AppSettings(fingerDrawing = true, penSize = 31f, customWorkspaces = listOf(profile))

        val restored = CustomWorkspaces.apply(settings, profile, WorkspaceOrientation.PORTRAIT)

        assertFalse(restored.showPageRail)
        assertTrue(restored.showLayerPanel)
        assertEquals(ToolbarDock.BOTTOM, restored.toolbarDock)
        assertTrue(restored.fingerDrawing)
        assertEquals(31f, restored.penSize)
        assertEquals(listOf(profile), restored.customWorkspaces)
    }

    @Test fun `profile and library codecs round trip normalized values`() {
        val profile = CustomWorkspaces.capture("workspace-1", "Focus", AppSettings(showBrushPanel = true), WorkspaceOrientation.LANDSCAPE)

        val decodedProfile = CustomWorkspaceCodec.decodeProfile(CustomWorkspaceCodec.encodeProfile(profile)).getOrThrow()
        val decodedLibrary = CustomWorkspaceCodec.decodeLibrary(CustomWorkspaceCodec.encodeLibrary(listOf(profile)))

        assertEquals(profile, decodedProfile)
        assertEquals(listOf(profile), decodedLibrary)
    }

    @Test fun `profile decoder rejects malformed oversized and empty-layout files`() {
        assertTrue(CustomWorkspaceCodec.decodeProfile("not json".toByteArray()).isFailure)
        assertTrue(CustomWorkspaceCodec.decodeProfile(ByteArray(CustomWorkspaceCodec.MAX_FILE_BYTES + 1)).isFailure)
        assertTrue(CustomWorkspaceCodec.decodeProfile("""{"format":"inkloom-workspace","version":1,"workspace":{"id":"x","name":"Empty","layouts":{}}}""".toByteArray()).isFailure)
    }

    @Test fun `duplicate names receive deterministic suffixes`() {
        val existing = listOf(
            CustomWorkspaces.capture("1", "Drawing", AppSettings(), WorkspaceOrientation.LANDSCAPE),
            CustomWorkspaces.capture("2", "Drawing 2", AppSettings(), WorkspaceOrientation.LANDSCAPE),
        )

        assertEquals("Drawing 3", CustomWorkspaces.uniqueName("Drawing", existing))
    }
}
