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

    /** Pigment transport accepts one deterministic tip mask, fixed shape and width pressure.
     * Reject dynamics that the blend renderer cannot reproduce instead of substituting a circle. */
    fun forSmudge(encoded: String?): Result<BrushPresetV5> = runCatching {
        val paint = requireNotNull(BrushPresetV5Codec.decodeOrNull(encoded)) { "Brush has no supported V5 tip" }
        require(paint.output == BrushOutput.PAINT) { "Choose a paint brush to borrow its tip" }
        val program = CompiledBrushProgramV5.compile(paint)
        require(BrushExecutionCapabilityResolver.resolve(program) == BrushExecutionCapabilityResolution.Supported) {
            "The paint brush is not executable"
        }
        val texture = program.tip.tipTexture
        val stamp = paint.stamp
        require((texture?.assetPaths?.size ?: 0) <= 1 &&
            paint.tip.aspect > 0f && paint.tip.aspect <= 1f && paint.tip.rotationDegrees.isFinite() &&
            paint.paperGrain == null && paint.textureGrain == null && paint.dual == null &&
            paint.wetMix == null && paint.deposition.buildup == 0f &&
            paint.stroke?.taperStartEnabled != true && paint.stroke?.taperEndEnabled != true &&
            (paint.stroke?.postCorrection ?: 0f) == 0f &&
            (stamp == null || (!stamp.followStrokeDirection && !stamp.followStylusOrientation &&
                stamp.randomRotationDegrees == 0f && stamp.particlesPerPlot == 1 &&
                !stamp.continuousPlot && stamp.spray == null && stamp.transferMode == DabTransferMode.UNIFORM_GLAZE &&
                stamp.lateralPathJitter == 0f && stamp.longitudinalPathJitter == 0f &&
                stamp.horizontalRoundnessJitter == 0f && stamp.verticalRoundnessJitter == 0f &&
                stamp.directionRotationFactor == 0f && stamp.strokeRotationJitterDegrees == 0f &&
                stamp.particlesPerPlotJitter == 0f)) &&
            !program.pressure.depositionEnabled && !program.pressure.flowEnabled &&
            !program.tilt.widthEnabled && !program.tilt.grainEnabled &&
            paint.dynamics.velocity == null && paint.dynamics.direction == null
        ) { "Blend supports one fixed tip mask and width pressure; this brush has unsupported dynamics or grain" }
        paint
    }
}
