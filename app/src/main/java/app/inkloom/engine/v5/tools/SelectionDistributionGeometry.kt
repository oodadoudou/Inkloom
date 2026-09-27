package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelRect

enum class SelectionDistributionMode { START_EDGE, CENTER, END_EDGE, EQUAL_GAP }

/** Ordered bounds include the two fixed endpoints. Returned offsets are in native pixels. */
object SelectionDistributionGeometry {
    fun offsets(ordered: List<PixelRect>, horizontal: Boolean, equalGap: Boolean): List<Float> =
        offsets(ordered, horizontal, if (equalGap) SelectionDistributionMode.EQUAL_GAP else SelectionDistributionMode.CENTER)

    fun offsets(ordered: List<PixelRect>, horizontal: Boolean, mode: SelectionDistributionMode): List<Float> {
        require(ordered.size >= 3)
        val first = ordered.first()
        val last = ordered.last()
        fun anchor(bounds: PixelRect): Float = when (mode) {
            SelectionDistributionMode.START_EDGE, SelectionDistributionMode.EQUAL_GAP ->
                (if (horizontal) bounds.left else bounds.top).toFloat()
            SelectionDistributionMode.CENTER ->
                (if (horizontal) bounds.left + bounds.right else bounds.top + bounds.bottom) / 2f
            SelectionDistributionMode.END_EDGE ->
                (if (horizontal) bounds.right else bounds.bottom).toFloat()
        }
        val anchorStep = (anchor(last) - anchor(first)) / (ordered.size - 1)
        val span = if (horizontal) last.right - first.left else last.bottom - first.top
        val totalExtent = ordered.sumOf { (if (horizontal) it.width else it.height).toDouble() }.toFloat()
        val gap = (span - totalExtent) / (ordered.size - 1)
        var nextEdge = (if (horizontal) first.right else first.bottom).toFloat()
        return ordered.mapIndexed { index, bounds ->
            if (index == 0 || index == ordered.lastIndex) return@mapIndexed 0f
            val start = if (horizontal) bounds.left.toFloat() else bounds.top.toFloat()
            val offset = if (mode == SelectionDistributionMode.EQUAL_GAP) nextEdge + gap - start
                else anchor(first) + anchorStep * index - anchor(bounds)
            nextEdge = (if (horizontal) bounds.right else bounds.bottom) + offset
            offset
        }
    }
}
