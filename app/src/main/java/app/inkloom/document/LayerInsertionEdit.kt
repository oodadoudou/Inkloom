package app.inkloom.document

/** A creation patch changes one node and, if needed, expands its parent. It never restores a page snapshot. */
data class LayerInsertionEdit(
    val layer: InkLayer,
    val previousTargetId: String,
    val belowSiblingId: String?,
    val expandedParentId: String?,
) {
    fun apply(page: InkPage, undo: Boolean = false): InkPage? {
        if (undo) {
            val current = page.layers.firstOrNull { it.id == layer.id } ?: return null
            // The host verifies the live raster is empty after awaiting paint publication.
            // Object and child ownership must also be empty before removing the node.
            if (current.mask != null ||
                page.elements.any { it.layerId == layer.id } || page.layers.any { it.parentId == layer.id }) return null
            return page.copy(layers = page.layers.filterNot { it.id == layer.id }.map {
                if (it.id == expandedParentId) it.copy(collapsed = true) else it
            })
        }
        if (page.layers.any { it.id == layer.id }) return null
        val parent = layer.parentId?.let { id -> page.layers.firstOrNull { it.id == id && it.role == LayerRole.FOLDER } ?: return null }
        if (parent != null && LayerHierarchy.effectiveLocked(page.layers, parent)) return null
        val below = belowSiblingId?.let { id -> page.layers.firstOrNull { it.id == id && it.parentId == layer.parentId } ?: return null }
        val position = if (below != null) page.layers.indexOf(below) + 1
            else if (parent != null) page.layers.indexOf(parent) + 1 else 0
        return page.copy(layers = page.layers.map {
            if (it.id == expandedParentId) it.copy(collapsed = false) else it
        }.toMutableList().apply { add(position, layer) })
    }

    companion object {
        fun create(page: InkPage, targetId: String, folder: Boolean, id: String = java.util.UUID.randomUUID().toString()): LayerInsertionEdit? {
            val target = page.layers.firstOrNull { it.id == targetId } ?: return null
            val parentId = if (target.role == LayerRole.FOLDER) target.id else target.parentId
            val parent = parentId?.let { parent -> page.layers.firstOrNull { it.id == parent && it.role == LayerRole.FOLDER } ?: return null }
            if (parent != null && LayerHierarchy.effectiveLocked(page.layers, parent)) return null
            val role = if (folder) LayerRole.FOLDER else LayerRole.CUSTOM
            val prefix = if (folder) "Folder" else "Layer"
            val names = page.layers.mapTo(hashSetOf()) { it.name }
            var suffix = 1
            while ("$prefix $suffix" in names) suffix++
            val created = InkLayer(id = id, name = "$prefix $suffix", role = role, parentId = parentId)
            val below = if (target.role == LayerRole.FOLDER) page.layers.lastOrNull { it.parentId == target.id }?.id else target.id
            return LayerInsertionEdit(created, targetId, below, parent?.takeIf { it.collapsed }?.id)
        }
    }
}
