package app.inkloom.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBrushCatalogTest {
    @Test
    fun factoryCatalogContainsProfessionalPenPencilAndPaintingDefaults() {
        val namesByGroup = SystemBrushCatalog.brushes.groupBy(ImportedBrush::groupName).mapValues { entry -> entry.value.map(ImportedBrush::name) }

        assertTrue(namesByGroup.getValue("Pen").containsAll(listOf("G Pen", "Painting Pen")))
        assertTrue(namesByGroup.getValue("Pencil").containsAll(listOf("Technical Pencil", "H Pencil", "HB Pencil", "2B Pencil", "6B Pencil")))
        assertTrue(namesByGroup.getValue("Brush").contains("Classic PS No. 19"))
        assertEquals(19f, SystemBrushCatalog.byId("system-ps19")!!.defaultSize)
    }

    @Test
    fun graphiteGradesAndInkPresetsHaveDistinctRuntimeDescriptors() {
        val graphite = listOf("system-technical-pencil", "system-h-pencil", "system-hb-pencil", "system-2b-pencil", "system-6b-pencil")
            .map { requireNotNull(SystemBrushCatalog.byId(it)) }

        assertEquals(graphite.size, graphite.map { listOf(it.defaultSize, it.hardness, it.flow, it.spacing) }.distinct().size)
        assertNotEquals(SystemBrushCatalog.byId("system-g-pen"), SystemBrushCatalog.byId("system-painting-pen"))
        assertTrue(SystemBrushCatalog.brushes.all { it.format == BrushFormat.SYSTEM && it.compatibility == Compatibility.COMPLETE })
    }

    @Test
    fun factoryBrushesNeverSilentlyCorrectTheAuthoredTrajectory() {
        SystemBrushCatalog.brushes.forEach { brush ->
            assertEquals("${brush.name} stabilization", 0f, brush.stabilization, .00001f)
            assertEquals("${brush.name} StreamLine", 0f, brush.streamLine, .00001f)
            assertEquals("${brush.name} moving-average stabilization", 0f, brush.procreateStabilization, .00001f)
            assertEquals("${brush.name} motion filtering", 0f, brush.motionFiltering, .00001f)
        }
    }

    @Test
    fun liquifyCatalogExposesCspAndProcreateModesAsPeerPresets() {
        val names = SystemBrushCatalog.brushes.filter { it.groupName == "Liquify" }.map(ImportedBrush::name)

        assertTrue(names.containsAll(listOf(
            "Push", "Push Left", "Push Right", "Twirl Left", "Twirl Right",
            "Pinch", "Expand", "Crystals", "Edge", "Reconstruct",
        )))
    }
}
