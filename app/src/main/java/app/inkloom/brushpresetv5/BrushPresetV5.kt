package app.inkloom.brushpresetv5

import app.inkloom.document.BrushCurvePoint
import kotlinx.serialization.Serializable

/**
 * Versioned, user-facing brush preset schema (schemaVersion = 5).
 *
 * Design rules:
 *  1. A preset describes user-configurable brush semantics only — never renderer internals
 *     (no ribbon vertices, tile surfaces, footprint shaders, engine helper names).
 *  2. Capability-gated sub-structures: each block is non-null only when the engine family makes
 *     it meaningful, so no preset carries a bag of permanently-inactive fields.
 *  3. `tipTexture` and `paperGrain` are distinct concepts.
 *  4. `pressure -> deposition`, `tilt -> width`, `tilt -> grain` are distinct dynamics channels.
 *
 * Immutable by construction; a stroke consumes a compiled snapshot, never a mutable editor copy.
 */
@Serializable
data class BrushPresetV5(
    val schemaVersion: Int = 5,
    val identity: PresetIdentity,
    /**
     * Deterministic seed for the brush's stochastic behavior (tip rotation, scatter, texture
     * variation). Independent of [PresetIdentity.id]: Duplicate / Save As keep this exact seed so
     * identical settings produce identical raster output; only an explicit "randomize texture"
     * operation replaces it.
     */
    val renderSeed: Int,
    /** Paint deposits the foreground colour; erase removes destination alpha with the same tip. */
    val output: BrushOutput = BrushOutput.PAINT,
    val tip: TipSettings,
    val deposition: DepositionSettings,
    /** Present for stamped families that expose spacing. */
    val spacing: SpacingSettings? = null,
    val dynamics: DynamicsSettings,
    /** Present only for families that support document-space paper grain. */
    val paperGrain: PaperGrainSettings? = null,
    /** Imported document/tip texture sampled by stamp programs. */
    val textureGrain: TextureGrainSettings? = null,
    /** Present only for families that support tip texture. */
    val tipTexture: TipTextureSettings? = null,
    /** Present only for families that support taper / smoothing. */
    val stroke: StrokeSettings? = null,
    /** Raster stamp emission semantics; null selects the family's continuous depositor. */
    val stamp: StampSettings? = null,
    /** Pigment pickup/deposition semantics for wet families. */
    val wetMix: WetMixSettings? = null,
    /** Optional second independently-authored stamp stream. */
    val dual: DualBrushSettings? = null,
    /** Reserved for family-specific knobs that are not part of the common contract. */
    val engineOptions: EngineOptions = EngineOptions(),
)

@Serializable
enum class BrushOutput { PAINT, ERASE }

@Serializable
data class PresetIdentity(
    val id: String,
    val name: String,
    val version: Int = 1,
    val family: BrushFamily,
    /**
     * The brush id the render engine recognizes (e.g. "system-hb-pencil", "system-pen"). Kept
     * separate from [id] so a duplicated / renamed user preset still renders through the same
     * procedural tip and ribbon path it was created from, without identity changes leaking into
     * rendering.
     */
    val renderBrushId: String,
)

@Serializable
data class TipSettings(
    /** Normalized footprint diameter at reference width. */
    val size: Float = 5f,
    /** Aspect (width/height) of the footprint; 1 = round. */
    val aspect: Float = 1f,
    /** Rotation in degrees (fixed stamp rotation; independent of stroke-follow). */
    val rotationDegrees: Float = 0f,
    /** 0 (soft) .. 1 (hard) edge. */
    val hardness: Float = 1f,
)

@Serializable
data class DepositionSettings(
    val opacity: Float = 1f,
    val flow: Float = 1f,
    /** How much pigment builds up on repeated passes (buildup). */
    val buildup: Float = 0f,
)

@Serializable
data class SpacingSettings(
    /** FIXED or RELATIVE (fraction of diameter). */
    val mode: SpacingMode = SpacingMode.RELATIVE,
    /** Fraction of diameter (RELATIVE) or absolute value (FIXED). */
    val value: Float = .12f,
)

@Serializable
enum class SpacingMode { FIXED, RELATIVE }

@Serializable
data class DynamicsSettings(
    val pressure: PressureMappings,
    val tilt: TiltMappings,
    val velocity: VelocityMappings? = null,
    val direction: DirectionMappings? = null,
)

/** Pressure -> width, pressure -> deposition (opacity) and pressure -> flow are distinct channels. */
@Serializable
data class PressureMappings(
    val widthEnabled: Boolean = true,
    val widthMinimum: Float = .35f,
    val widthCurve: List<BrushCurvePoint> = emptyList(),
    val depositionEnabled: Boolean = false,
    val depositionMinimum: Float = 0f,
    val depositionCurve: List<BrushCurvePoint> = emptyList(),
    val flowEnabled: Boolean = false,
    val flowMinimum: Float = 0f,
    val flowCurve: List<BrushCurvePoint> = emptyList(),
)

