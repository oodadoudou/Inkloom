package app.inkloom.document

import kotlin.math.hypot
import kotlin.math.atan2

/**
 * Converts irregular Android input samples into repeatable, distance-spaced brush stamps.
 * Coordinates remain normalized while distance and spacing are measured in output pixels.
 */
object StrokeStampSampler {
    /** Mutable live-stroke sampler. Each newly modelled input segment is visited exactly once;
     * committed/export rendering can continue to use [sample] for a stateless replay. */
    class Incremental(
        private val canvasWidth: Float,
        private val canvasHeight: Float,
        private val includeStationaryPoints: Boolean = false,
    ) {
        private val result = ArrayList<StrokePoint>(64)
        private var inputCount = 0
        private var firstPoint: StrokePoint? = null
        private var distanceUntilNext = 0f

        fun update(points: List<StrokePoint>, spacingFor: (StrokePoint) -> Float): List<StrokePoint> {
            if (points.isEmpty()) {
                reset()
                return result
            }
            if (inputCount > points.size || firstPoint != points.first()) reset()
            if (inputCount == 0) {
                firstPoint = points.first()
                result += points.first()
                distanceUntilNext = spacingFor(points.first()).coerceAtLeast(MINIMUM_SPACING_PX)
                inputCount = 1
            }
            val width = canvasWidth.coerceAtLeast(1f)
            val height = canvasHeight.coerceAtLeast(1f)
            for (index in inputCount until points.size) {
                appendSegment(points[index - 1], points[index], width, height, spacingFor)
            }
            inputCount = points.size
            return result
        }

        fun reset() {
            result.clear()
            inputCount = 0
            firstPoint = null
            distanceUntilNext = 0f
        }

        private fun appendSegment(
            start: StrokePoint,
            end: StrokePoint,
            width: Float,
            height: Float,
            spacingFor: (StrokePoint) -> Float,
        ) {
            val segmentWidth = (end.x - start.x) * width
            val segmentHeight = (end.y - start.y) * height
            val segmentLength = hypot(segmentWidth.toDouble(), segmentHeight.toDouble()).toFloat()
            if (segmentLength <= 0f) {
                if (includeStationaryPoints) result += end
                return
            }
            var consumed = 0f
            while (segmentLength - consumed + EPSILON >= distanceUntilNext) {
                consumed += distanceUntilNext
                val point = interpolate(start, end, (consumed / segmentLength).coerceIn(0f, 1f))
                result += point
                distanceUntilNext = spacingFor(point).coerceAtLeast(MINIMUM_SPACING_PX)
            }
            distanceUntilNext -= segmentLength - consumed
            if (distanceUntilNext < EPSILON) distanceUntilNext = MINIMUM_SPACING_PX
        }
    }

    fun sample(
        points: List<StrokePoint>,
        canvasWidth: Float,
        canvasHeight: Float,
        includeStationaryPoints: Boolean = false,
        spacingFor: (StrokePoint) -> Float,
    ): List<StrokePoint> {
        if (points.size <= 1) return points
        return Incremental(canvasWidth, canvasHeight, includeStationaryPoints).update(points, spacingFor).toList()
    }

    fun directionDegrees(points: List<StrokePoint>, index: Int, canvasWidth: Float, canvasHeight: Float): Float {
        if (points.size < 2) return 0f
        val safeIndex = index.coerceIn(0, points.lastIndex)
        val start = points[if (safeIndex == points.lastIndex) safeIndex - 1 else safeIndex]
        val end = points[if (safeIndex == points.lastIndex) safeIndex else safeIndex + 1]
        val dx = (end.x - start.x) * canvasWidth.coerceAtLeast(1f)
        val dy = (end.y - start.y) * canvasHeight.coerceAtLeast(1f)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun interpolate(start: StrokePoint, end: StrokePoint, amount: Float) = StrokePoint(
        x = lerp(start.x, end.x, amount),
        y = lerp(start.y, end.y, amount),
        pressure = lerp(start.pressure, end.pressure, amount),
        timeMs = (start.timeMs + (end.timeMs - start.timeMs) * amount).toLong(),
        tilt = lerp(start.tilt, end.tilt, amount),
        orientation = interpolateAngle(start.orientation, end.orientation, amount),
        velocity = lerp(start.velocity, end.velocity, amount),
    )

    private fun interpolateAngle(start: Float, end: Float, amount: Float): Float {
        var delta = (end - start) % FULL_TURN
        if (delta > HALF_TURN) delta -= FULL_TURN
        if (delta < -HALF_TURN) delta += FULL_TURN
        return start + delta * amount
    }

    private fun lerp(start: Float, end: Float, amount: Float) = start + (end - start) * amount

    private const val MINIMUM_SPACING_PX = .5f
    private const val EPSILON = .0001f
    private const val HALF_TURN = Math.PI.toFloat()
    private const val FULL_TURN = (Math.PI * 2.0).toFloat()
}
