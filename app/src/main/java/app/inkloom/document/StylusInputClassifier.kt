package app.inkloom.document

object StylusInputClassifier {
    const val RECENT_STYLUS_WINDOW_MS = 700L
    const val PALM_TOUCH_MAJOR_PX = 48f

    fun shouldNavigateOrRejectFinger(
        isStylus: Boolean,
        fingerDrawingEnabled: Boolean,
        pointerCount: Int,
        touchMajorPixels: Float,
        millisecondsSinceStylus: Long,
    ): Boolean = !isStylus && (
        !fingerDrawingEnabled ||
            pointerCount > 1 ||
            touchMajorPixels >= PALM_TOUCH_MAJOR_PX ||
            millisecondsSinceStylus in 0..RECENT_STYLUS_WINDOW_MS
        )
}
