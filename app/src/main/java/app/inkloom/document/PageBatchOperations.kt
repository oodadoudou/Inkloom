package app.inkloom.document

object PageBatchOperations {
    /** Keep the visible page. If it was removed, prefer its next surviving neighbor, then previous. */
    fun pageAfterRemoval(order: List<String>, removed: Set<String>, current: String?): String? {
        val remaining = order.filterNot { it in removed }
        if (current in remaining) return current
        val index = order.indexOf(current)
        return if (index >= 0) order.drop(index + 1).firstOrNull { it !in removed }
            ?: order.take(index).lastOrNull { it !in removed }
        else remaining.firstOrNull()
    }

    fun moveBlock(order: List<String>, selectedIds: Set<String>, targetStartIndex: Int): List<String> {
        val selected = order.filter { it in selectedIds }
        if (selected.isEmpty() || selected.size == order.size) return order
        val remaining = order.filterNot { it in selectedIds }.toMutableList()
        val insertion = targetStartIndex.coerceIn(0, remaining.size)
        remaining.addAll(insertion, selected)
        return remaining
    }
}
