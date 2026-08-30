package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrushLibraryInventoryTest {
    @Test fun countsOnlyDistinctProductionExecutablePresets() {
        val system = native("system", BrushFormat.SYSTEM)
        val imported = native("imported", BrushFormat.PROCREATE)
        val user = native("user", BrushFormat.INKLOOM)
        val rejected = ImportedBrush(
            id = "rejected", name = "rejected", format = BrushFormat.PROCREATE,
            compatibility = Compatibility.FAILED, sizeBytes = 1L,
            report = listOf("UNSUPPORTED_WITH_EXPLICIT_REASON:WET_PAINT_DUAL_BRUSH_REQUIRED"),
        )

        val summary = BrushLibraryInventory.summarize(
            listOf(system),
            listOf(imported, imported, user, rejected, system),
        )

        assertEquals(3, summary.total)
        assertEquals(1, summary.factory)
        assertEquals(1, summary.imported)
        assertEquals(1, summary.userCreated)
        assertFalse(BrushLibraryInventory.executable(rejected))
    }

    private fun native(id: String, format: BrushFormat): ImportedBrush {
        val source = ImportedBrush(
            id = id, name = id, format = format, compatibility = Compatibility.COMPLETE,
            sizeBytes = 1L, report = emptyList(),
        )
        return when (format) {
            BrushFormat.SYSTEM -> BrushPresetV5Import.createFactoryRecord(source)
            BrushFormat.INKLOOM -> BrushPresetV5Import.createUserRecord(source)
            else -> BrushPresetV5Import.convertDecodedSource(source)
        }
    }
}
