package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticExecutionDisposition
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Generates only ignored local evidence; proprietary source packages are never copied. */
class ProcreateResourceDependencyAuditTest {
    @Test fun inventoryAuthorizedCorpusWhenConfigured() {
        val root = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val output = System.getProperty("inkloom.audit.output.dir")?.let(::File)
        val sources = root!!.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in setOf("brush", "brushset")
        }.sortedBy(File::getAbsolutePath).toList()
        assumeTrue(sources.isNotEmpty())
        val assets = File(System.getProperty("java.io.tmpdir"), "inkloom-procreate-resource-audit-${System.nanoTime()}")
        val rows = mutableListOf<Row>()
        try {
            sources.forEachIndexed { sourceIndex, source ->
                val hash = sha256(source)
                val parsed = ProcreateBrushParser(File(assets, "source-$sourceIndex")).enrich(
                    source,
                    hash,
                    ImportedBrush(
                        name = source.nameWithoutExtension,
                        format = BrushFormat.PROCREATE,
                        compatibility = Compatibility.BASIC,
                        sizeBytes = source.length(),
                        report = emptyList(),
                    ),
                )
                parsed.forEach { brush ->
                    val terminal = BrushPresetV5Import.convertOrReject(brush)
                    val refs = brush.dualGrain
                    val missing = listOfNotNull(
                        refs.sourceTipReference,
                        refs.sourceGrainReference,
                        refs.dualSourceTipReference,
                        refs.dualSourceGrainReference,
                    ).distinct()
                    val unsupported = BrushSemanticMapper.describe(brush)
                        .filter { it.executionDisposition == SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON }
                        .map { it.sourceSemantic }
                        .distinct()
                        .sorted()
                    val status = when {
                        terminal.v5PresetJson != null -> "PRODUCT_USABLE_EXACT"
                        terminal.report.any { it.contains("parsing failed", ignoreCase = true) } -> "FAILED"
                        missing.isNotEmpty() -> "UNSUPPORTED_MISSING_RESOURCE"
                        else -> "UNSUPPORTED_SEMANTICS"
                    }
                    rows += Row(
                        brushset = source.name,
                        brushName = brush.name,
                        sourceBrushId = brush.id,
                        shapeReference = refs.sourceTipReference,
                        grainReference = refs.sourceGrainReference,
                        shapeEmbedded = brush.tipPreviewPath != null,
                        grainEmbedded = brush.grainPreviewPath != null,
                        sub01 = brush.dualBrush,
                        dualShapeReference = refs.dualSourceTipReference,
                        dualGrainReference = refs.dualSourceGrainReference,
                        missing = missing,
                        unsupported = unsupported,
                        status = status,
                        finalReason = terminal.report.lastOrNull {
                            it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON") ||
                                it.contains("parsing failed", ignoreCase = true)
                        }.orEmpty(),
                    )
                }
            }

            assertEquals(391, rows.size)
            assertEquals(rows.size, rows.groupingBy(Row::status).eachCount().values.sum())
            output?.let { writeReports(it.apply(File::mkdirs), rows) }
        } finally {
            assets.deleteRecursively()
        }
    }

    private fun writeReports(output: File, rows: List<Row>) {
        val counts = rows.groupingBy(Row::status).eachCount().toSortedMap()
        val missing = rows.flatMap(Row::missing).groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        output.resolve("PROCREATE_RESOURCE_DEPENDENCY_REPORT.md").writeText(buildString {
            appendLine("# Procreate resource dependency report")
            appendLine()
            appendLine("Authorized local corpus only. No source package or proprietary resource is copied into Git/APK.")
            appendLine()
            appendLine("## Aggregate missing resource identifiers")
            appendLine()
            missing.forEach { appendLine("- `${it.key}`: ${it.value}") }
            appendLine()
            appendLine("## Per-brush inventory")
            appendLine()
            appendLine("| Brushset | Brush | Source ID | Shape ref | Grain ref | Shape embedded | Grain embedded | Sub01 | Dual shape ref | Dual grain ref | Missing | Unsupported semantics | Final state | Final reason |")
            appendLine("|---|---|---|---|---|---:|---:|---:|---|---|---|---|---|---|")
            rows.forEach { row ->
                appendLine(
                    listOf(
                        row.brushset, row.brushName, row.sourceBrushId, row.shapeReference.orEmpty(),
                        row.grainReference.orEmpty(), row.shapeEmbedded, row.grainEmbedded, row.sub01,
                        row.dualShapeReference.orEmpty(), row.dualGrainReference.orEmpty(),
                        row.missing.joinToString(", "), row.unsupported.joinToString(", "), row.status,
                        row.finalReason,
                    ).joinToString(" | ", prefix = "| ", postfix = " |") { markdown(it.toString()) },
                )
            }
        })
        output.resolve("PROCREATE_IMPORT_LEDGER.json").writeText(
            rows.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { row ->
                "  {\"brushset\":${json(row.brushset)},\"brushName\":${json(row.brushName)}," +
                    "\"sourceBrushId\":${json(row.sourceBrushId)},\"status\":${json(row.status)}," +
                    "\"missingResources\":[${row.missing.joinToString(",") { json(it) }}]," +
                    "\"unsupportedSemantics\":[${row.unsupported.joinToString(",") { json(it) }}]}"
            },
        )
        output.resolve("PROCREATE_IMPORT_SUMMARY.md").writeText(buildString {
            appendLine("# Procreate import summary")
            appendLine()
            counts.forEach { appendLine("- `${it.key}`: ${it.value}") }
            val usable = counts["PRODUCT_USABLE_EXACT"].orZero() + counts["PRODUCT_USABLE_APPROXIMATED"].orZero()
            appendLine("- `UI_IMPORTED_SUCCESS_COUNT`: $usable")
            appendLine("- `DISCOVERED`: ${rows.size}")
            appendLine("- `CONSERVATION`: ${rows.size == counts.values.sum()}")
        })
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun markdown(value: String): String = value.replace("|", "\\|").replace("\n", " ")
    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
    private fun Int?.orZero(): Int = this ?: 0

    private data class Row(
        val brushset: String,
        val brushName: String,
        val sourceBrushId: String,
        val shapeReference: String?,
        val grainReference: String?,
        val shapeEmbedded: Boolean,
        val grainEmbedded: Boolean,
        val sub01: Boolean,
        val dualShapeReference: String?,
        val dualGrainReference: String?,
        val missing: List<String>,
        val unsupported: List<String>,
        val status: String,
        val finalReason: String,
    )
}
