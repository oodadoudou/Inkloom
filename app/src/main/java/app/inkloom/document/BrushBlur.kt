package app.inkloom.document

/** Converts CSP BrushBlur metadata into a bounded render-space blur radius. */
object BrushBlur {
    fun radiusPixels(
        value: Float,
        unit: Int,
        linkedToSize: Boolean,
        currentDiameter: Float,
        baseDiameter: Float,
        pageWidth: Float,
    ): Float {
        if (!value.isFinite() || value <= 0f || !pageWidth.isFinite() || pageWidth <= 0f) return 0f
        val referencePixels = if (unit == MILLIMETRE_UNIT) value * PIXELS_PER_MILLIMETRE else value
        val sizeScale = if (
            linkedToSize && currentDiameter.isFinite() && baseDiameter.isFinite() && baseDiameter > MIN_DIAMETER
        ) {
            (currentDiameter / baseDiameter).coerceIn(0f, MAX_SIZE_SCALE)
        } else 1f
        return (referencePixels.coerceIn(0f, MAX_REFERENCE_BLUR) * pageWidth / REFERENCE_PAGE_WIDTH * sizeScale)
            .coerceIn(0f, pageWidth / MAX_PAGE_RADIUS_DIVISOR)
    }

    private const val MILLIMETRE_UNIT = 2
    private const val PIXELS_PER_MILLIMETRE = 3.7795275f
    private const val REFERENCE_PAGE_WIDTH = 1600f
    private const val MAX_REFERENCE_BLUR = 400f
    private const val MAX_SIZE_SCALE = 8f
    private const val MIN_DIAMETER = .001f
    private const val MAX_PAGE_RADIUS_DIVISOR = 4f
}
