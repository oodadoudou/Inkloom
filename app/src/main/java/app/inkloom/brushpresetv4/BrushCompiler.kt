package app.inkloom.brushpresetv4

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import java.util.UUID

/**
 * Immutable, stroke-scoped compile of a [BrushPresetV4]. A stroke is drawn from exactly one
 * compiled snapshot; later edits to the (mutable) editor preset never change an active stroke.
 *
 * The compiled program carries the user-facing semantics resolved to renderer-ready values:
 * tip reference, deposition constants, spacing policy, resolved dynamics curves, grain sampler
 * config, stroke shaping, and the resolved execution [backend]. It does not expose renderer
 * implementation objects.
 */
class CompiledBrushProgram private constructor(
    val preset: BrushPresetV4,
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
        /** V4 paper grain is always document-anchored (distinct from tip texture). */
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

    /**
     * Produces the renderer input (an [InkStroke]) for this snapshot. The renderer keeps operating
     * on [InkStroke] (the verified input of the goldens); the compiled preset only feeds it a
     * frozen parameter set so no mid-stroke editor mutation can leak in.
     *
     * The render identity comes from the preset ([renderBrushId]), the stochastic seed from
     * [renderSeed] — never from the stroke [id], which is just document identity (defaults to a
     * fresh UUID for each stroke, exactly like the legacy document path). This keeps Duplicate /
     * Save As byte-identical to the source preset.
     */
    fun toInkStroke(
        colorArgb: Long,
        points: List<StrokePoint>,
        id: String = UUID.randomUUID().toString(),
    ): InkStroke = buildInkStroke(colorArgb, points, id)

    private fun buildInkStroke(colorArgb: Long, points: List<StrokePoint>, id: String): InkStroke {
        // Continuous ribbon is a CONTINUOUS-family behavior; the renderer gates ribbon on the
        // brush id (system-pen / system-ps19), so the snapshot just records the family intent.
        val continuous = preset.identity.family == BrushFamily.INK
        val engineKind = legacyEngineKind(backend)
        return InkStroke(
            id = id,
            renderSeed = renderSeed,
            brushId = renderBrushId,
            engineKind = engineKind,
            points = points,
            colorArgb = colorArgb,
            width = tip.size,
            opacity = deposition.opacity,
            flow = deposition.flow,
            hardness = tip.hardness,
            thickness = tip.aspect,
            rotationDegrees = tip.rotationDegrees,
            spacing = spacingPolicy?.value ?: .12f,
            spacingMode = app.inkloom.document.CspBrushGap.FIXED,
            sizePressureEnabled = pressure.widthEnabled,
            sizePressureMinimum = pressure.widthMinimum,
            sizePressureCurve = pressure.widthCurve,
            opacityPressureEnabled = pressure.depositionEnabled,
            opacityPressureMinimum = pressure.depositionMinimum,
            opacityPressureCurve = pressure.depositionCurve,
            flowPressureEnabled = pressure.flowEnabled,
            flowPressureMinimum = pressure.flowMinimum,
            flowPressureCurve = pressure.flowCurve,
            graphiteTiltWidth = if (tilt.widthEnabled) tilt.widthMaximum else 0f,
            graphiteTiltWidthStart = tilt.widthStart,
            graphiteTiltGrain = if (tilt.grainEnabled) tilt.grainMaximum else 0f,
            graphiteTiltGrainStart = tilt.grainStart,
            proceduralPaperGrainEnabled = grainSampler != null,
            proceduralPaperGrainScale = grainSampler?.scale ?: 100f,
            proceduralPaperGrainDensity = grainSampler?.density ?: 0f,
            proceduralPaperGrainStrength = grainSampler?.strength ?: 1f,
            ribbon = continuous,
            continuousPlot = continuous,
            taperStartEnabled = strokeShaping.taperStartEnabled,
            taperStartLength = strokeShaping.taperStartLength,
            taperEndEnabled = strokeShaping.taperEndEnabled,
            taperEndLength = strokeShaping.taperEndLength,
        )
    }

    companion object {
        /**
         * Maps a V4 [RenderBackend] to the legacy renderer [BrushEngineKind]. This is the single
         * place the legacy runtime kind is derived, so the semantic family and the execution
         * backend stay separate concepts. A procedural-tip pencil keeps the legacy MESH_INK kind
         * (its stamped behavior is driven by renderBrushId, exactly as the V3 path did), while an
         * imported-tip pencil/stamp uses STAMP_RASTER.
         */
        fun legacyEngineKind(backend: RenderBackend): BrushEngineKind = when (backend) {
            RenderBackend.CONTINUOUS_RIBBON, RenderBackend.PROCEDURAL_STAMP -> BrushEngineKind.MESH_INK
            RenderBackend.ASSET_STAMP -> BrushEngineKind.STAMP_RASTER
            RenderBackend.AIRBRUSH -> BrushEngineKind.AIRBRUSH
            RenderBackend.WET_PAINT -> BrushEngineKind.WET_SMUDGE
        }

        fun compile(preset: BrushPresetV4): CompiledBrushProgram {
            val family = preset.identity.family
            val capabilities = BrushCapabilities.of(family)
            val hasImportedTip = preset.tipTexture?.assetPath != null
            return CompiledBrushProgram(
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
