package app.inkloom.importer

import app.inkloom.document.ProcreateColorDynamics
import app.inkloom.document.ProcreateWetMix
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticExecutionDisposition
import app.inkloom.model.SemanticFidelity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushSemanticMapperTest {
    @Test fun executableV5FamiliesAreReportedAsSupportedAndExecuted() {
        val mapped = BrushSemanticMapper.apply(
            ImportedBrush(
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
            ),
        )

        val executed = mapped.semanticMappings
            .filter { it.executionDisposition == SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED }
            .map { it.targetRuntime }
            .toSet()

        assertTrue("StampPaintOp.tipTexture" in executed)
        assertTrue("StampPaintOp.textureGrain" in executed)
        assertTrue("StampPaintOp.continuousPlot" in executed)
        assertTrue("StampPaintOp.dual" in executed)
        assertTrue(mapped.semanticMappings.all { it.executionDisposition in SemanticExecutionDisposition.entries })
    }

    @Test fun parsedButUnexecutedFamiliesAreNeverReportedAsExactRuntimeSupport() {
        val brush = ImportedBrush(
            name = "Advanced",
            format = BrushFormat.PROCREATE,
            compatibility = Compatibility.BASIC,
            sizeBytes = 42,
            report = emptyList(),
            strokeFalloff = .4f,
            strokePathJitter = .3f,
            shapeRoundnessHorizontalJitter = .2f,
            colorJitterHueDegrees = 15f,
            procreateColorDynamics = ProcreateColorDynamics(stampHue = .2f),
            procreateWetMix = ProcreateWetMix(dilution = .5f, charge = .4f, grade = .3f),
        )

        val mappings = BrushSemanticMapper.describe(brush)
        val preservedFamilies = mappings
            .filter { it.executionDisposition == SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY }
            .map { it.sourceSemantic }
            .toSet()

        assertTrue("PROCREATE.strokeFalloff" in preservedFamilies)
        assertTrue("PROCREATE.colorDynamics" in preservedFamilies)
        assertTrue("PROCREATE.wetMix.advanced" in preservedFamilies)
        assertTrue(mappings.filter { it.sourceSemantic in preservedFamilies }.all { it.fidelity == SemanticFidelity.UNSUPPORTED })
        assertEquals(
            SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED,
            mappings.single { it.sourceSemantic == "PROCREATE.wetMix.basic" }.executionDisposition,
        )
        assertEquals(
            SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED,
            mappings.single { it.sourceSemantic == "PROCREATE.pathJitter" }.executionDisposition,
        )
        assertEquals(
            SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED,
            mappings.single { it.sourceSemantic == "PROCREATE.shapeDynamics" }.executionDisposition,
        )
    }

    @Test fun wetGrainDualAndTaperAreReportedSourceOnlyInsteadOfBlockingThePrimaryWetProgram() {
        val brush = ImportedBrush(
            name = "Wet imported",
            format = BrushFormat.PROCREATE,
            compatibility = Compatibility.BASIC,
            sizeBytes = 42,
            report = emptyList(),
            watercolor = true,
            procreateWetMix = ProcreateWetMix(dilution = .5f, charge = .4f),
            grainPreviewPath = "/grain.png",
            grainDensity = .8f,
            dualBrush = true,
            dualTipPreviewPaths = listOf("/dual.png"),
            taperStartEnabled = true,
            taperEndEnabled = true,
        )

        val mappings = BrushSemanticMapper.describe(brush).associateBy { it.sourceSemantic }

        listOf("PROCREATE.grain.basic", "PROCREATE.dualBrush.basic", "PROCREATE.taper.basic").forEach { semantic ->
            assertEquals(SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY, mappings.getValue(semantic).executionDisposition)
        }
        assertEquals(
            SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED,
            mappings.getValue("PROCREATE.wetMix.basic").executionDisposition,
        )
    }

    @Test fun fatalDualSemanticCarriesStableExplicitRejection() {
        val brush = ImportedBrush(
            name = "RGB dual",
            format = BrushFormat.SUT,
            compatibility = Compatibility.BASIC,
            sizeBytes = 42,
            report = emptyList(),
            dualBrush = true,
            dualTipPreviewPaths = listOf("/dual.png"),
            dualApplyRgb = true,
        )

        val mapping = BrushSemanticMapper.describe(brush).single { it.sourceSemantic == "SUT.dualBrush.rgb" }

        assertEquals(SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON, mapping.executionDisposition)
        assertTrue(mapping.note.isNotBlank())
    }

    @Test fun unavailableProcreateSourceLibraryAssetsAreRejectedInsteadOfReplacedByCircles() {
        val brush = ImportedBrush(
            name = "Source-library brush",
            format = BrushFormat.PROCREATE,
            compatibility = Compatibility.BASIC,
            sizeBytes = 42,
            report = emptyList(),
            grainDensity = .6f,
            dualGrain = app.inkloom.model.ImportedBrushGrain(
                sourceTipReference = "Brush-Preset-Flat-Brush.jpg",
                sourceGrainReference = "Texture-Preset-Canvas.jpg",
            ),
        )

        val rejected = BrushSemanticMapper.describe(brush)
            .filter { it.executionDisposition == SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON }
            .map { it.sourceSemantic }

        assertEquals(listOf("PROCREATE.shape.asset", "PROCREATE.grain.asset"), rejected)
    }
}
