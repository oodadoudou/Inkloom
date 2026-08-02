package app.inkloom.importer

object SutUnitConverter {
    fun referencePixels(value: Float, unit: Int): Float =
        (if (unit == MILLIMETERS) value * PIXELS_PER_MILLIMETER else value).coerceIn(.5f, 400f)

    const val MILLIMETERS = 2
    private const val PIXELS_PER_MILLIMETER = 3.7795275f
}
