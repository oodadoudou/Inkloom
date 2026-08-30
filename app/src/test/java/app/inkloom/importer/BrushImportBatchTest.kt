package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushImportBatchTest {
    @Test fun ledgerConservesEveryDiscoveredBrushIntoExactlyOneTerminalState() {
        val renderable = BrushPresetV5Import.convertDecodedSource(brush("renderable", Compatibility.HIGH, group = "Pencil"))
        val rejected = brush(
            "rejected", Compatibility.FAILED,
            report = listOf("UNSUPPORTED_WITH_EXPLICIT_REASON:REALTIME_TAPER_REQUIRED"),
        )
        val failed = brush(
            "failed", Compatibility.FAILED,
            report = listOf("Procreate parsing failed: truncated archive"),
        )

        val batch = BrushImportBatch.classify(1, listOf(renderable, rejected, failed)) { BrushRenderProbeResult.Succeeded }

        assertEquals(listOf(renderable), batch.renderableBrushes)
        assertEquals(listOf(rejected), batch.rejectedBrushes)
        assertEquals(listOf(failed), batch.failedBrushes)
        assertEquals(3, batch.summary.brushesDiscovered)
        assertEquals(1, batch.summary.importedUsable)
        assertEquals(1, batch.summary.rejectedUnsupported)
        assertEquals(1, batch.summary.failedDecode)
        assertEquals(0, batch.summary.failedAsset)
        assertEquals(0, batch.summary.failedCompile)
        assertEquals(0, batch.summary.failedRender)
        assertEquals(
            batch.summary.brushesDiscovered,
            batch.summary.importedUsable + batch.summary.rejectedUnsupported + batch.summary.failed,
        )
    }

    @Test fun containerAndAssetCountsNeverBecomeBrushCounts() {
        val first = BrushPresetV5Import.convertDecodedSource(brush("one", Compatibility.HIGH).copy(sha256 = "same"))
        val second = BrushPresetV5Import.convertDecodedSource(brush("two", Compatibility.HIGH).copy(sha256 = "same"))
        val ledger = BrushImportLedger.classify(1, listOf(first, second), renderProbe = { BrushRenderProbeResult.Succeeded })

        assertEquals(1, ledger.summary.sourceFilesScanned)
        assertEquals(1, ledger.summary.sourceContainers)
        assertEquals(2, ledger.summary.brushesDiscovered)
        assertEquals(2, ledger.summary.importedUsable)
    }

    @Test fun missingReferencedAssetTerminatesAsAssetFailure() {
        val converted = BrushPresetV5Import.convertDecodedSource(
            brush("missing", Compatibility.HIGH).copy(tipPreviewPath = "/not/present/shape.png"),
        )
        val ledger = BrushImportLedger.classify(1, listOf(converted), assetValidator = { false })

        assertEquals(BrushImportFinalState.FAILED_ASSET, ledger.records.single().finalState)
        assertEquals(1, ledger.summary.failedAsset)
        assertTrue(ledger.records.single().rejectionReasons.single().contains("Unreadable brush asset"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSourceRecordsAreRejected() {
        val source = BrushPresetV5Import.convertDecodedSource(brush("same", Compatibility.HIGH))
        BrushImportLedger.classify(1, listOf(source, source), renderProbe = { BrushRenderProbeResult.Succeeded })
    }

    @Test fun compileSuccessWithoutAProductionProbeIsNotImportedUsable() {
        val converted = BrushPresetV5Import.convertDecodedSource(brush("unprobed", Compatibility.HIGH))

        val ledger = BrushImportLedger.classify(1, listOf(converted))

        assertEquals(BrushImportFinalState.FAILED_RENDER, ledger.records.single().finalState)
        assertEquals(BrushImportStageStatus.FAILED, ledger.records.single().renderProbeStatus)
        assertEquals(0, ledger.summary.importedUsable)
        assertEquals(1, ledger.summary.failedRender)
    }

    private fun brush(
        name: String,
        compatibility: Compatibility,
        group: String = "",
        report: List<String> = emptyList(),
    ) = ImportedBrush(
        id = "id-$name",
        name = name,
        format = BrushFormat.PROCREATE,
        compatibility = compatibility,
        sizeBytes = 10,
        report = report,
        groupName = group,
    )
}
