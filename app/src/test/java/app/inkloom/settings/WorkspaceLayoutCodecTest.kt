package app.inkloom.settings

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutCodecTest {
    @Test fun roundTripRetainsFloatingGeometryOpenStateToolbarAndNamedLayouts() {
        val base = EditorPanelLayout()
        val layout = base.copy(
            floating = base.floating.mapIndexed { index, floating ->
                if (index == 0) floating.copy(rect = PanelRect(42f, 17f, 183f, 491f)) else floating
            },
            open = setOf(EditorPanel.PAGES, EditorPanel.NEARBY),
            locked = true,
        )
        val rail = ToolRailLayout.capture(AppSettings().copy(toolbarOffsetXDp = 41f, toolbarOffsetYDp = 75f))
        val workspace = PanelWorkspaceBackup(layout, NamedPanelLayouts().save("Drawing", layout, rail))
        val encoded = WorkspaceLayoutCodec.encode(workspace, rail)
        val decoded = WorkspaceLayoutCodec.decode(ByteArrayInputStream(encoded)).getOrThrow()
        assertEquals(workspace, decoded.workspace)
        assertEquals(rail, decoded.toolRail)
    }

    @Test fun invalidAndOversizedFilesAreRejectedBeforeApplying() {
        val workspace = PanelWorkspaceBackup(EditorPanelLayout(), NamedPanelLayouts())
        val rail = ToolRailLayout.capture(AppSettings())
        val valid = WorkspaceLayoutCodec.encode(workspace, rail).decodeToString()
        assertTrue(WorkspaceLayoutCodec.decode(valid.replace(WorkspaceLayoutCodec.FORMAT, "foreign-layout").encodeToByteArray()).isFailure)
        assertTrue(WorkspaceLayoutCodec.decode(valid.replace(Regex("\\\"format\\\"\\s*:\\s*\\\"[^\\\"]+\\\",?"), "").encodeToByteArray()).isFailure)
        val storedDock = "\"dock\": \"${rail.dock}\""
        assertTrue(valid.contains(storedDock))
        assertTrue(WorkspaceLayoutCodec.decode(valid.replace(storedDock, "\"dock\": \"UNKNOWN\"").encodeToByteArray()).isFailure)
        assertTrue(WorkspaceLayoutCodec.decode(ByteArrayInputStream(ByteArray(WorkspaceLayoutCodec.MAX_FILE_BYTES + 1))).isFailure)
    }

    @Test fun mergePreservesUnrelatedLocalLayoutsAndRejectsOverflow() {
        val layout = EditorPanelLayout()
        val local = NamedPanelLayouts().save("Local", layout).save("Drawing", layout)
        val updated = layout.copy(open = setOf(EditorPanel.NEARBY))
        val incoming = NamedPanelLayouts().save("drawing", updated).save("Imported", updated)
        val merged = WorkspaceLayoutCodec.mergeNamedLayouts(local, incoming)
        assertEquals(listOf("Local", "drawing", "Imported"), merged.entries.map { it.name })
        assertEquals(updated, merged.entries[1].layout)
        assertFalse(merged.entries.any { it.name == "Drawing" })
        val full = (1..20).fold(NamedPanelLayouts()) { state, index -> state.save("Local $index", layout) }
        assertTrue(runCatching { WorkspaceLayoutCodec.mergeNamedLayouts(full, incoming) }.isFailure)
    }
}
