package app.inkloom.document

object LayerHierarchy {
    fun effectiveVisible(layers: List<InkLayer>, layer: InkLayer): Boolean =
        layer.visible && ancestors(layers, layer).all(InkLayer::visible)

    fun effectiveLocked(layers: List<InkLayer>, layer: InkLayer): Boolean =
        layer.locked || ancestors(layers, layer).any(InkLayer::locked)

    fun depth(layers: List<InkLayer>, layer: InkLayer): Int = ancestors(layers, layer).size

    fun panelOrder(layers: List<InkLayer>): List<InkLayer> {
        val byParent = layers.groupBy(InkLayer::parentId)
        val result = mutableListOf<InkLayer>()
        fun append(parentId: String?) {
            byParent[parentId].orEmpty().asReversed().forEach { layer ->
                result += layer
                if (layer.role == LayerRole.FOLDER && !layer.collapsed) append(layer.id)
            }
        }
        append(null)
        return result
    }

    fun availableFolders(layers: List<InkLayer>, movingLayerId: String): List<InkLayer> =
        layers.filter { candidate -> candidate.role == LayerRole.FOLDER && candidate.id != movingLayerId && !isDescendant(layers, candidate.id, movingLayerId) }

    fun removeFolder(page: InkPage, folderId: String): InkPage {
        val folder = page.layers.firstOrNull { it.id == folderId && it.role == LayerRole.FOLDER } ?: return page
        return page.copy(
            layers = page.layers.filterNot { it.id == folder.id }.map { if (it.parentId == folder.id) it.copy(parentId = folder.parentId) else it },
        )
    }

    fun ancestors(layers: List<InkLayer>, layer: InkLayer): List<InkLayer> {
        val byId = layers.associateBy(InkLayer::id)
        val result = mutableListOf<InkLayer>()
        val seen = mutableSetOf(layer.id)
        var parentId = layer.parentId
        while (parentId != null && seen.add(parentId)) {
            val parent = byId[parentId] ?: break
            result += parent
            parentId = parent.parentId
        }
        return result
    }

    private fun isDescendant(layers: List<InkLayer>, candidateId: String, ancestorId: String): Boolean {
        val candidate = layers.firstOrNull { it.id == candidateId } ?: return false
        return ancestors(layers, candidate).any { it.id == ancestorId }
    }
}
