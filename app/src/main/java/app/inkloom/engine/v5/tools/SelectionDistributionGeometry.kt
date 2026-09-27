package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelRect

/** Ordered bounds include the two fixed endpoints. Returned offsets are in native pixels. */
object SelectionDistributionGeometry {
    fun offsets(ordered: List<PixelRect>, horizontal: Boolean, equalGap: Boolean): List<Float> {
        require(ordered.size >= 3)
        val first = ordered.first()
        val last = ordered.last()
        val firstCenter = if (horizontal) (first.left + first.right) / 2f else (first.top + first.bottom) / 2f
        val lastCenter = if (horizontal) (last.left + last.right) / 2f else (last.top + last.bottom) / 2f
        val centerStep = (lastCenter - firstCenter) / (ordered.size - 1)
        val span = if (horizontal) last.right - first.left else last.bottom - first.top
        val totalExtent = ordered.sumOf { (if (horizontal) it.width else it.height).toDouble() }.toFloat()
        val gap = (span - totalExtent) / (ordered.size - 1)
        var nextEdge = (if (horizontal) first.right else first.bottom).toFloat()
        return ordered.mapIndexed { index, bounds ->
            if (index == 0 || index == ordered.lastIndex) return@mapIndexed 0f
            val start = if (horizontal) bounds.left.toFloat() else bounds.top.toFloat()
            val center = if (horizontal) (bounds.left + bounds.right) / 2f else (bounds.top + bounds.bottom) / 2f
            val offset = if (equalGap) nextEdge + gap - start else firstCenter + centerStep * index - center
            nextEdge = (if (horizontal) bounds.right else bounds.bottom) + offset
            offset
        }
    }
}
