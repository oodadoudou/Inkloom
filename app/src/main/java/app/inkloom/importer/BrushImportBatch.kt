package app.inkloom.importer

import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush

/** Conservation-correct result of importing one or more brush source files. */
data class BrushImportSummary(
    val filesScanned: Int,
    val brushesDiscovered: Int,
    val parsed: Int,
    val imported: Int,
    val rejected: Int,
    val renderable: Int,
    val failed: Int,
    val skipped: Int,
    val groupsCreated: Int,
) {
    init {
        require(filesScanned >= 0 && brushesDiscovered >= 0 && parsed >= 0 && imported >= 0)
        require(rejected >= 0 && renderable >= 0 && failed >= 0 && skipped >= 0 && groupsCreated >= 0)
        require(brushesDiscovered == parsed + failed + skipped)
        require(parsed == renderable + rejected)
        require(imported == renderable)
    }
}

data class BrushImportBatch(
    val renderableBrushes: List<ImportedBrush>,
    val rejectedBrushes: List<ImportedBrush>,
    val failedBrushes: List<ImportedBrush>,
    val summary: BrushImportSummary,
) {
    companion object {
        fun classify(filesScanned: Int, brushes: List<ImportedBrush>): BrushImportBatch {
            val failed = brushes.filter { it.hasParserFailure() }
            val parsed = brushes - failed.toSet()
            val renderable = parsed.filter { brush ->
                brush.compatibility != Compatibility.FAILED && brush.v5PresetJson != null
            }
            val rejected = parsed - renderable.toSet()
            return BrushImportBatch(
                renderableBrushes = renderable,
                rejectedBrushes = rejected,
                failedBrushes = failed,
                summary = BrushImportSummary(
                    filesScanned = filesScanned,
                    brushesDiscovered = brushes.size,
                    parsed = parsed.size,
                    imported = renderable.size,
                    rejected = rejected.size,
                    renderable = renderable.size,
                    failed = failed.size,
                    skipped = 0,
                    groupsCreated = renderable.map(ImportedBrush::groupName)
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .size,
                ),
            )
        }

        private fun ImportedBrush.hasParserFailure(): Boolean = report.any { line ->
            line.contains("parsing failed", ignoreCase = true) ||
                line.contains("could not be decoded", ignoreCase = true)
        }
    }
}
