package app.inkloom.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NamedPanelLayoutsTest {
    @Test fun saveRestoreRenameAndDeleteKeepIndependentLayouts() {
        val studio = EditorPanelLayout(open = setOf(EditorPanel.BRUSHES, EditorPanel.LAYERS))
        val reading = EditorPanelLayouts.move(
            EditorPanelLayout(), "pages", null,
            PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 210f, 700f),
        ).copy(open = setOf(EditorPanel.PAGES))
        val saved = NamedPanelLayouts().save(" Drawing ", studio).save("Reading", reading)
        assertEquals(listOf("Drawing", "Reading"), saved.entries.map { it.name })
        assertEquals(studio, saved.entries.first().layout)
        assertEquals(reading, saved.entries.last().layout)
        val decoded = Json.decodeFromString<NamedPanelLayouts>(Json.encodeToString(NamedPanelLayouts.serializer(), saved))
        assertEquals(saved, decoded)
        assertEquals(saved, saved.rename("Drawing", "Reading"))
        val renamed = saved.rename("Drawing", "Sketching")
        assertEquals(listOf("Sketching", "Reading"), renamed.entries.map { it.name })
        assertEquals(listOf("Reading"), renamed.delete("Sketching").entries.map { it.name })
        assertEquals(listOf("Reading", "drawing"), saved.save("drawing", studio).entries.map { it.name })
    }

    @Test fun invalidLayoutsAndEmptyNamesDoNotOverwriteSavedWork() {
        val valid = EditorPanelLayout()
        val saved = NamedPanelLayouts().save("Studio", valid)
        assertEquals(saved, saved.save("  ", valid))
        assertEquals(saved, saved.save("Studio", valid.copy(floating = emptyList())))
        assertFalse(EditorPanelLayouts.isValid(valid.copy(floating = emptyList())))
    }

    @Test fun namedWorkspaceRestoresToolRailWithItsPanels() {
        val original = AppSettings(showToolbar = false, verticalToolbar = true,
            toolbarDock = ToolbarDock.RIGHT, toolbarOffsetXDp = 900f, toolbarOffsetYDp = 70f)
        val saved = NamedPanelLayouts().save("Reading", EditorPanelLayout(), ToolRailLayout.capture(original))
        val decoded = Json.decodeFromString<NamedPanelLayouts>(Json.encodeToString(NamedPanelLayouts.serializer(), saved))
        val restored = requireNotNull(decoded.entries.single().toolRail).applyTo(AppSettings())
        assertEquals(original.showToolbar, restored.showToolbar)
        assertEquals(original.verticalToolbar, restored.verticalToolbar)
        assertEquals(original.toolbarDock, restored.toolbarDock)
        assertEquals(original.toolbarOffsetXDp, restored.toolbarOffsetXDp)
        assertEquals(original.toolbarOffsetYDp, restored.toolbarOffsetYDp)
    }
}
