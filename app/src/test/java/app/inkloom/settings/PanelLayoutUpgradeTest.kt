package app.inkloom.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLayoutUpgradeTest {
    /** A layout saved before the navigator existed: every other panel, docked and floating as the user left them. */
    private val older = EditorPanelLayout().let { layout ->
        layout.copy(floating = layout.floating.filterNot { EditorPanel.NAVIGATOR in EditorPanelLayouts.members(it.node) }
            .map { if (EditorPanel.PAGES in EditorPanelLayouts.members(it.node)) it.copy(rect = PanelRect(40f, 50f, 200f, 400f)) else it },
            open = setOf(EditorPanel.PAGES), nextId = 7)
    }

    @Test fun anOlderLayoutGainsTheNewPanelAndKeepsEverythingElse() {
        assertFalse(EditorPanelLayouts.isValid(older))
        val upgraded = EditorPanelLayouts.withAllPanels(older)
        assertTrue(EditorPanelLayouts.isValid(upgraded))
        assertEquals("the user's windows are kept", older.floating, upgraded.floating.dropLast(1))
        assertEquals(listOf(EditorPanel.NAVIGATOR), EditorPanelLayouts.members(upgraded.floating.last().node))
        assertEquals("it arrives closed", setOf(EditorPanel.PAGES), upgraded.open)
        assertEquals(EditorPanelLayout(), EditorPanelLayouts.withAllPanels(EditorPanelLayout()))
    }

    @Test fun savedWorkspacesAndTheirNamedLayoutsUpgradeOnDecode() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val saved = PanelWorkspaceBackup(older, NamedPanelLayouts(listOf(NamedPanelLayout("Inking", older))),
            orientationLayouts = mapOf("PORTRAIT" to NamedPanelLayout("PORTRAIT", older)))
        val decoded = json.decodeFromString(PanelWorkspaceBackup.serializer(), json.encodeToString(PanelWorkspaceBackup.serializer(), saved)).withAllPanels()
        decoded.validate()
        assertTrue(EditorPanelLayouts.isValid(decoded.namedLayouts.entries.single().layout))
        assertTrue(EditorPanelLayouts.isValid(decoded.orientationLayouts.getValue("PORTRAIT").layout))
    }
}
