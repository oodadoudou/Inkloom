package app.inkloom.document

/** Resolve against measured, visible page centers; page size and panel width may vary. */
object PageDropPosition {
    fun insertionIndex(order: List<String>, selected: Set<String>, centers: Map<String, Float>, pointerY: Float): Int? {
        if (!pointerY.isFinite()) return null
        val remaining = order.filterNot { it in selected }
        val target = remaining.filter { centers[it]?.isFinite() == true }.minByOrNull { kotlin.math.abs(centers.getValue(it) - pointerY) } ?: return null
        return remaining.indexOf(target) + if (pointerY >= centers.getValue(target)) 1 else 0
    }
}
