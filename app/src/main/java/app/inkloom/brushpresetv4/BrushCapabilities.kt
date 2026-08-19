package app.inkloom.brushpresetv4

/**
 * Declares which [BrushCapability] values an engine family supports. The common schema always
 * provides size/opacity/flow + basic pressure dynamics; capability-specific blocks are present only
 * for families that declare them, so no preset carries permanently-inactive fields.
 */
object BrushCapabilities {

    fun of(family: BrushEngineFamily): Set<BrushCapability> = when (family) {
        BrushEngineFamily.CONTINUOUS_INK -> setOf(
            BrushCapability.CONTINUOUS_RIBBON,
            BrushCapability.TAPER,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
            BrushCapability.DIRECTION,
        )

        BrushEngineFamily.STAMPED_PENCIL -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
            BrushCapability.TILT_WIDTH,
            BrushCapability.TILT_GRAIN,
            BrushCapability.PAPER_GRAIN,
            BrushCapability.TIP_TEXTURE,
        )

        BrushEngineFamily.AIRBRUSH -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
        )

        BrushEngineFamily.DECORATION -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.TIP_TEXTURE,
        )

        BrushEngineFamily.WET_PAINT -> setOf(
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.DIRECTION,
        )
    }

    /** True when [capability] is meaningful for the family — used by UI to show panels, and by the
     * compiler to decide whether a block is allowed to be non-null. */
    fun supports(family: BrushEngineFamily, capability: BrushCapability): Boolean =
        capability in of(family)
}
