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
    val output: BrushOutput,
    val capabilities: Set<BrushCapability>,
    val backend: RenderBackend,
    val tip: CompiledTip,
    val deposition: CompiledDeposition,
    val spacingPolicy: CompiledSpacing?,
    val pressure: CompiledPressure,
    val tilt: CompiledTilt,
    val grainSampler: CompiledGrainSampler?,
    val textureGrain: TextureGrainSettings?,
    val strokeShaping: CompiledStrokeShaping,
    val stamp: StampSettings?,
    val wetMix: WetMixSettings?,
    val dual: DualBrushSettings?,
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
        val assetPaths: List<String>,
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
        val widthMinimum: Float,
        val widthStart: Float,
        val widthCurve: List<BrushCurvePoint>,
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
            preset.stamp?.let { stamp ->
                require(stamp.randomRotationDegrees.isFinite() && stamp.randomRotationDegrees >= 0f)
                require(stamp.particlesPerPlot in 1..32)
                stamp.spray?.let(::requireValidSpray)
            }
            preset.textureGrain?.let(::requireValidTextureGrain)
            preset.dual?.let { dual ->
                require(dual.tipAssetPaths.isNotEmpty() && dual.tipAssetPaths.none(String::isBlank))
                require(dual.sizeScale.isFinite() && dual.sizeScale in .01f..8f)
                require(dual.flow.isFinite() && dual.flow in 0f..1f)
                require(dual.hardness.isFinite() && dual.hardness in 0f..1f)
                require(dual.aspect.isFinite() && dual.aspect in .01f..1f)
                require(dual.randomRotationDegrees.isFinite() && dual.randomRotationDegrees >= 0f)
                require(dual.spacing.value.isFinite() && dual.spacing.value in .005f..4f)
                dual.textureGrain?.let(::requireValidTextureGrain)
                dual.spray?.let(::requireValidSpray)
            }
            val family = preset.identity.family
            val capabilities = BrushCapabilities.of(family)
            val importedTipPaths = preset.tipTexture?.let { texture ->
                texture.assetPaths.ifEmpty { listOfNotNull(texture.assetPath) }
            }.orEmpty().distinct()
            val hasImportedTip = importedTipPaths.isNotEmpty()
            return CompiledBrushProgramV5(
                preset = preset,
                output = preset.output,
                capabilities = capabilities,
                backend = BrushCapabilities.backend(family, hasImportedTip),
                tip = CompiledTip(
                    size = preset.tip.size,
                    aspect = preset.tip.aspect,
                    rotationDegrees = preset.tip.rotationDegrees,
                    hardness = preset.tip.hardness,
                    tipTexture = preset.tipTexture?.let {
                        CompiledTipTexture(
                            procedural = it.procedural,
                            assetPath = importedTipPaths.firstOrNull(),
                            assetPaths = importedTipPaths,
                            toothStrength = it.toothStrength,
                        )
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
                    widthMinimum = preset.dynamics.tilt.widthMinimum,
                    widthStart = preset.dynamics.tilt.widthStart,
                    widthCurve = preset.dynamics.tilt.widthCurve.toList(),
                    widthMaximum = preset.dynamics.tilt.widthMaximum,
                    grainEnabled = preset.dynamics.tilt.grainEnabled,
                    grainStart = preset.dynamics.tilt.grainStart,
                    grainMaximum = preset.dynamics.tilt.grainMaximum,
                ),
                grainSampler = preset.paperGrain?.let {
                    CompiledGrainSampler(it.scale, it.density, it.strength)
                },
                textureGrain = preset.textureGrain,
                strokeShaping = CompiledStrokeShaping(
                    smoothing = preset.stroke?.smoothing ?: 0f,
                    taperStartEnabled = preset.stroke?.taperStartEnabled ?: false,
                    taperStartLength = preset.stroke?.taperStartLength ?: .2f,
                    taperEndEnabled = preset.stroke?.taperEndEnabled ?: false,
                    taperEndLength = preset.stroke?.taperEndLength ?: .2f,
                    postCorrection = preset.stroke?.postCorrection ?: 0f,
                ),
                stamp = preset.stamp,
                wetMix = preset.wetMix,
                dual = preset.dual,
            )
        }

        private fun requireValidTextureGrain(grain: TextureGrainSettings) {
            require(grain.assetPath.isNotBlank())
            require(grain.density.isFinite() && grain.density in 0f..1f)
            require(grain.scale.isFinite() && grain.scale in 1f..4000f)
            require(grain.movement.isFinite() && grain.movement in 0f..1f)
            require(grain.zoom.isFinite() && grain.zoom in 0f..1f)
            require(grain.rotationDegrees.isFinite())
        }

        private fun requireValidSpray(spray: SpraySettings) {
            require(spray.particleSize.isFinite() && spray.particleSize > 0f)
            require(spray.density.isFinite() && spray.density > 0f)
            require(spray.bias.isFinite() && spray.bias in -100f..100f)
            require(spray.spread.isFinite() && spray.spread >= 0f)
        }
    }
}
