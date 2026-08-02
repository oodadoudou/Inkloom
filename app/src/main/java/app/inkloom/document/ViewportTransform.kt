package app.inkloom.document

data class ViewportState(val scale: Float, val panX: Float, val panY: Float)

data class ViewportGeometry(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val minimumVisibleFraction: Float = .15f,
)

object ViewportTransform {
    /** Converts movement reported in the page's local coordinates into screen-space panning. */
    fun panBy(
        current: ViewportState,
        localDeltaX: Float,
        localDeltaY: Float,
        maximumPan: Float = 2_500f,
        geometry: ViewportGeometry? = null,
    ): ViewportState = clamp(
        current.copy(
            panX = (current.panX + localDeltaX * current.scale).coerceIn(-maximumPan, maximumPan),
            panY = (current.panY + localDeltaY * current.scale).coerceIn(-maximumPan, maximumPan),
        ),
        geometry,
    )

    /** Keeps the page point under the gesture focus stationary while changing scale. */
    fun zoomAround(
        current: ViewportState,
        factor: Float,
        focusOffsetX: Float,
        focusOffsetY: Float,
        minimumScale: Float = 1f,
        maximumScale: Float = 8f,
        maximumPan: Float = 2_500f,
        geometry: ViewportGeometry? = null,
    ): ViewportState {
        val nextScale = (current.scale * factor).coerceIn(minimumScale, maximumScale)
        if (nextScale <= minimumScale + .0001f) return ViewportState(minimumScale, 0f, 0f)
        return clamp(
            ViewportState(
                scale = nextScale,
                panX = (current.panX + (current.scale - nextScale) * focusOffsetX).coerceIn(-maximumPan, maximumPan),
                panY = (current.panY + (current.scale - nextScale) * focusOffsetY).coerceIn(-maximumPan, maximumPan),
            ),
            geometry,
        )
    }

    fun clamp(current: ViewportState, geometry: ViewportGeometry?): ViewportState {
        if (current.scale <= 1.0001f) return current.copy(panX = 0f, panY = 0f)
        geometry ?: return current
        val pageWidth = (geometry.pageWidth * current.scale).coerceAtLeast(1f)
        val pageHeight = (geometry.pageHeight * current.scale).coerceAtLeast(1f)
        val visibleX = minOf(pageWidth * geometry.minimumVisibleFraction.coerceIn(.05f, .5f), geometry.viewportWidth * .5f).coerceAtLeast(48f)
        val visibleY = minOf(pageHeight * geometry.minimumVisibleFraction.coerceIn(.05f, .5f), geometry.viewportHeight * .5f).coerceAtLeast(48f)
        val maximumX = (geometry.viewportWidth * .5f + pageWidth * .5f - visibleX).coerceAtLeast(0f)
        val maximumY = (geometry.viewportHeight * .5f + pageHeight * .5f - visibleY).coerceAtLeast(0f)
        return current.copy(
            panX = current.panX.coerceIn(-maximumX, maximumX),
            panY = current.panY.coerceIn(-maximumY, maximumY),
        )
    }

    /** Pointer index that remains after ACTION_POINTER_UP, used to continue a
     * one-finger pan without jumping back to the pinch gesture's old anchor. */
    fun remainingPointerIndex(pointerCount: Int, liftedPointerIndex: Int): Int? {
        if (pointerCount < 2 || liftedPointerIndex !in 0 until pointerCount) return null
        return if (liftedPointerIndex == 0) 1 else 0
    }
}
