package app.inkloom.brushpresetv5

import app.inkloom.model.BrushFormat
import app.inkloom.model.BrushToolRole
import app.inkloom.model.ImportedBrush

/** One transient quick edit. No library identity is allocated until a changed gesture commits. */
class QuickBrushEditSession private constructor(
    val source: ImportedBrush,
    private val workingCopy: EditorWorkingCopy,
    val options: List<BrushOptionDescriptor>,
) {
    val current: BrushPresetV5 get() = workingCopy.current
    val changed: Boolean get() = current != workingCopy.source.preset

    fun change(optionId: String, value: Float): QuickBrushEditSession {
        require(value.isFinite()) { "Brush parameter must be finite" }
        val option = requireNotNull(options.firstOrNull { it.id == optionId }) {
            "Quick parameter $optionId is not supported by this brush"
        }
        return QuickBrushEditSession(source, workingCopy.update {
            option.apply(it, value.coerceIn(option.valueRange.start, option.valueRange.endInclusive))
        }, options)
    }

    /** An unchanged edit returns the source, so callers can skip both saving and selection. */
    fun commit(modifiedName: String = "${source.name} - Modified"): ImportedBrush {
        if (!changed) return source
        val revision = workingCopy.source.preset.identity.version + 1
        val owned = workingCopy.update { it.copy(identity = it.identity.copy(version = revision)) }
            .commit(modifiedName)
        requireExecutable(owned.preset)
        return BrushPresetV5Import.writePreset(source, owned.preset, BrushFormat.INKLOOM).copy(
            derivedFromFactoryId = if (source.format == BrushFormat.SYSTEM) source.id else source.derivedFromFactoryId,
        )
    }

    companion object {
        private val quickIds = listOf("flow", "pressureSizeEnabled", "smoothing", "rotation", "hardness", "paperGrainDensity")

        fun start(source: ImportedBrush): QuickBrushEditSession {
            require(source.toolBinding.role in setOf(BrushToolRole.PAINT, BrushToolRole.ERASE)) {
                "Quick brush properties apply to painting and erasing. Use the tool controls for this tool."
            }
            val preset = BrushPresetV5Import.requirePreset(source)
            require(preset.identity.id == source.id) { "Brush identity does not match its saved program" }
            requireExecutable(preset)
            val owned = OwnedBrushPreset(preset, if (source.format == BrushFormat.SYSTEM) {
                PresetOwnership.Factory(source.id)
            } else PresetOwnership.User(source.id, source.derivedFromFactoryId))
            val available = BrushOptionDescriptors.productionPanels(preset).flatMap { it.second }.associateBy { it.id }
            return QuickBrushEditSession(source, EditorWorkingCopy.start(owned), quickIds.mapNotNull(available::get))
        }

        private fun requireExecutable(preset: BrushPresetV5) {
            when (val result = BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(preset))) {
                BrushExecutionCapabilityResolution.Supported -> Unit
                is BrushExecutionCapabilityResolution.Unsupported -> error(result.reasons.joinToString { it.name })
            }
        }
    }
}
