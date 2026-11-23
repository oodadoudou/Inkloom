package app.inkloom.engine.v5.material

/** Immutable piecewise-linear pressure response. Empty points mean a linear response. */
class PressureResponse(
    val enabled: Boolean,
    minimum: Float,
    points: List<Pair<Float, Float>> = emptyList(),
) {
    val minimum = minimum.coerceIn(0f, 1f)
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

    fun evaluate(rawPressure: Float): Float {
        if (!enabled) return 1f
        val pressure = rawPressure.coerceIn(0f, 1f)
        val upper = curve.indexOfFirst { it.first >= pressure }.takeIf { it >= 0 } ?: curve.lastIndex
        if (upper <= 0) return mapMinimum(curve.first().second)
        val left = curve[upper - 1]
        val right = curve[upper]
        val span = right.first - left.first
        val amount = if (span <= 1e-6f) 0f else (pressure - left.first) / span
        return mapMinimum(left.second + (right.second - left.second) * amount)
    }

    private fun mapMinimum(value: Float): Float = minimum + (1f - minimum) * value.coerceIn(0f, 1f)

    companion object {
        val CONSTANT = PressureResponse(enabled = false, minimum = 1f)
    }
}

