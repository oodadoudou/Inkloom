package app.inkloom.document

/** Secondary selection includes the primary layer in batch operations without changing it. */
object LayerSecondarySelection {
    fun toggle(primaryId: String, selected: Set<String>, swipedId: String): Set<String> {
        if (swipedId == primaryId) return selected
        val next = if (swipedId in selected) selected - swipedId else selected + swipedId + primaryId
        return if (next.all { it == primaryId }) emptySet() else next
    }
}
