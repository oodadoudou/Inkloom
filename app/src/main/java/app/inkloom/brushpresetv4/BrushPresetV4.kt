package app.inkloom.brushpresetv4

import app.inkloom.document.BrushCurvePoint
import kotlinx.serialization.Serializable

/**
 * Versioned, user-facing brush preset schema (schemaVersion = 4).
 *
 * Design rules (see BRUSH_PRESET_V4_FOUNDATION):
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
data class BrushPresetV4(
    val schemaVersion: Int = 4,
    val identity: PresetIdentity,
    val tip: TipSettings,
    val deposition: DepositionSettings,
    /** Present for stamped families that expose spacing. */
    val spacing: SpacingSettings? = null,
    val dynamics: DynamicsSettings,
    /** Present only for families that support document-space paper grain. */
    val paperGrain: PaperGrainSettings? = null,
    /** Present only for families that support tip texture. */
    val tipTexture: TipTextureSettings? = null,
    /** Present only for families that support taper / smoothing. */
    val stroke: StrokeSettings? = null,
    /** Reserved for family-specific knobs that are not part of the common contract. */
    val engineOptions: EngineOptions = EngineOptions(),
)

@Serializable
data class PresetIdentity(
    val id: String,
    val name: String,
    val version: Int = 1,
    val engineFamily: BrushEngineFamily,
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

/** Pressure -> width and pressure -> deposition are distinct channels. */
@Serializable
data class PressureMappings(
    val widthEnabled: Boolean = true,
    val widthMinimum: Float = .35f,
    val widthCurve: List<BrushCurvePoint> = emptyList(),
    val depositionEnabled: Boolean = false,
    val depositionMinimum: Float = 0f,
    val depositionCurve: List<BrushCurvePoint> = emptyList(),
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
data class TipTextureSettings(
    /** True = deterministic procedural tooth; false = imported tip asset. */
    val procedural: Boolean = true,
    /** Imported tip asset path (when procedural is false). */
    val assetPath: String? = null,
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
data class EngineOptions(
    /** Reserved for family-specific options; kept empty for the two reference families. */
    val values: Map<String, String> = emptyMap(),
)
