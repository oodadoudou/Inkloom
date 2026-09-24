package app.inkloom.brushpresetv5

/** Reuses an authored paint tip without modifying or duplicating the saved brush. */
object BrushToolTipReuse {
    fun forEraser(encoded: String?): Result<BrushPresetV5> = runCatching {
        val paint = requireNotNull(BrushPresetV5Codec.decodeOrNull(encoded)) { "Brush has no supported V5 tip" }
        require(paint.output == BrushOutput.PAINT) { "Choose a paint brush to borrow its tip" }
        val eraser = paint.copy(output = BrushOutput.ERASE)
        when (val capability = BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(eraser))) {
            BrushExecutionCapabilityResolution.Supported -> eraser
            is BrushExecutionCapabilityResolution.Unsupported ->
                error("This tip cannot erase: ${capability.reasons.joinToString()}")
        }
    }

    /** The current pigment transport accepts a round, non-textured tip and width pressure.
     * Reject richer presets rather than silently replacing their authored shape or dynamics. */
    fun forSmudge(encoded: String?): Result<BrushPresetV5> = runCatching {
        val paint = requireNotNull(BrushPresetV5Codec.decodeOrNull(encoded)) { "Brush has no supported V5 tip" }
        require(paint.output == BrushOutput.PAINT) { "Choose a paint brush to borrow its tip" }
        val program = CompiledBrushProgramV5.compile(paint)
        require(BrushExecutionCapabilityResolver.resolve(program) == BrushExecutionCapabilityResolution.Supported) {
            "The paint brush is not executable"
        }
        require(program.tip.tipTexture?.assetPaths.isNullOrEmpty() &&
            program.tip.tipTexture?.proceduralKind in setOf(null, ProceduralTipKind.ROUND) &&
            paint.tip.aspect == 1f && paint.tip.rotationDegrees == 0f &&
            paint.paperGrain == null && paint.textureGrain == null && paint.dual == null &&
            paint.wetMix == null && paint.stamp == null &&
            !program.pressure.depositionEnabled && !program.pressure.flowEnabled &&
            !program.tilt.widthEnabled && !program.tilt.grainEnabled &&
            paint.dynamics.velocity == null && paint.dynamics.direction == null
        ) { "This smudge engine supports round untextured tips with width pressure only" }
        paint
    }
}
