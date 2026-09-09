package app.inkloom.engine.v5.stroke

import app.inkloom.engine.v5.coordinates.DocumentPoint
import kotlin.math.hypot

/** Per-contact state shared by paint, erasing and raster tools. Inputs are document pixels. */
class StrokeCoordinateStabilizer(global: Float, brush: Float) {
    init {
        require(global.isFinite() && global in 0f..1f)
        require(brush.isFinite() && brush in 0f..1f)
    }
    private val filters = listOf(global, brush).filter { it > 0f }.map(::CoordinateFilter)

    fun apply(point: DocumentPoint): DocumentPoint = filters.fold(point) { next, filter -> filter.apply(next) }

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
