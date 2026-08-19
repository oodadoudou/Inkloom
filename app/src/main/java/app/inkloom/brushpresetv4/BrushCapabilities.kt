package app.inkloom.brushpresetv4

/**
 * Declares which [BrushCapability] values a brush [BrushFamily] supports, and maps a family + tip
 * combination to its [RenderBackend]. The common schema always provides size/opacity/flow + basic
 * pressure dynamics; capability-specific blocks are present only for families that declare them,
 * so no preset carries permanently-inactive fields.
 */
object BrushCapabilities {

    fun of(family: BrushFamily): Set<BrushCapability> = when (family) {
        BrushFamily.INK -> setOf(
            BrushCapability.CONTINUOUS_RIBBON,
            BrushCapability.TAPER,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
            BrushCapability.DIRECTION,
        )

        BrushFamily.PENCIL -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
            BrushCapability.TILT_WIDTH,
            BrushCapability.TILT_GRAIN,
            BrushCapability.PAPER_GRAIN,
            BrushCapability.TIP_TEXTURE,
        )

        BrushFamily.AIRBRUSH -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.PRESSURE_WIDTH,
        )

        BrushFamily.DECORATION -> setOf(
            BrushCapability.STAMP_SPACING,
            BrushCapability.TIP_TEXTURE,
        )

        BrushFamily.WET_PAINT -> setOf(
            BrushCapability.PRESSURE_DEPOSITION,
            BrushCapability.DIRECTION,
        )
    }

    /**
     * Resolves the execution [RenderBackend] for a family + tip. A PENCIL with a procedural tip
     * stamps procedurally; the same family with an imported tip asset stamps the asset.
     */
    fun backend(family: BrushFamily, hasImportedTip: Boolean): RenderBackend = when (family) {
        BrushFamily.INK -> RenderBackend.CONTINUOUS_RIBBON
        BrushFamily.PENCIL -> if (hasImportedTip) RenderBackend.ASSET_STAMP else RenderBackend.PROCEDURAL_STAMP
        BrushFamily.AIRBRUSH -> RenderBackend.AIRBRUSH
        BrushFamily.DECORATION -> RenderBackend.ASSET_STAMP
        BrushFamily.WET_PAINT -> RenderBackend.WET_PAINT
    }

    /** True when [capability] is meaningful for the family — used by UI to show panels, and by the
     * compiler to decide whether a block is allowed to be non-null. */
    fun supports(family: BrushFamily, capability: BrushCapability): Boolean =
        capability in of(family)
}
