package app.inkloom.brushpresetv4

import kotlinx.serialization.Serializable

/**
 * BrushPresetV4 engine families. A family declares which high-level capabilities are meaningful,
 * so a preset never carries a bag of permanently-inactive renderer-specific fields. The schema
 * describes user-configurable brush semantics only; renderer internals (ribbon vertices, tile
 * surfaces, footprint shaders, ...) never appear here.
 */
@Serializable
enum class BrushEngineFamily(val label: String) {
    CONTINUOUS_INK("Ink"),
    STAMPED_PENCIL("Pencil"),
    AIRBRUSH("Airbrush"),
    DECORATION("Decoration"),
    /** Reserved for a future wet-paint engine; no preset is built on it yet. */
    WET_PAINT("Wet Paint"),
}

/**
 * User-visible capabilities of an engine family. The common schema always provides size/opacity/
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
