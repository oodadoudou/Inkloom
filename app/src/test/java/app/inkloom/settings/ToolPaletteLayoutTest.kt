package app.inkloom.settings

import app.inkloom.model.EditorTool
import org.junit.Assert.*
import org.junit.Test

class ToolPaletteLayoutTest {
    @Test fun roundTripKeepsEveryToolAndSeparators() {
        val decoded=ToolPaletteLayout.decode(ToolPaletteLayout.encode(ToolPaletteLayout.defaultEntries))
        assertEquals(EditorTool.entries.toSet(),decoded.filterIsInstance<ToolPaletteEntry.Tool>().map { it.tool }.toSet())
        assertEquals(2,decoded.count { it == ToolPaletteEntry.Separator })
    }

    @Test fun movingAcrossSeparatorChangesGroupWithoutLosingTools() {
        val original=ToolPaletteLayout.defaultEntries
        val image=original.indexOf(ToolPaletteEntry.Tool(EditorTool.IMAGE))
        val firstSeparator=original.indexOf(ToolPaletteEntry.Separator)
        val moved=ToolPaletteLayout.move(original,image,firstSeparator)
        assertTrue(moved.indexOf(ToolPaletteEntry.Tool(EditorTool.IMAGE)) < moved.indexOf(ToolPaletteEntry.Separator))
        assertEquals(EditorTool.entries.size,moved.filterIsInstance<ToolPaletteEntry.Tool>().size)
    }

    @Test fun malformedLayoutFallsBackAndRepeatedSeparatorsCollapse() {
        assertEquals(ToolPaletteLayout.defaultEntries,ToolPaletteLayout.decode("PEN,|,|,PEN"))
        val normalized=ToolPaletteLayout.normalize(listOf(ToolPaletteEntry.Separator,ToolPaletteEntry.Tool(EditorTool.PEN),ToolPaletteEntry.Separator,ToolPaletteEntry.Separator))
        assertFalse(normalized.first() == ToolPaletteEntry.Separator)
        assertFalse(normalized.last() == ToolPaletteEntry.Separator)
    }
}
