package app.inkloom.document

object PageBatchOperations {
    fun moveBlock(order: List<String>, selectedIds: Set<String>, targetStartIndex: Int): List<String> {
        val selected = order.filter { it in selectedIds }
        if (selected.isEmpty() || selected.size == order.size) return order
        val remaining = order.filterNot { it in selectedIds }.toMutableList()
        val insertion = targetStartIndex.coerceIn(0, remaining.size)
        remaining.addAll(insertion, selected)
        return remaining
    }
}
