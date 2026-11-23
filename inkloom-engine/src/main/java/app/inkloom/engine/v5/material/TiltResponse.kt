package app.inkloom.engine.v5.material

/** Immutable stylus-tilt response. Android reports zero upright and PI/2 flat. */
class TiltResponse(
    val enabled: Boolean,
    minimum: Float,
    start: Float = 0f,
    points: List<Pair<Float, Float>> = emptyList(),
) {
    val minimum = minimum.coerceIn(0f, 1f)
    private val start = start.coerceIn(0f, .99f)
    private val curve = points
        .filter { (x, y) -> x.isFinite() && y.isFinite() }
        .map { (x, y) -> x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f) }
        .sortedBy(Pair<Float, Float>::first)
        .distinctBy(Pair<Float, Float>::first)
        .let { normalized ->
            buildList {
                if (normalized.firstOrNull()?.first != 0f) add(0f to 0f)
                addAll(normalized)
                if (normalized.lastOrNull()?.first != 1f) add(1f to 1f)
            }
        }

    fun evaluate(tiltRadians: Float): Float {
        if (!enabled) return 1f
        val normalized = (tiltRadians.takeIf(Float::isFinite)?.coerceIn(0f, HALF_PI) ?: 0f) / HALF_PI
        val input = ((normalized - start) / (1f - start)).coerceIn(0f, 1f)
        val upper = curve.indexOfFirst { it.first >= input }.takeIf { it >= 0 } ?: curve.lastIndex
        val value = if (upper <= 0) curve.first().second else {
            val left = curve[upper - 1]
            val right = curve[upper]
            val amount = if (right.first - left.first <= 1e-6f) 0f else (input - left.first) / (right.first - left.first)
            left.second + (right.second - left.second) * amount
        }
        return minimum + (1f - minimum) * value.coerceIn(0f, 1f)
    }

    companion object {
        val CONSTANT = TiltResponse(false, 1f)
        private const val HALF_PI = (Math.PI / 2.0).toFloat()
    }
}
