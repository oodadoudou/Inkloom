package app.inkloom.engine.v2.input

import app.inkloom.document.StrokeStabilization
import kotlin.math.hypot

/**
 * Stateful, allocation-free modeler for one contact stream.
 *
 * The renderer owns document point creation during the migration, but filtering state no longer
 * depends on that document list. This is the single input model used by the canvas.
 */
internal class StrokeInputModeler {
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var pressure: Float = 1f
        private set
    var timeMs: Long = 0L
        private set
    var tilt: Float = 0f
        private set
    var orientation: Float = 0f
        private set
    var velocity: Float = 0f
        private set

    private var hasAcceptedSample = false

    fun reset() {
        hasAcceptedSample = false
        x = 0f
        y = 0f
        pressure = 1f
        timeMs = 0L
        tilt = 0f
        orientation = 0f
        velocity = 0f
    }

    fun append(
        rawX: Float,
        rawY: Float,
        adjustedPressure: Float,
        sampleTimeMs: Long,
        sampleTilt: Float,
        sampleOrientation: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        stabilization: Float,
        stabilizationBySpeed: Boolean,
        stabilizationBySpeedMode: Int,
        force: Boolean = false,
    ): Boolean {
        val normalizedX = rawX.coerceIn(0f, 1f)
        val normalizedY = rawY.coerceIn(0f, 1f)
        val elapsed = if (hasAcceptedSample) (sampleTimeMs - timeMs).coerceAtLeast(1L) else 1L
        val inputVelocity = if (hasAcceptedSample) {
            normalizedVelocity(normalizedX - x, normalizedY - y, viewportWidthPx, viewportHeightPx, elapsed)
        } else 0f
        val smoothing = StrokeStabilization.smoothing(
            stabilization,
            stabilizationBySpeed,
            stabilizationBySpeedMode,
            inputVelocity,
        )
        val modeledX = if (hasAcceptedSample) x * smoothing + normalizedX * (1f - smoothing) else normalizedX
        val modeledY = if (hasAcceptedSample) y * smoothing + normalizedY * (1f - smoothing) else normalizedY
        if (!force && hasAcceptedSample && hypot((modeledX - x).toDouble(), (modeledY - y).toDouble()) <= MINIMUM_DISTANCE) {
            return false
        }

        val rawVelocity = if (hasAcceptedSample) {
            normalizedVelocity(modeledX - x, modeledY - y, viewportWidthPx, viewportHeightPx, elapsed)
        } else 0f
        val modeledVelocity = if (hasAcceptedSample) {
            velocity * VELOCITY_SMOOTHING + rawVelocity * (1f - VELOCITY_SMOOTHING)
        } else rawVelocity

        x = modeledX.coerceIn(0f, 1f)
        y = modeledY.coerceIn(0f, 1f)
        pressure = adjustedPressure.coerceIn(MINIMUM_PRESSURE, 1f)
        timeMs = sampleTimeMs
        tilt = sampleTilt
        orientation = sampleOrientation
        velocity = modeledVelocity
        hasAcceptedSample = true
        return true
    }

    private fun normalizedVelocity(
        deltaX: Float,
        deltaY: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        elapsedMs: Long,
    ): Float = (
        hypot(
            (deltaX * viewportWidthPx.coerceAtLeast(1)).toDouble(),
            (deltaY * viewportHeightPx.coerceAtLeast(1)).toDouble(),
        ).toFloat() / elapsedMs / VELOCITY_FULL_SCALE_PX_PER_MS
    ).coerceIn(0f, 1f)

    private companion object {
        const val MINIMUM_DISTANCE = .0008
        const val MINIMUM_PRESSURE = .05f
        const val VELOCITY_FULL_SCALE_PX_PER_MS = 2f
        const val VELOCITY_SMOOTHING = .65f
    }
}
