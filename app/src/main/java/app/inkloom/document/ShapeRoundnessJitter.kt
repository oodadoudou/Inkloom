package app.inkloom.document

data class ShapeScaleFactors(val horizontal: Float, val vertical: Float)

/** Deterministic Procreate per-stamp horizontal/vertical roundness compression. */
object ShapeRoundnessJitter {
    fun factors(seed: Int, stampIndex: Int, horizontal: Float, vertical: Float, dual: Boolean = false): ShapeScaleFactors {
        val horizontalAmount = horizontal.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val verticalAmount = vertical.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return ShapeScaleFactors(
            horizontal = 1f - horizontalAmount * BrushDynamics.deterministicRandom(seed, stampIndex.toLong(), if (dual) DUAL_HORIZONTAL_SALT else HORIZONTAL_SALT),
            vertical = 1f - verticalAmount * BrushDynamics.deterministicRandom(seed, stampIndex.toLong(), if (dual) DUAL_VERTICAL_SALT else VERTICAL_SALT),
        )
    }

    private const val HORIZONTAL_SALT = 0x52584A31
    private const val VERTICAL_SALT = 0x52594A31
    private const val DUAL_HORIZONTAL_SALT = 0x52584432
    private const val DUAL_VERTICAL_SALT = 0x52594432
}
