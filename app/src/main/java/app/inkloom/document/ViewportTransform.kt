package app.inkloom.document

data class ViewportState(val scale: Float, val panX: Float, val panY: Float, val rotationDegrees: Float = 0f)

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
    ): ViewportState {
        val (screenDeltaX, screenDeltaY) = rotate(localDeltaX * current.scale, localDeltaY * current.scale, current.rotationDegrees)
        return clamp(
            current.copy(
                panX = (current.panX + screenDeltaX).coerceIn(-maximumPan, maximumPan),
                panY = (current.panY + screenDeltaY).coerceIn(-maximumPan, maximumPan),
            ),
            geometry,
        )
    }

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
        if (nextScale <= minimumScale + .0001f) return current.copy(scale = minimumScale, panX = 0f, panY = 0f)
        val (screenFocusX, screenFocusY) = rotate(focusOffsetX, focusOffsetY, current.rotationDegrees)
        return clamp(
            ViewportState(
                scale = nextScale,
                panX = (current.panX + (current.scale - nextScale) * screenFocusX).coerceIn(-maximumPan, maximumPan),
                panY = (current.panY + (current.scale - nextScale) * screenFocusY).coerceIn(-maximumPan, maximumPan),
                rotationDegrees = current.rotationDegrees,
            ),
            geometry,
        )
    }

    fun rotateBy(current: ViewportState, deltaDegrees: Float, geometry: ViewportGeometry? = null): ViewportState =
        clamp(current.copy(rotationDegrees = normalizeDegrees(current.rotationDegrees + deltaDegrees)), geometry)

    fun actualSize(
        current: ViewportState,
        pageModelWidth: Float,
        pageViewWidth: Float,
        geometry: ViewportGeometry? = null,
        maximumScale: Float = 8f,
    ): ViewportState {
        val scale = (pageModelWidth / pageViewWidth.coerceAtLeast(1f)).coerceIn(1f, maximumScale)
        return clamp(current.copy(scale = scale, panX = 0f, panY = 0f), geometry)
    }

    fun fitWidth(
        current: ViewportState,
        viewportWidth: Float,
        pageViewWidth: Float,
        geometry: ViewportGeometry? = null,
        horizontalFill: Float = .94f,
        maximumScale: Float = 8f,
    ): ViewportState {
        val scale = (viewportWidth.coerceAtLeast(1f) * horizontalFill.coerceIn(.5f, 1f) / pageViewWidth.coerceAtLeast(1f)).coerceIn(1f, maximumScale)
        return clamp(current.copy(scale = scale, panX = 0f, panY = 0f), geometry)
    }

    fun resetRotation(current: ViewportState, geometry: ViewportGeometry? = null): ViewportState =
        clamp(current.copy(rotationDegrees = 0f), geometry)

    fun clamp(current: ViewportState, geometry: ViewportGeometry?): ViewportState {
        if (current.scale <= 1.0001f) return current.copy(panX = 0f, panY = 0f)
        geometry ?: return current
        val unrotatedWidth = (geometry.pageWidth * current.scale).coerceAtLeast(1f)
        val unrotatedHeight = (geometry.pageHeight * current.scale).coerceAtLeast(1f)
        val radians = Math.toRadians(current.rotationDegrees.toDouble())
        val cosine = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
        val sine = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
        val pageWidth = unrotatedWidth * cosine + unrotatedHeight * sine
        val pageHeight = unrotatedWidth * sine + unrotatedHeight * cosine
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

    fun normalizeDegrees(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

    private fun rotate(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        val cosine = kotlin.math.cos(radians).toFloat()
        val sine = kotlin.math.sin(radians).toFloat()
        return x * cosine - y * sine to x * sine + y * cosine
    }
}
