package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Test

class BrushImportBatchTest {
    @Test fun countersConserveRenderableRejectedAndFailedBrushes() {
        val renderable = brush("renderable", Compatibility.HIGH, v5 = "{}", group = "Pencil")
        val rejected = brush(
            "rejected",
            Compatibility.FAILED,
            report = listOf("UNSUPPORTED_WITH_EXPLICIT_REASON:REALTIME_TAPER_REQUIRED"),
        )
        val failed = brush(
            "failed",
            Compatibility.FAILED,
            report = listOf("Procreate parsing failed: truncated archive"),
        )

        val batch = BrushImportBatch.classify(1, listOf(renderable, rejected, failed))

        assertEquals(listOf(renderable), batch.renderableBrushes)
        assertEquals(listOf(rejected), batch.rejectedBrushes)
        assertEquals(listOf(failed), batch.failedBrushes)
        assertEquals(
            BrushImportSummary(
                filesScanned = 1,
                brushesDiscovered = 3,
                parsed = 2,
                imported = 1,
                rejected = 1,
                renderable = 1,
                failed = 1,
                skipped = 0,
                groupsCreated = 1,
            ),
            batch.summary,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun inconsistentCountersAreRejected() {
        BrushImportSummary(1, 2, 2, 1, 0, 1, 1, 0, 0)
    }

    private fun brush(
        name: String,
        compatibility: Compatibility,
        v5: String? = null,
        group: String = "",
        report: List<String> = emptyList(),
    ) = ImportedBrush(
        name = name,
        format = BrushFormat.PROCREATE,
        compatibility = compatibility,
        sizeBytes = 10,
        report = report,
        groupName = group,
        v5PresetJson = v5,
    )
}
