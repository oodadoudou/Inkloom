package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolution
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolver
import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticExecutionDisposition
import java.io.File

/** One terminal result for one source brush. Containers and embedded assets are never records. */
enum class BrushImportFinalState {
    IMPORTED_USABLE, REJECTED_UNSUPPORTED, FAILED_DECODE, FAILED_ASSET, FAILED_COMPILE, FAILED_RENDER,
}

enum class BrushImportStageStatus { NOT_REQUIRED, SUCCEEDED, FAILED, UNSUPPORTED }

data class BrushImportRecord(
    val sourceFile: String,
    val sourceContainerId: String,
    val sourceBrushId: String,
    val sourceBrushName: String,
    val sourceGroup: String,
    val decodeStatus: BrushImportStageStatus,
    val assetStatus: BrushImportStageStatus,
    val mappingStatus: BrushImportStageStatus,
    val capabilityStatus: BrushImportStageStatus,
    val compileStatus: BrushImportStageStatus,
    val renderProbeStatus: BrushImportStageStatus,
    val commitStatus: BrushImportStageStatus,
    val finalState: BrushImportFinalState,
    val rejectionReasons: Set<String> = emptySet(),
    val unsupportedSemantics: Set<String> = emptySet(),
    val createdPresetId: String? = null,
    val brush: ImportedBrush? = null,
) {
    init {
        require(sourceBrushId.isNotBlank() && sourceBrushName.isNotBlank())
        require((finalState == BrushImportFinalState.IMPORTED_USABLE) == (createdPresetId != null && brush != null))
        if (finalState == BrushImportFinalState.REJECTED_UNSUPPORTED) require(rejectionReasons.isNotEmpty())
    }
}

sealed interface BrushRenderProbeResult {
    data object Succeeded : BrushRenderProbeResult
    data class Failed(val reason: String) : BrushRenderProbeResult
}

