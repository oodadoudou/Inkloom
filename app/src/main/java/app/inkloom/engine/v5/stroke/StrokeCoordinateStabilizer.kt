package app.inkloom.engine.v5.stroke

import app.inkloom.engine.v5.coordinates.DocumentPoint
import kotlin.math.hypot
import kotlin.math.exp
import app.inkloom.brushpresetv5.MotionFilteringSettings

/** Per-contact state shared by paint, erasing and raster tools. Inputs are document pixels. */
class StrokeCoordinateStabilizer(global: Float, brush: Float,
    globalMotion: MotionFilteringSettings = MotionFilteringSettings(),
    brushMotion: MotionFilteringSettings = MotionFilteringSettings()) {
    init {
        require(global.isFinite() && global in 0f..1f)
        require(brush.isFinite() && brush in 0f..1f)
    }
    private val globalFilter = global.takeIf { it > 0f }?.let(::CoordinateFilter)
    private val brushFilter = brush.takeIf { it > 0f }?.let(::CoordinateFilter)
    private val globalMotionFilter = globalMotion.takeIf { it.amount > 0f }?.let(::MotionFilter)
    private val brushMotionFilter = brushMotion.takeIf { it.amount > 0f }?.let(::MotionFilter)
    fun apply(point: DocumentPoint): DocumentPoint {
        val globalStable = globalFilter?.apply(point) ?: point
        val globalFiltered = globalMotionFilter?.apply(globalStable) ?: globalStable
        val brushStable = brushFilter?.apply(globalFiltered) ?: globalFiltered
        return brushMotionFilter?.apply(brushStable) ?: brushStable
    }

    /** Arc-length response is independent of event timing; expression opens the response at turns.
     * No queued samples or pen-up replay: every output belongs to the current contact. */
    private class MotionFilter(private val settings: MotionFilteringSettings) {
        private var raw: DocumentPoint? = null
        private var output: DocumentPoint? = null
        private var directionX = 0f
        private var directionY = 0f
        fun apply(point: DocumentPoint): DocumentPoint {
            val previous = raw
            raw = point
            if (previous == null) { output = point; return point }
            val dx = point.x - previous.x; val dy = point.y - previous.y
            val distance = hypot(dx, dy)
            if (distance < 1e-6f) return requireNotNull(output)
            val ux = dx / distance; val uy = dy / distance
            val turn = ((1f - (ux * directionX + uy * directionY).coerceIn(-1f, 1f)) * .5f)
            val length = .25f + settings.amount * 6f
            val response = (1f - exp(-distance / length)).coerceIn(0f, 1f)
            val expressive = settings.expression * (1f - settings.amount * .5f) * turn
            val alpha = response + (1f - response) * expressive
            val prior = requireNotNull(output)
            var x = prior.x + (point.x - prior.x) * alpha
            var y = prior.y + (point.y - prior.y) * alpha
            val lag = hypot(x - point.x, y - point.y)
            if (lag > length) { x = point.x + (x - point.x) * length / lag; y = point.y + (y - point.y) * length / lag }
            directionX = ux; directionY = uy
            return DocumentPoint(x, y).also { output = it }
        }
    }

    private class CoordinateFilter(amount: Float) {
        private val response = (1f - amount * .85f).coerceIn(.15f, 1f)
        private val maximumLagPixels = .5f + amount * 3.5f
        private var previous: DocumentPoint? = null

        fun apply(point: DocumentPoint): DocumentPoint {
            val prior = previous
            val filtered = if (prior == null) point else {
                val x = prior.x + (point.x - prior.x) * response
                val y = prior.y + (point.y - prior.y) * response
                val dx = x - point.x; val dy = y - point.y
                val lag = hypot(dx, dy)
                if (lag <= maximumLagPixels || lag <= 1e-6f) DocumentPoint(x, y)
                else {
                    val scale = maximumLagPixels / lag
                    DocumentPoint(point.x + dx * scale, point.y + dy * scale)
                }
            }
            previous = filtered
            return filtered
        }
    }
}
