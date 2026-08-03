package app.inkloom.settings

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolboxQuickSlotTest {
    @Test fun `codec preserves brush preset group and workspace slots`() {
        val workspace = AppSettings(
            showPageRail = false,
            showBrushPanel = true,
            brushPanelDock = WorkspacePanelDock.FREE,
            brushPanelXDp = 321f,
            toolbarDock = ToolbarDock.LEFT,
        ).captureToolboxWorkspace()
        val slots = listOf(
            ToolboxQuickSlot(ToolboxQuickSlotKind.BRUSH, "Ink", brushId = "brush-1"),
            ToolboxQuickSlot(ToolboxQuickSlotKind.BRUSH_GROUP, "Sketch", groupPath = "Favorites/Pencils"),
            ToolboxQuickSlot(ToolboxQuickSlotKind.TOOL_PRESET, "Red 12", brushId = "brush-2", size = 12f, opacity = .6f, colorArgb = 0xFFAA0000.toInt()),
            ToolboxQuickSlot(ToolboxQuickSlotKind.WORKSPACE, "Drawing", workspace = workspace),
        )

        val decoded = ToolboxQuickSlotCodec.decode(ToolboxQuickSlotCodec.encode(slots))

        assertEquals(ToolboxQuickSlots.COUNT, decoded.size)
        assertEquals(slots, decoded.take(slots.size))
        assertEquals(ToolboxQuickSlotKind.EMPTY, decoded.last().kind)
    }

    @Test fun `normalization bounds values and removes unsafe path formatting`() {
        val normalized = ToolboxQuickSlots.normalize(
            ToolboxQuickSlot(
                kind = ToolboxQuickSlotKind.TOOL_PRESET,
                label = "  Preset  ",
                groupPath = " / Favorites / Ink / ",
                size = 999f,
                opacity = 0f,
            ),
        )

        assertEquals("Preset", normalized.label)
        assertEquals("Favorites/Ink", normalized.groupPath)
        assertEquals(400f, normalized.size)
        assertEquals(.05f, normalized.opacity)
    }

    @Test fun `workspace snapshot restores only workspace related settings`() {
        val snapshot = AppSettings(showPageRail = false, showLayerPanel = true, toolbarDock = ToolbarDock.BOTTOM).captureToolboxWorkspace()
        val original = AppSettings(fingerDrawing = true, penSize = 42f, showPageRail = true)

        val restored = original.applyToolboxWorkspace(snapshot)

        assertEquals(false, restored.showPageRail)
        assertEquals(true, restored.showLayerPanel)
        assertEquals(ToolbarDock.BOTTOM, restored.toolbarDock)
        assertEquals(true, restored.fingerDrawing)
        assertEquals(42f, restored.penSize)
    }

    @Test fun `malformed codec input returns eight empty slots`() {
        val decoded = ToolboxQuickSlotCodec.decode("not json")

        assertEquals(ToolboxQuickSlots.COUNT, decoded.size)
        assertEquals(true, decoded.all { it.kind == ToolboxQuickSlotKind.EMPTY })
        assertNull(decoded.first().workspace)
    }

    @Test fun `quick slots resolve exact brushes and most recent nested group brushes`() {
        val brushes = listOf(brush("one", "One", "Favorites/Ink", used = 1), brush("two", "Two", "Favorites/Ink/Fine", used = 2))

        assertEquals("one", ToolboxQuickSlots.preferredBrush(ToolboxQuickSlot(ToolboxQuickSlotKind.BRUSH, brushId = "one"), brushes)?.id)
        assertEquals("two", ToolboxQuickSlots.preferredBrush(ToolboxQuickSlot(ToolboxQuickSlotKind.BRUSH_GROUP, groupPath = "Favorites/Ink"), brushes)?.id)
    }

    @Test fun `backup restore remaps referenced brush ids without changing system presets`() {
        val slots = listOf(
            ToolboxQuickSlot(ToolboxQuickSlotKind.BRUSH, brushId = "old-brush"),
            ToolboxQuickSlot(ToolboxQuickSlotKind.TOOL_PRESET, brushId = ""),
        )

        val restored = ToolboxQuickSlots.remapBrushIds(slots, mapOf("old-brush" to "restored-brush"))

        assertEquals("restored-brush", restored[0].brushId)
        assertEquals("", restored[1].brushId)
    }

    private fun brush(id: String, name: String, group: String, used: Long) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.SUT,
        compatibility = Compatibility.HIGH,
        sizeBytes = 1,
        report = emptyList(),
        groupName = group,
        lastUsedAt = used,
    )
}
