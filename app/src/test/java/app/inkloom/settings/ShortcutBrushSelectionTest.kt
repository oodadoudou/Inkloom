package app.inkloom.settings

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutBrushSelectionTest {
    private fun brush(id: String, name: String, group: String, used: Long = 0, hidden: Boolean = false, order: Int = 0) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.SUT,
        compatibility = Compatibility.HIGH,
        sizeBytes = 1,
        report = emptyList(),
        groupName = group,
        lastUsedAt = used,
        hidden = hidden,
        sortOrder = order,
    )

    @Test fun `group matching accepts CSP style English and Chinese labels`() {
        assertTrue(ShortcutBrushSelection.matches(brush("1", "G Pen", "钢笔"), ShortcutBrushGroup.PEN))
        assertTrue(ShortcutBrushSelection.matches(brush("2", "Real Pencil", "Sketch"), ShortcutBrushGroup.PENCIL))
        assertTrue(ShortcutBrushSelection.matches(brush("3", "Dry", "Brushes"), ShortcutBrushGroup.BRUSH))
        assertFalse(ShortcutBrushSelection.matches(brush("4", "Marker", "Marker"), ShortcutBrushGroup.PENCIL))
    }

    @Test fun `preferred ignores hidden brushes and chooses most recently used`() {
        val chosen = ShortcutBrushSelection.preferred(
            listOf(
                brush("old", "G Pen", "Pen", used = 10),
                brush("new", "Mapping Pen", "Pen", used = 20),
                brush("hidden", "Hidden Pen", "Pen", used = 30, hidden = true),
            ),
            ShortcutBrushGroup.PEN,
        )
        assertEquals("new", chosen?.id)
    }

    @Test fun `configured target selects an exact or nested custom group before name inference`() {
        val brushes = listOf(
            brush("semantic", "Pen", "Imported", used = 99),
            brush("target", "Opaque tool", "Favorites/Inking", used = 1),
            brush("nested", "Newest opaque tool", "Favorites/Inking/Fine", used = 2),
        )

        assertEquals("nested", ShortcutBrushSelection.preferred(brushes, ShortcutBrushGroup.PEN, " Favorites / Inking ")?.id)
    }

    @Test fun `missing configured target safely falls back to CSP name inference`() {
        val brushes = listOf(brush("pen", "G-Pen", "Imported", used = 1))

        assertEquals("pen", ShortcutBrushSelection.preferred(brushes, ShortcutBrushGroup.PEN, "Deleted group")?.id)
    }

    @Test fun `group targets normalize paths and update one role without changing the others`() {
        val targets = ShortcutBrushGroupTargets(pen = " Pens ", pencil = "Sketch", brush = "Paint")
            .withGroup(ShortcutBrushGroup.PEN, " Favorites / Inking ")

        assertEquals(ShortcutBrushGroupTargets("Favorites/Inking", "Sketch", "Paint"), targets)
    }

    @Test fun `adjacent wraps inside the current group`() {
        val brushes = listOf(
            brush("a", "A", "Pen"),
            brush("b", "B", "Pen"),
            brush("c", "C", "Pencil"),
        )
        assertEquals("b", ShortcutBrushSelection.adjacent(brushes, "a", -1)?.id)
        assertEquals("a", ShortcutBrushSelection.adjacent(brushes, "b", 1)?.id)
        assertEquals("c", ShortcutBrushSelection.adjacent(brushes, null, -1)?.id)
    }

    @Test fun `adjacent follows persisted manual order`() {
        val brushes = listOf(brush("a", "A", "Pen", order = 2), brush("b", "B", "Pen", order = 0), brush("c", "C", "Pen", order = 1))

        assertEquals("c", ShortcutBrushSelection.adjacent(brushes, "b", 1)?.id)
    }
}