@Serializable
data class TiltMappings(
    /** Tilt widens the footprint perpendicular to the stroke (width/aspect). */
    val widthEnabled: Boolean = false,
    val widthStart: Float = 0f,
    /** Max extra width multiplier at full tilt (0..3). */
    val widthMaximum: Float = 0f,
    /** Tilt boosts document-space paper grain density. */
    val grainEnabled: Boolean = false,
    val grainStart: Float = 0f,
    /** Max grain-density multiplier at full tilt (0..2). */
    val grainMaximum: Float = 0f,
)

@Serializable
data class VelocityMappings(
    val enabled: Boolean = false,
    val minimum: Float = 0f,
    val scale: Float = 1f,
    val curve: List<BrushCurvePoint> = emptyList(),
)

@Serializable
data class DirectionMappings(
    val enabled: Boolean = false,
    val minimum: Float = 0f,
    val curve: List<BrushCurvePoint> = emptyList(),
)

@Serializable
data class PaperGrainSettings(
    /** Document-anchored grain, separate from tip texture. */
    val scale: Float = 100f,
    val density: Float = .5f,
    /** 0..1 grain strength applied to deposition. */
    val strength: Float = 1f,
)

@Serializable
data class TextureGrainSettings(
    val assetPath: String,
    val density: Float = 0f,
    val scale: Float = 100f,
    val movement: Float = 0f,
    val zoom: Float = 0f,
    val rotationDegrees: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val reversed: Boolean = false,
    val emphasized: Boolean = false,
    val offsetJitter: Boolean = false,
)

@Serializable
data class TipTextureSettings(
    /** True = deterministic procedural tooth; false = imported tip asset. */
    val procedural: Boolean = true,
    /** Imported tip asset path (when procedural is false). */
    val assetPath: String? = null,
    /** Ordered multi-tip sequence. Empty uses [assetPath] for presets authored before this field. */
    val assetPaths: List<String> = emptyList(),
    /** Per-grade tooth strength for procedural graphite tips (0..1). */
    val toothStrength: Float = .8f,
)

@Serializable
data class StrokeSettings(
    val smoothing: Float = 0f,
    val taperStartEnabled: Boolean = false,
    val taperStartLength: Float = .2f,
    val taperEndEnabled: Boolean = false,
    val taperEndLength: Float = .2f,
    val postCorrection: Float = 0f,
)

@Serializable
data class StampSettings(
    val followStrokeDirection: Boolean = false,
    val followStylusOrientation: Boolean = false,
    val randomRotationDegrees: Float = 0f,
    val particlesPerPlot: Int = 1,
    val continuousPlot: Boolean = false,
    val spray: SpraySettings? = null,
)

@Serializable
data class SpraySettings(
    val particleSize: Float = 100f,
    /** 0 = relative percent of brush diameter; 1 = document-reference pixels. */
    val particleSizeUnit: Int = 0,
    val particleSizeTracksBrush: Boolean = true,
    val density: Float = 1f,
    val bias: Float = 0f,
    val spread: Float = 1f,
    val particleRotationDegrees: Float = 0f,
    val followStrokeDirection: Boolean = false,
    val followStylusOrientation: Boolean = false,
    val randomRotationDegrees: Float = 0f,
)

@Serializable
data class WetMixSettings(
    val dilution: Float = 0f,
    val charge: Float = 0f,
    val attack: Float = 0f,
    val pull: Float = 0f,
)

@Serializable
data class DualBrushSettings(
    val tipAssetPaths: List<String>,
    val sizeScale: Float = 1f,
    val spacing: SpacingSettings = SpacingSettings(),
    val flow: Float = 1f,
    val hardness: Float = 1f,
    val aspect: Float = 1f,
    val rotationDegrees: Float = 0f,
    val followStrokeDirection: Boolean = false,
    val followStylusOrientation: Boolean = false,
    val randomRotationDegrees: Float = 0f,
    /** The two brush-density streams are combined before a single deposit reaches the layer. */
    val compositeMode: DualCompositeMode = DualCompositeMode.NORMAL,
    val textureGrain: TextureGrainSettings? = null,
    val spray: SpraySettings? = null,
)

@Serializable
enum class DualCompositeMode {
    NORMAL,
    MULTIPLY,
    ADD_GLOW,
    SUBTRACT,
    DARKEN,
    LIGHTEN,
    SCREEN,
    OVERLAY,
    COLOR_DODGE,
    COLOR_BURN,
    LINEAR_BURN,
    HARD_MIX,
    HEIGHT_LINEAR,
}

@Serializable
data class EngineOptions(
    /** Reserved for family-specific options; kept empty for the two reference families. */
    val values: Map<String, String> = emptyMap(),
)
