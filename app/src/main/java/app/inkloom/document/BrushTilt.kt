package app.inkloom.document

/** Shared Apple Pencil/Android tilt conversion. Android reports 0 upright and π/2 flat. */
object BrushTilt {
    fun response(point: StrokePoint, start: Float): Float {
        val normalized = (point.tilt / HALF_PI).coerceIn(0f, 1f)
        val trigger = start.coerceIn(0f, .99f)
        return ((normalized - trigger) / (1f - trigger)).coerceIn(0f, 1f)
    }

    private const val HALF_PI = (Math.PI / 2.0).toFloat()
}
