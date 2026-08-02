package app.inkloom.document

data class ViewportState(val scale: Float, val panX: Float, val panY: Float)

object ViewportTransform {
    /** Converts movement reported in the page's local coordinates into screen-space panning. */
    fun panBy(
        current: ViewportState,
        localDeltaX: Float,
        localDeltaY: Float,
        maximumPan: Float = 2_500f,
    ): ViewportState = current.copy(
        panX = (current.panX + localDeltaX * current.scale).coerceIn(-maximumPan, maximumPan),
        panY = (current.panY + localDeltaY * current.scale).coerceIn(-maximumPan, maximumPan),
    )

    /** Keeps the page point under the gesture focus stationary while changing scale. */
    fun zoomAround(
        current: ViewportState,
        factor: Float,
        focusOffsetX: Float,
        focusOffsetY: Float,
        minimumScale: Float = .5f,
        maximumScale: Float = 4f,
        maximumPan: Float = 2_500f,
    ): ViewportState {
        val nextScale = (current.scale * factor).coerceIn(minimumScale, maximumScale)
        return ViewportState(
            scale = nextScale,
            panX = (current.panX + (current.scale - nextScale) * focusOffsetX).coerceIn(-maximumPan, maximumPan),
            panY = (current.panY + (current.scale - nextScale) * focusOffsetY).coerceIn(-maximumPan, maximumPan),
        )
    }
}