/** Immutable source-of-truth for a complete import session. */
data class BrushImportLedger(
    val sourceFilesScanned: Int,
    val sourceContainers: Int,
    val records: List<BrushImportRecord>,
    val groupsCreated: Int = 0,
) {
    init {
        require(sourceFilesScanned >= 0 && sourceContainers >= 0 && groupsCreated >= 0)
        require(records.map(BrushImportRecord::sourceBrushId).distinct().size == records.size) {
            "Every discovered source brush needs exactly one ledger record"
        }
        require(summary.brushesDiscovered == summary.importedUsable + summary.rejectedUnsupported +
            summary.failedDecode + summary.failedAsset + summary.failedCompile + summary.failedRender)
    }

    val summary: BrushImportSummary get() = BrushImportSummary(
        sourceFilesScanned = sourceFilesScanned,
        sourceContainers = sourceContainers,
        brushesDiscovered = records.size,
        parsed = records.count { it.decodeStatus == BrushImportStageStatus.SUCCEEDED },
        mapped = records.count { it.mappingStatus == BrushImportStageStatus.SUCCEEDED },
        renderable = records.count { it.capabilityStatus == BrushImportStageStatus.SUCCEEDED },
        importedUsable = records.count { it.finalState == BrushImportFinalState.IMPORTED_USABLE },
        rejectedUnsupported = records.count { it.finalState == BrushImportFinalState.REJECTED_UNSUPPORTED },
        failedDecode = records.count { it.finalState == BrushImportFinalState.FAILED_DECODE },
        failedAsset = records.count { it.finalState == BrushImportFinalState.FAILED_ASSET },
        failedCompile = records.count { it.finalState == BrushImportFinalState.FAILED_COMPILE },
        failedRender = records.count { it.finalState == BrushImportFinalState.FAILED_RENDER },
        groupsDiscovered = records.asSequence().map(BrushImportRecord::sourceGroup)
            .map(String::trim).filter(String::isNotEmpty).distinct().count(),
        groupsCreated = groupsCreated,
)

    fun withGroupsCreated(value: Int) = copy(groupsCreated = value)

    companion object {
        fun classify(
            filesScanned: Int,
            brushes: List<ImportedBrush>,
            assetValidator: (String) -> Boolean = { path -> File(path).isFile && File(path).length() > 0L },
            renderProbe: (ImportedBrush) -> BrushRenderProbeResult = {
                BrushRenderProbeResult.Failed("PRODUCTION_RENDER_PROBE_REQUIRED")
            },
        ): BrushImportLedger = BrushImportLedger(
            sourceFilesScanned = filesScanned,
            sourceContainers = brushes.mapNotNull(ImportedBrush::sha256).distinct().size
                .coerceAtLeast(if (filesScanned > 0) 1 else 0),
            records = brushes.map { classifyBrush(it, assetValidator, renderProbe) },
        )

        fun combine(ledgers: List<BrushImportLedger>): BrushImportLedger = BrushImportLedger(
            sourceFilesScanned = ledgers.sumOf(BrushImportLedger::sourceFilesScanned),
            sourceContainers = ledgers.sumOf(BrushImportLedger::sourceContainers),
            records = ledgers.flatMap(BrushImportLedger::records),
            groupsCreated = ledgers.sumOf(BrushImportLedger::groupsCreated),
        )

        @Suppress("LongMethod")
        private fun classifyBrush(
            brush: ImportedBrush,
            assetValidator: (String) -> Boolean,
            renderProbe: (ImportedBrush) -> BrushRenderProbeResult,
        ): BrushImportRecord {
            val parserFailure = brush.report.firstOrNull { line ->
                line.contains("parsing failed", ignoreCase = true) || line.contains("could not be decoded", ignoreCase = true)
            }
            val sourceFile = brush.sourcePath?.let(::File)?.name ?: brush.name
            val container = brush.sha256 ?: brush.sourcePath ?: "${brush.format}:$sourceFile"
            fun record(
                finalState: BrushImportFinalState,
                decode: BrushImportStageStatus,
                asset: BrushImportStageStatus,
                mapping: BrushImportStageStatus,
                capability: BrushImportStageStatus,
                compile: BrushImportStageStatus,
                probe: BrushImportStageStatus,
                commit: BrushImportStageStatus,
                reasons: Set<String> = emptySet(),
                presetId: String? = null,
                admitted: ImportedBrush? = null,
            ) = BrushImportRecord(
                sourceFile, container, brush.id, brush.name, brush.groupName,
                decode, asset, mapping, capability, compile, probe, commit, finalState,
                rejectionReasons = reasons,
                unsupportedSemantics = brush.semanticMappings.filterNot {
                    it.executionDisposition == SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED
                }.mapTo(linkedSetOf()) { it.sourceSemantic },
                createdPresetId = presetId,
                brush = admitted ?: brush,
            )
            if (parserFailure != null) return record(
                BrushImportFinalState.FAILED_DECODE, BrushImportStageStatus.FAILED,
                BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                setOf(parserFailure),
            )
            val assetPaths = buildList {
                addAll(brush.tipPreviewPaths)
                brush.tipPreviewPath?.let(::add)
                brush.grainPreviewPath?.let(::add)
                addAll(brush.dualTipPreviewPaths)
                brush.dualGrainPreviewPath?.let(::add)
            }.distinct()
            val assetSuccess = if (assetPaths.isEmpty()) BrushImportStageStatus.NOT_REQUIRED else BrushImportStageStatus.SUCCEEDED
            val missingAssets = assetPaths.filterNot(assetValidator)
            if (missingAssets.isNotEmpty()) return record(
                BrushImportFinalState.FAILED_ASSET, BrushImportStageStatus.SUCCEEDED,
                BrushImportStageStatus.FAILED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                missingAssets.mapTo(linkedSetOf()) { "Unreadable brush asset: $it" },
            )
            val encoded = brush.v5PresetJson
            if (encoded == null) {
                val explicit = brush.report.filter { it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") }
                    .mapTo(linkedSetOf()) { it.substringAfter(':') }
                val rejected = explicit.isNotEmpty() || brush.compatibility == Compatibility.FAILED
                return record(
                    if (rejected) BrushImportFinalState.REJECTED_UNSUPPORTED else BrushImportFinalState.FAILED_COMPILE,
                    BrushImportStageStatus.SUCCEEDED, assetSuccess, BrushImportStageStatus.SUCCEEDED,
                    if (rejected) BrushImportStageStatus.UNSUPPORTED else BrushImportStageStatus.NOT_REQUIRED,
                    if (rejected) BrushImportStageStatus.NOT_REQUIRED else BrushImportStageStatus.FAILED,
                    BrushImportStageStatus.NOT_REQUIRED, BrushImportStageStatus.NOT_REQUIRED,
                    explicit.ifEmpty { setOf("Brush Preset V5 compilation failed") },
                )
            }
            val preset = BrushPresetV5Codec.decodeOrNull(encoded) ?: return record(
                BrushImportFinalState.FAILED_COMPILE, BrushImportStageStatus.SUCCEEDED,
                assetSuccess, BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.FAILED, BrushImportStageStatus.NOT_REQUIRED,
                BrushImportStageStatus.NOT_REQUIRED, setOf("Invalid Brush Preset V5 payload"),
            )
            val program = runCatching { CompiledBrushProgramV5.compile(preset) }.getOrElse { failure ->
                return record(
                    BrushImportFinalState.FAILED_COMPILE, BrushImportStageStatus.SUCCEEDED,
                    assetSuccess, BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.NOT_REQUIRED,
                    BrushImportStageStatus.FAILED, BrushImportStageStatus.NOT_REQUIRED,
                    BrushImportStageStatus.NOT_REQUIRED,
                    setOf(failure.message ?: "Brush Preset V5 compilation failed"),
                )
            }
            return when (val capability = BrushExecutionCapabilityResolver.resolve(program)) {
                BrushExecutionCapabilityResolution.Supported -> when (val probe = runCatching { renderProbe(brush) }
                    .getOrElse { BrushRenderProbeResult.Failed(it.message ?: "Production render probe failed") }) {
                    BrushRenderProbeResult.Succeeded -> record(
                        BrushImportFinalState.IMPORTED_USABLE, BrushImportStageStatus.SUCCEEDED,
                        assetSuccess, BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.SUCCEEDED,
                        BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.SUCCEEDED,
                        BrushImportStageStatus.SUCCEEDED, presetId = brush.id, admitted = brush,
                    )
                    is BrushRenderProbeResult.Failed -> record(
                        BrushImportFinalState.FAILED_RENDER, BrushImportStageStatus.SUCCEEDED,
                        assetSuccess, BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.SUCCEEDED,
                        BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.FAILED,
                        BrushImportStageStatus.NOT_REQUIRED, setOf(probe.reason), admitted = brush,
                    )
                }
                is BrushExecutionCapabilityResolution.Unsupported -> record(
                    BrushImportFinalState.REJECTED_UNSUPPORTED, BrushImportStageStatus.SUCCEEDED,
                    assetSuccess, BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.UNSUPPORTED,
                    BrushImportStageStatus.SUCCEEDED, BrushImportStageStatus.NOT_REQUIRED,
                    BrushImportStageStatus.NOT_REQUIRED,
                    capability.reasons.mapTo(linkedSetOf()) { it.name },
                )
            }
        }
    }
}

data class BrushImportSummary(
    val sourceFilesScanned: Int,
    val sourceContainers: Int,
    val brushesDiscovered: Int,
    val parsed: Int,
    val mapped: Int,
    val renderable: Int,
    val importedUsable: Int,
    val rejectedUnsupported: Int,
    val failedDecode: Int,
    val failedAsset: Int,
    val failedCompile: Int,
    val failedRender: Int,
    val groupsDiscovered: Int,
    val groupsCreated: Int,
) {
    val failed: Int get() = failedDecode + failedAsset + failedCompile + failedRender
}

data class BrushImportBatch(val ledger: BrushImportLedger) {
    val renderableBrushes: List<ImportedBrush> get() = ledger.records.mapNotNull { record ->
        record.brush?.takeIf { record.finalState == BrushImportFinalState.IMPORTED_USABLE }
    }
    val rejectedBrushes: List<ImportedBrush> get() = ledger.records.mapNotNull { record ->
        record.brush?.takeIf { record.finalState == BrushImportFinalState.REJECTED_UNSUPPORTED }
    }
    val failedBrushes: List<ImportedBrush> get() = ledger.records.mapNotNull { record ->
        record.brush?.takeIf { record.finalState !in setOf(BrushImportFinalState.IMPORTED_USABLE, BrushImportFinalState.REJECTED_UNSUPPORTED) }
    }
    val summary: BrushImportSummary get() = ledger.summary

    companion object {
        fun classify(
            filesScanned: Int,
            brushes: List<ImportedBrush>,
            renderProbe: (ImportedBrush) -> BrushRenderProbeResult,
        ): BrushImportBatch = BrushImportBatch(
            BrushImportLedger.classify(filesScanned, brushes, renderProbe = renderProbe),
        )
    }
}
