package app.inkloom.document

object WaterEdge {
    fun distancePixels(value: Float, unit: Int, pageWidth: Float): Float {
        val referencePixels = if (unit == 2) value * PX_PER_MM else value
        return referencePixels.coerceAtLeast(0f) * pageWidth.coerceAtLeast(1f) / REFERENCE_PAGE_WIDTH
    }

    fun darkenArgb(color: Int, darkness: Float): Int {
        val factor = 1f - darkness.coerceIn(0f, 1f)
        val alpha = color ushr 24 and 0xFF
        val red = ((color ushr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val green = ((color ushr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val blue = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    private const val PX_PER_MM = 3.7795275f
    private const val REFERENCE_PAGE_WIDTH = 1600f
}
