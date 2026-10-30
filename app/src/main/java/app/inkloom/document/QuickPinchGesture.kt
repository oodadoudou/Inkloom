package app.inkloom.document

/**
 * Procreate's quick pinch: a fast pinch in, lifted at the end, snaps the canvas to fit the screen.
 * Slow pinches and pinches that also turn the canvas stay ordinary zooming.
 */
class QuickPinchGesture(
    private val maximumDurationMs: Long = 260L,
    private val maximumSpanRatio: Float = .6f,
    private val maximumRotationDegrees: Float = 20f,
) {
    private var startedAt = -1L
    private var startSpan = 0f

    fun start(timeMs: Long, span: Float) {
        startedAt = timeMs
        startSpan = span
    }

    /** [rotationDegrees] is how far the fingers turned during the pinch, in either direction. */
    fun finish(timeMs: Long, span: Float, rotationDegrees: Float): Boolean {
        val completed = startedAt >= 0L && startSpan > 0f && timeMs - startedAt <= maximumDurationMs &&
            span / startSpan <= maximumSpanRatio && rotationDegrees <= maximumRotationDegrees
        cancel()
        return completed
    }

    fun cancel() {
        startedAt = -1L
        startSpan = 0f
    }
}
