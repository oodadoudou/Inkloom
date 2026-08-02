package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Test

class BrushGroupOperationsTest {
    @Test fun groupNamesAreNormalizedDeduplicatedAndSorted() {
        val brushes = listOf(brush("A", "  Inking   pens "), brush("B", "watercolor"), brush("C", "INKING PENS"), brush("D", ""))

        assertEquals(listOf("Inking pens", "watercolor"), BrushGroupOperations.names(brushes))
    }

    @Test fun assignRenameMergeAndUngroupPreserveUnrelatedBrushes() {
        val first = BrushGroupOperations.assign(brush("A", ""), "  Sketch   tools ")
        val second = brush("B", "Paint")
        val third = brush("C", "paint")

        assertEquals("Sketch tools", first.groupName)
        val merged = BrushGroupOperations.rename(listOf(first, second, third), "PAINT", " Sketch tools ")
        assertEquals(listOf("Sketch tools", "Sketch tools", "Sketch tools"), merged.map(ImportedBrush::groupName))
        val ungrouped = BrushGroupOperations.rename(merged, "Sketch tools", "   ")
        assertEquals(listOf("", "", ""), ungrouped.map(ImportedBrush::groupName))
    }

    @Test fun groupNamesAreBoundedForStorageAndUi() {
        assertEquals(BrushGroupOperations.MAX_NAME_LENGTH, BrushGroupOperations.normalize("x".repeat(200)).length)
    }

    private fun brush(name: String, group: String) = ImportedBrush(
        id = name,
        name = name,
        format = BrushFormat.IMAGE,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 1,
        report = emptyList(),
        groupName = group,
    )
}
