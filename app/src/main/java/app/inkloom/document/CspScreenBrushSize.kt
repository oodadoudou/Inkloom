package app.inkloom.document

/** CSP Brush size > Specify by size on screen.
 *
 * The editor canvas is transformed as a whole. Dividing the authored diameter by the scale before
 * a gesture is materialized keeps its physical screen footprint equal to the 100% view footprint.
 */
object CspScreenBrushSize {
    fun documentSize(authoredSize: Float, viewportScale: Float, enabled: Boolean): Float {
        val size = authoredSize.takeIf(Float::isFinite)?.coerceAtLeast(.01f) ?: .01f
        if (!enabled) return size
        val scale = viewportScale.takeIf(Float::isFinite)?.coerceIn(ViewportTransform.MINIMUM_SCALE, ViewportTransform.MAXIMUM_SCALE) ?: 1f
        return size / scale
    }

    fun physicalSize(authoredSize: Float, viewportScale: Float, enabled: Boolean): Float =
        documentSize(authoredSize, viewportScale, enabled) *
            (viewportScale.takeIf(Float::isFinite)?.coerceIn(ViewportTransform.MINIMUM_SCALE, ViewportTransform.MAXIMUM_SCALE) ?: 1f)
}
