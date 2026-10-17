package app.inkloom.document

/**
 * Geometry of the continuous page view: pages stacked top to bottom with a gap, the active page
 * live and its neighbours shown as renders. Heights are each page's fit-to-window box height in
 * pixels before zoom; the active page's centre sits at the window centre plus [panY].
 *
 * The active page changes only when a scroll carries a neighbour's centre closer to the window
 * centre than the active page's own. Drawing never pans the view, so a stroke never changes the
 * active page; the switch keeps every page where it is on screen.
 */
object ContinuousPageStrip {
    data class Placement(val index: Int, val centerOffset: Float)
    data class Switch(val index: Int, val rebasedPanY: Float)

    /** Unscaled distance between the centres of pages [from] and [to]. */
    fun centerDistance(heights: List<Float>, gap: Float, from: Int, to: Int): Float {
        if (from == to) return 0f
        val (low, high) = if (from < to) from to to else to to from
        var distance = heights[low] / 2f + heights[high] / 2f + gap * (high - low)
        for (index in low + 1 until high) distance += heights[index]
        return if (to > from) distance else -distance
    }

    /** Neighbours within [reach] pages of [active] whose box overlaps the window vertically. */
    fun visibleNeighbours(heights: List<Float>, gap: Float, active: Int, scale: Float, panY: Float, windowHeight: Float, reach: Int = 2): List<Placement> {
        if (active !in heights.indices || scale <= 0f) return emptyList()
        val half = windowHeight / 2f
        return (maxOf(0, active - reach)..minOf(heights.lastIndex, active + reach)).filter { it != active }.mapNotNull { index ->
            val offset = centerDistance(heights, gap, active, index)
            val center = panY + offset * scale
            val extent = heights[index] * scale / 2f
            if (center + extent < -half || center - extent > half) null else Placement(index, offset)
        }
    }

    /** The page that should become active after a scroll, with the pan that keeps the view still. */
    fun switchAfterScroll(heights: List<Float>, gap: Float, active: Int, scale: Float, panY: Float): Switch? {
        if (active !in heights.indices || scale <= 0f || !panY.isFinite()) return null
        var best = active
        var bestDistance = kotlin.math.abs(panY)
        for (index in heights.indices) {
            if (index == active) continue
            val distance = kotlin.math.abs(panY + centerDistance(heights, gap, active, index) * scale)
            if (distance < bestDistance) { best = index; bestDistance = distance }
        }
        if (best == active) return null
        return Switch(best, panY + centerDistance(heights, gap, active, best) * scale)
    }
}
