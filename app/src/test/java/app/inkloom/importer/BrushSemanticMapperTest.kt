package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticFidelity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushSemanticMapperTest {
    @Test fun importedTextureBrushGetsExecutableV2Mappings() {
        val brush = ImportedBrush(
            name = "Mapped",
            format = BrushFormat.PROCREATE,
            compatibility = Compatibility.HIGH,
            sizeBytes = 42,
            report = emptyList(),
            tipPreviewPath = "/shape.png",
            tipPreviewPaths = listOf("/shape.png"),
            grainPreviewPath = "/grain.png",
            continuousPlot = true,
            dualBrush = true,
            dualTipPreviewPaths = listOf("/dual.png"),
        )

        val mapped = BrushSemanticMapper.apply(brush)
        val targets = mapped.semanticMappings.map { it.targetRuntime }.toSet()

        assertTrue("StampRasterEngine.tipTexture" in targets)
        assertTrue("StampRasterEngine.grainTexture" in targets)
        assertTrue("AirbrushEngine.timeSampler" in targets)
        assertTrue("StampRasterEngine.secondaryCoat" in targets)
        assertEquals(SemanticFidelity.APPROXIMATE, mapped.semanticMappings.single { it.sourceSemantic == "PROCREATE.renderingMode" }.fidelity)
        assertTrue(mapped.semanticMappings.filterNot { it.sourceSemantic == "PROCREATE.renderingMode" }.all { it.fidelity == SemanticFidelity.EXACT })
    }

    @Test fun wetMixIsExplicitlyReportedAsApproximate() {
        val brush = ImportedBrush(
            name = "Wet",
            format = BrushFormat.SUT,
            compatibility = Compatibility.BASIC,
            sizeBytes = 42,
            report = emptyList(),
            watercolor = true,
        )

        val wet = BrushSemanticMapper.describe(brush).single { it.sourceSemantic.endsWith("wetMix") }

        assertEquals(SemanticFidelity.APPROXIMATE, wet.fidelity)
        assertTrue(wet.note.isNotBlank())
    }
}
