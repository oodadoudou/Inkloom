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
    /** Maps a physical screen point back into the untransformed page View. This
     * is the inverse of the centered graphicsLayer transform used by the editor. */
    fun screenToLocal(
        screenX: Float,
        screenY: Float,
        viewportCenterScreenX: Float,
        viewportCenterScreenY: Float,
        localWidth: Float,
        localHeight: Float,
        viewport: ViewportState,
    ): Pair<Float, Float> {
        val safe = sanitize(viewport)
        val dx = (screenX.takeIf(Float::isFinite) ?: viewportCenterScreenX) - viewportCenterScreenX - safe.panX
        val dy = (screenY.takeIf(Float::isFinite) ?: viewportCenterScreenY) - viewportCenterScreenY - safe.panY
        val radians = Math.toRadians((-safe.rotationDegrees).toDouble())
        val cosine = kotlin.math.cos(radians).toFloat()
        val sine = kotlin.math.sin(radians).toFloat()
        val unrotatedX = dx * cosine - dy * sine
        val unrotatedY = dx * sine + dy * cosine
        return localWidth.coerceAtLeast(1f) / 2f + unrotatedX / safe.scale to
            localHeight.coerceAtLeast(1f) / 2f + unrotatedY / safe.scale
    }

    /** Applies a screen-space translation. Gesture coordinates must use this path
     * when the page View itself is scaled, otherwise local deltas change coordinate
     * systems between MotionEvents and the page appears to drift. */
    fun panByScreen(
        current: ViewportState,
        screenDeltaX: Float,
        screenDeltaY: Float,
        maximumPan: Float = 2_500f,
        geometry: ViewportGeometry? = null,
    ): ViewportState {
        val safe = sanitize(current)
        val deltaX = screenDeltaX.takeIf(Float::isFinite) ?: 0f
        val deltaY = screenDeltaY.takeIf(Float::isFinite) ?: 0f
        val maximumOffset = maximumPan.takeIf { it.isFinite() && it >= 0f } ?: 2_500f
        return clamp(
            safe.copy(
                panX = (safe.panX + deltaX).coerceIn(-maximumOffset, maximumOffset),
                panY = (safe.panY + deltaY).coerceIn(-maximumOffset, maximumOffset),
            ),
            geometry,
        )
    }

    /** Converts movement reported in the page's local coordinates into screen-space panning. */
    fun panBy(
        current: ViewportState,
        localDeltaX: Float,
        localDeltaY: Float,
        maximumPan: Float = 2_500f,
        geometry: ViewportGeometry? = null,
    ): ViewportState {
        val safe = sanitize(current)
        val deltaX = localDeltaX.takeIf(Float::isFinite) ?: 0f
        val deltaY = localDeltaY.takeIf(Float::isFinite) ?: 0f
        val maximumOffset = maximumPan.takeIf { it.isFinite() && it >= 0f } ?: 2_500f
        val (screenDeltaX, screenDeltaY) = rotate(deltaX * safe.scale, deltaY * safe.scale, safe.rotationDegrees)
        return clamp(
            safe.copy(
                panX = (safe.panX + screenDeltaX).coerceIn(-maximumOffset, maximumOffset),
                panY = (safe.panY + screenDeltaY).coerceIn(-maximumOffset, maximumOffset),
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
        val minimum = minimumScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val maximum = maximumScale.takeIf { it.isFinite() && it >= minimum } ?: maxOf(minimum, 8f)
        val safe = sanitize(current, minimum, maximum)
        val safeFactor = factor.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nextScale = (safe.scale * safeFactor).coerceIn(minimum, maximum)
        val focusX = focusOffsetX.takeIf(Float::isFinite) ?: 0f
        val focusY = focusOffsetY.takeIf(Float::isFinite) ?: 0f
        val (screenFocusX, screenFocusY) = rotate(focusX, focusY, safe.rotationDegrees)
        val maximumOffset = maximumPan.takeIf { it.isFinite() && it >= 0f } ?: 2_500f
        val next = ViewportState(
            scale = nextScale,
            panX = (safe.panX + (safe.scale - nextScale) * screenFocusX).coerceIn(-maximumOffset, maximumOffset),
            panY = (safe.panY + (safe.scale - nextScale) * screenFocusY).coerceIn(-maximumOffset, maximumOffset),
            rotationDegrees = safe.rotationDegrees,
        )
        if (nextScale <= minimum + .0001f && kotlin.math.abs(safe.rotationDegrees) <= .0001f) return next.copy(panX = 0f, panY = 0f)
        return clamp(
            next,
            geometry,
        )
    }

    /** Screen-coordinate counterpart of [zoomAround]. The focus is relative to
     * the stable viewport center, not the already transformed page View. */
    fun zoomAroundScreen(
        current: ViewportState,
        factor: Float,
        focusOffsetX: Float,
        focusOffsetY: Float,
        minimumScale: Float = 1f,
        maximumScale: Float = 8f,
        maximumPan: Float = 2_500f,
        geometry: ViewportGeometry? = null,
    ): ViewportState {
        val minimum = minimumScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val maximum = maximumScale.takeIf { it.isFinite() && it >= minimum } ?: maxOf(minimum, 8f)
        val safe = sanitize(current, minimum, maximum)
        val safeFactor = factor.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nextScale = (safe.scale * safeFactor).coerceIn(minimum, maximum)
        val ratio = nextScale / safe.scale.coerceAtLeast(.0001f)
        val focusX = focusOffsetX.takeIf(Float::isFinite) ?: 0f
        val focusY = focusOffsetY.takeIf(Float::isFinite) ?: 0f
        val maximumOffset = maximumPan.takeIf { it.isFinite() && it >= 0f } ?: 2_500f
        val next = safe.copy(
            scale = nextScale,
            panX = (focusX - (focusX - safe.panX) * ratio).coerceIn(-maximumOffset, maximumOffset),
            panY = (focusY - (focusY - safe.panY) * ratio).coerceIn(-maximumOffset, maximumOffset),
        )
        if (nextScale <= minimum + .0001f && kotlin.math.abs(safe.rotationDegrees) <= .0001f) return next.copy(panX = 0f, panY = 0f)
        return clamp(next, geometry)
    }

    fun rotateBy(current: ViewportState, deltaDegrees: Float, geometry: ViewportGeometry? = null): ViewportState =
        sanitize(current).let { safe -> clamp(safe.copy(rotationDegrees = normalizeDegrees(safe.rotationDegrees + (deltaDegrees.takeIf(Float::isFinite) ?: 0f))), geometry) }

    fun actualSize(
        current: ViewportState,
        pageModelWidth: Float,
        pageViewWidth: Float,
        geometry: ViewportGeometry? = null,
        maximumScale: Float = 8f,
    ): ViewportState {
        val modelWidth = pageModelWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val viewWidth = pageViewWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val maximum = maximumScale.takeIf { it.isFinite() && it >= 1f } ?: 8f
        val scale = (modelWidth / viewWidth).coerceIn(1f, maximum)
        return clamp(sanitize(current, maximumScale = maximum).copy(scale = scale, panX = 0f, panY = 0f), geometry)
    }

    fun fitWidth(
        current: ViewportState,
        viewportWidth: Float,
        pageViewWidth: Float,
        geometry: ViewportGeometry? = null,
        horizontalFill: Float = .94f,
        maximumScale: Float = 8f,
    ): ViewportState {
        val viewport = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val page = pageViewWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val fill = horizontalFill.takeIf(Float::isFinite)?.coerceIn(.5f, 1f) ?: .94f
        val maximum = maximumScale.takeIf { it.isFinite() && it >= 1f } ?: 8f
        val scale = (viewport * fill / page).coerceIn(1f, maximum)
        return clamp(sanitize(current, maximumScale = maximum).copy(scale = scale, panX = 0f, panY = 0f), geometry)
    }

    fun resetRotation(current: ViewportState, geometry: ViewportGeometry? = null): ViewportState =
        clamp(sanitize(current).copy(rotationDegrees = 0f), geometry)

    fun clamp(current: ViewportState, geometry: ViewportGeometry?): ViewportState {
        val safe = sanitize(current)
        if (safe.scale <= 1.0001f && kotlin.math.abs(safe.rotationDegrees) <= .0001f) return safe.copy(panX = 0f, panY = 0f)
        val validGeometry = geometry?.takeIf { it.viewportWidth.isFinite() && it.viewportHeight.isFinite() && it.pageWidth.isFinite() && it.pageHeight.isFinite() && it.viewportWidth > 0f && it.viewportHeight > 0f && it.pageWidth > 0f && it.pageHeight > 0f } ?: return safe
        val unrotatedWidth = (validGeometry.pageWidth * safe.scale).coerceAtLeast(1f)
        val unrotatedHeight = (validGeometry.pageHeight * safe.scale).coerceAtLeast(1f)
        val radians = Math.toRadians(safe.rotationDegrees.toDouble())
        val cosine = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
        val sine = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
        val pageWidth = unrotatedWidth * cosine + unrotatedHeight * sine
        val pageHeight = unrotatedWidth * sine + unrotatedHeight * cosine
        val visibleFraction = validGeometry.minimumVisibleFraction.takeIf(Float::isFinite)?.coerceIn(.05f, .5f) ?: .15f
        val visibleX = minOf(pageWidth * visibleFraction, validGeometry.viewportWidth * .5f).coerceAtLeast(48f)
        val visibleY = minOf(pageHeight * visibleFraction, validGeometry.viewportHeight * .5f).coerceAtLeast(48f)
        val maximumX = (validGeometry.viewportWidth * .5f + pageWidth * .5f - visibleX).coerceAtLeast(0f)
        val maximumY = (validGeometry.viewportHeight * .5f + pageHeight * .5f - visibleY).coerceAtLeast(0f)
        return safe.copy(
            panX = safe.panX.coerceIn(-maximumX, maximumX),
            panY = safe.panY.coerceIn(-maximumY, maximumY),
        )
    }

    /** Pointer index that remains after ACTION_POINTER_UP, used to continue a
     * one-finger pan without jumping back to the pinch gesture's old anchor. */
    fun remainingPointerIndex(pointerCount: Int, liftedPointerIndex: Int): Int? {
        if (pointerCount < 2 || liftedPointerIndex !in 0 until pointerCount) return null
        return if (liftedPointerIndex == 0) 1 else 0
    }

    fun normalizeDegrees(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

    fun sanitize(current: ViewportState, minimumScale: Float = 1f, maximumScale: Float = 8f): ViewportState {
        val minimum = minimumScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val maximum = maximumScale.takeIf { it.isFinite() && it >= minimum } ?: maxOf(minimum, 8f)
        val scale = current.scale.takeIf(Float::isFinite)?.coerceIn(minimum, maximum) ?: minimum
        val panX = current.panX.takeIf(Float::isFinite) ?: 0f
        val panY = current.panY.takeIf(Float::isFinite) ?: 0f
        val rotation = current.rotationDegrees.takeIf(Float::isFinite)?.let(::normalizeDegrees) ?: 0f
        return ViewportState(scale, panX, panY, rotation)
    }

    private fun rotate(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        val cosine = kotlin.math.cos(radians).toFloat()
        val sine = kotlin.math.sin(radians).toFloat()
        return x * cosine - y * sine to x * sine + y * cosine
    }
}
