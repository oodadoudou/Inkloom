package app.inkloom.brushpresetv5

import app.inkloom.document.BrushCurvePoint

/**
 * Immutable, stroke-scoped compile of a [BrushPresetV5]. A stroke is drawn from exactly one
 * compiled snapshot; later edits to the (mutable) editor preset never change an active stroke.
 *
 * The compiled program carries the user-facing semantics resolved to renderer-ready values:
 * tip reference, deposition constants, spacing policy, resolved dynamics curves, grain sampler
 * config, stroke shaping, and the resolved execution [backend]. It does not expose renderer
 * implementation objects.
 */
class CompiledBrushProgramV5 private constructor(
    val preset: BrushPresetV5,
    val capabilities: Set<BrushCapability>,
    val backend: RenderBackend,
    val tip: CompiledTip,
    val deposition: CompiledDeposition,
    val spacingPolicy: CompiledSpacing?,
    val pressure: CompiledPressure,
    val tilt: CompiledTilt,
    val grainSampler: CompiledGrainSampler?,
    val strokeShaping: CompiledStrokeShaping,
) {
    /** The preset a stroke was compiled from (identity only; not editable mid-stroke). */
    val identity: PresetIdentity get() = preset.identity

    /** Renderer-recognized brush id (rendering identity, decoupled from the preset id). */
    val renderBrushId: String get() = preset.identity.renderBrushId

    /** Deterministic stochastic seed for this preset's texture/scatter behavior. */
    val renderSeed: Int get() = preset.renderSeed

    data class CompiledTip(
        val size: Float,
        val aspect: Float,
        val rotationDegrees: Float,
        val hardness: Float,
        val tipTexture: CompiledTipTexture?,
    )

    data class CompiledTipTexture(
        val procedural: Boolean,
        val assetPath: String?,
        val toothStrength: Float,
    )

    data class CompiledDeposition(val opacity: Float, val flow: Float, val buildup: Float)

    data class CompiledSpacing(val mode: SpacingMode, val value: Float)

    data class CompiledPressure(
        val widthEnabled: Boolean,
        val widthMinimum: Float,
        val widthCurve: List<BrushCurvePoint>,
        val depositionEnabled: Boolean,
        val depositionMinimum: Float,
        val depositionCurve: List<BrushCurvePoint>,
        val flowEnabled: Boolean,
        val flowMinimum: Float,
        val flowCurve: List<BrushCurvePoint>,
    )

    data class CompiledTilt(
        val widthEnabled: Boolean,
        val widthStart: Float,
        val widthMaximum: Float,
        val grainEnabled: Boolean,
        val grainStart: Float,
        val grainMaximum: Float,
    )

    data class CompiledGrainSampler(
        val scale: Float,
        val density: Float,
        val strength: Float,
        /** V5 paper grain is always document-anchored (distinct from tip texture). */
        val coordinateSpace: GrainCoordinateSpace = GrainCoordinateSpace.DOCUMENT,
    )

    enum class GrainCoordinateSpace { DOCUMENT }

    data class CompiledStrokeShaping(
        val smoothing: Float,
        val taperStartEnabled: Boolean,
        val taperStartLength: Float,
        val taperEndEnabled: Boolean,
        val taperEndLength: Float,
        val postCorrection: Float,
    )

    companion object {
        /** Compiles only V5 semantics. There is intentionally no InkStroke/legacy renderer exit. */
        fun compile(preset: BrushPresetV5): CompiledBrushProgramV5 {
            require(preset.schemaVersion == 5) { "Unsupported brush preset schema ${preset.schemaVersion}" }
            require(preset.identity.id.isNotBlank() && preset.identity.renderBrushId.isNotBlank()) {
                "A V5 preset needs stable library and render identities"
            }
            require(preset.tip.size.isFinite() && preset.tip.size > 0f) { "Brush size must be finite and positive" }
            require(preset.tip.aspect.isFinite() && preset.tip.aspect > 0f) { "Tip aspect must be finite and positive" }
            val family = preset.identity.family
            val capabilities = BrushCapabilities.of(family)
            val hasImportedTip = preset.tipTexture?.assetPath != null
            return CompiledBrushProgramV5(
                preset = preset,
                capabilities = capabilities,
                backend = BrushCapabilities.backend(family, hasImportedTip),
                tip = CompiledTip(
                    size = preset.tip.size,
                    aspect = preset.tip.aspect,
                    rotationDegrees = preset.tip.rotationDegrees,
                    hardness = preset.tip.hardness,
                    tipTexture = preset.tipTexture?.let {
                        CompiledTipTexture(it.procedural, it.assetPath, it.toothStrength)
                    },
                ),
                deposition = CompiledDeposition(preset.deposition.opacity, preset.deposition.flow, preset.deposition.buildup),
                spacingPolicy = preset.spacing?.let { CompiledSpacing(it.mode, it.value) },
                pressure = CompiledPressure(
                    widthEnabled = preset.dynamics.pressure.widthEnabled,
                    widthMinimum = preset.dynamics.pressure.widthMinimum,
                    widthCurve = preset.dynamics.pressure.widthCurve.toList(),
                    depositionEnabled = preset.dynamics.pressure.depositionEnabled,
                    depositionMinimum = preset.dynamics.pressure.depositionMinimum,
                    depositionCurve = preset.dynamics.pressure.depositionCurve.toList(),
                    flowEnabled = preset.dynamics.pressure.flowEnabled,
                    flowMinimum = preset.dynamics.pressure.flowMinimum,
                    flowCurve = preset.dynamics.pressure.flowCurve.toList(),
                ),
                tilt = CompiledTilt(
                    widthEnabled = preset.dynamics.tilt.widthEnabled,
                    widthStart = preset.dynamics.tilt.widthStart,
                    widthMaximum = preset.dynamics.tilt.widthMaximum,
                    grainEnabled = preset.dynamics.tilt.grainEnabled,
                    grainStart = preset.dynamics.tilt.grainStart,
                    grainMaximum = preset.dynamics.tilt.grainMaximum,
                ),
                grainSampler = preset.paperGrain?.let {
                    CompiledGrainSampler(it.scale, it.density, it.strength)
                },
                strokeShaping = CompiledStrokeShaping(
                    smoothing = preset.stroke?.smoothing ?: 0f,
                    taperStartEnabled = preset.stroke?.taperStartEnabled ?: false,
                    taperStartLength = preset.stroke?.taperStartLength ?: .2f,
                    taperEndEnabled = preset.stroke?.taperEndEnabled ?: false,
                    taperEndLength = preset.stroke?.taperEndLength ?: .2f,
                    postCorrection = preset.stroke?.postCorrection ?: 0f,
                ),
            )
        }
    }
}
