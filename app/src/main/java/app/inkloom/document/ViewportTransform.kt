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
        minimumScale: Float = 1f,
        maximumScale: Float = 4f,
        maximumPan: Float = 2_500f,
    ): ViewportState {
        val nextScale = (current.scale * factor).coerceIn(minimumScale, maximumScale)
        if (nextScale <= minimumScale + .0001f) return ViewportState(minimumScale, 0f, 0f)
        return ViewportState(
            scale = nextScale,
            panX = (current.panX + (current.scale - nextScale) * focusOffsetX).coerceIn(-maximumPan, maximumPan),
            panY = (current.panY + (current.scale - nextScale) * focusOffsetY).coerceIn(-maximumPan, maximumPan),
        )
    }

    /** Pointer index that remains after ACTION_POINTER_UP, used to continue a
     * one-finger pan without jumping back to the pinch gesture's old anchor. */
    fun remainingPointerIndex(pointerCount: Int, liftedPointerIndex: Int): Int? {
        if (pointerCount < 2 || liftedPointerIndex !in 0 until pointerCount) return null
        return if (liftedPointerIndex == 0) 1 else 0
    }
}
