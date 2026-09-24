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
}
