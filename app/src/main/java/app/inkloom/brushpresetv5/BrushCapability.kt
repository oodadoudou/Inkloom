package app.inkloom.brushpresetv5

import kotlinx.serialization.Serializable

/**
 * Semantic brush families. A family describes *what kind of brush the user means* (ink, pencil,
 * airbrush, ...) and declares which high-level capabilities are meaningful, so a preset never
 * carries a bag of permanently-inactive renderer-specific fields.
 *
 * A family is deliberately separate from [RenderBackend]: the same family may execute through
 * different backends depending on its tip (e.g. a PENCIL renders procedurally when its tip is
 * procedural, but as an imported stamp when the user assigns an asset tip). UI must key panels off
 * the family's [BrushCapability] set, never off an execution backend, and vice versa.
 */
@Serializable
enum class BrushFamily(val label: String) {
    INK("Ink"),
    PENCIL("Pencil"),
    AIRBRUSH("Airbrush"),
    DECORATION("Decoration"),
    /** Pigment-mixing paint (Procreate wet mix, CSP color mixing). */
    WET_PAINT("Wet Paint"),
}

/**
 * Execution backends: how a compiled program actually emits pixels. This is an engine-level
 * concern and must never be conflated with the semantic [BrushFamily].
 *
 * - [CONTINUOUS_RIBBON]: emits a continuous ribbon along the path (ink/pen).
 * - [PROCEDURAL_STAMP]: stamps a procedurally-drawn tip along the path (procedural pencil).
 * - [ASSET_STAMP]: stamps an imported tip asset along the path (asset-tip pencil, decoration).
 * - [AIRBRUSH]: particle emission (airbrush).
 * - [WET_PAINT]: pigment mixing (wet paint).
 */
@Serializable
enum class RenderBackend {
    CONTINUOUS_RIBBON,
    PROCEDURAL_STAMP,
    ASSET_STAMP,
    AIRBRUSH,
    WET_PAINT,
}

/**
 * User-visible capabilities of a brush family. The common schema always provides size/opacity/
 * flow + basic pressure dynamics; everything capability-specific is gated here so a Pencil preset
 * has no Ink-only fields and vice versa.
 */
@Serializable
enum class BrushCapability {
    /** Stamps are placed at a configurable spacing along the path (stamped engines). */
    STAMP_SPACING,

    /** Continuous ribbon emission with taper (ink/pen engines). */
    CONTINUOUS_RIBBON,
    TAPER,

    /** Pressure drives the deposited amount (darkness), separate from tip size. */
    PRESSURE_DEPOSITION,

    /** Pressure also scales the footprint width. */
    PRESSURE_WIDTH,

    /** Tilt widens the footprint perpendicular to the stroke (width/aspect). */
    TILT_WIDTH,

    /** Tilt boosts the document-space paper grain density. */
    TILT_GRAIN,

    /** Document-anchored paper grain, distinct from the brush-tip texture. */
    PAPER_GRAIN,

    /** A real brush-tip texture (graphite tooth, imported mask, ...). */
    TIP_TEXTURE,

    /** Direction (stroke azimuth) response. */
    DIRECTION,
}
