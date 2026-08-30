package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolution
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolver
import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush

/** Counts only live, selectable presets admitted to a production V5 backend. */
object BrushLibraryInventory {
    data class Summary(val total: Int, val factory: Int, val imported: Int, val userCreated: Int)

    fun executable(brush: ImportedBrush): Boolean {
        if (brush.compatibility == Compatibility.FAILED) return false
        val preset = brush.v5PresetJson?.let(BrushPresetV5Codec::decodeOrNull) ?: return false
        val program = runCatching { CompiledBrushProgramV5.compile(preset) }.getOrNull() ?: return false
        return BrushExecutionCapabilityResolver.resolve(program) == BrushExecutionCapabilityResolution.Supported
    }

    fun importedUsable(brushes: List<ImportedBrush>): List<ImportedBrush> = brushes.asSequence()
        .filterNot { it.format == BrushFormat.SYSTEM }
        .filter(::executable)
        .distinctBy(ImportedBrush::id)
        .toList()

    fun selectable(factory: List<ImportedBrush>, stored: List<ImportedBrush>): List<ImportedBrush> {
        val factoryById = factory.asSequence().filter(::executable).associateBy(ImportedBrush::id)
        return (stored.asSequence().filter(::executable).filterNot { it.id in factoryById } + factoryById.values.asSequence())
            .distinctBy(ImportedBrush::id)
            .toList()
    }

    fun summarize(factory: List<ImportedBrush>, stored: List<ImportedBrush>): Summary {
        val live = selectable(factory, stored)
        return Summary(
            total = live.size,
            factory = live.count { it.format == BrushFormat.SYSTEM },
            imported = live.count { it.format !in setOf(BrushFormat.SYSTEM, BrushFormat.INKLOOM) },
            userCreated = live.count { it.format == BrushFormat.INKLOOM },
        )
    }
}
