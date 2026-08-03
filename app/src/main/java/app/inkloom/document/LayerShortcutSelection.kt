package app.inkloom.document

object LayerShortcutSelection {
    fun adjacentEditable(layers: List<InkLayer>, currentLayerId: String, delta: Int): String? {
        if (delta == 0) return currentLayerId.takeIf { id -> layers.any { it.id == id } }
        val direction = if (delta > 0) 1 else -1
        var index = layers.indexOfFirst { it.id == currentLayerId }
        if (index < 0) return null
        while (true) {
            index += direction
            val candidate = layers.getOrNull(index) ?: return null
            if (
                candidate.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) &&
                LayerHierarchy.effectiveVisible(layers, candidate) &&
                !LayerHierarchy.effectiveLocked(layers, candidate)
            ) return candidate.id
        }
    }
}
