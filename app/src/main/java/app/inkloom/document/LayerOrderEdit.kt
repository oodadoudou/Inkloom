package app.inkloom.document

enum class LayerDropPosition { ABOVE, BELOW, INSIDE }
data class LayerDrop(val targetId: String, val position: LayerDropPosition)
enum class LayerMoveRejection { MISSING_TARGET, PAPER, LOCKED, CYCLE, THROUGH_CLIPPING }
class LayerMoveRejected(val reason: LayerMoveRejection) : IllegalArgumentException(reason.name)

/** Only order and parent ownership are historical; newer pixel checkpoints and properties survive. */
data class LayerOrderEdit(
    override val layerId: String,
    val before: List<Pair<String, String?>>,
    val after: List<Pair<String, String?>>,
) : LayerAppearanceEdit {
    override val isNoOp get() = before == after
    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        if (page.layers.map { it.id to it.parentId } != if (undo) after else before) return null
        val current = page.layers.associateBy(InkLayer::id)
        val next = (if (undo) before else after).map { (id, parent) -> current.getValue(id).copy(parentId = parent) }
        if (LayerGroupPolicy.rejection(next) != null) return null
        return page.copy(layers = next)
    }

    companion object {
        fun roots(layers: List<InkLayer>, selected: Set<String>): List<InkLayer> {
            val byId = layers.associateBy(InkLayer::id)
            return LayerHierarchy.panelOrder(layers.map { it.copy(collapsed = false) }).filter { layer ->
                if (layer.id !in selected) false else {
                    var parent = layer.parentId
                    val visited = mutableSetOf(layer.id)
                    var ancestorSelected = false
                    while (parent != null && visited.add(parent)) {
                        if (parent in selected) { ancestorSelected = true; break }
                        parent = byId[parent]?.parentId
                    }
                    !ancestorSelected
                }
            }
        }

        fun create(page: InkPage, primaryId: String, selected: Set<String>, drop: LayerDrop): LayerOrderEdit {
            fun reject(reason: LayerMoveRejection): Nothing = throw LayerMoveRejected(reason)
            val layers = page.layers
            val byId = layers.associateBy(InkLayer::id)
            if (selected.isEmpty() || primaryId !in selected || selected.any { it !in byId }) reject(LayerMoveRejection.MISSING_TARGET)
            val target = byId[drop.targetId] ?: reject(LayerMoveRejection.MISSING_TARGET)
            val moving = roots(layers, selected)
            val movingIds = moving.mapTo(hashSetOf(), InkLayer::id)
            if (moving.any { it.role == LayerRole.BACKGROUND }) reject(LayerMoveRejection.PAPER)
            if (moving.any { LayerHierarchy.effectiveLocked(layers, it) }) reject(LayerMoveRejection.LOCKED)
            if (target.role == LayerRole.BACKGROUND && drop.position != LayerDropPosition.ABOVE) reject(LayerMoveRejection.PAPER)
            val parentId = if (drop.position == LayerDropPosition.INSIDE) {
                if (target.role != LayerRole.FOLDER) reject(LayerMoveRejection.MISSING_TARGET)
                target.id
            } else target.parentId
            var ancestor: String? = target.id
            val seen = hashSetOf<String>()
            while (ancestor != null && seen.add(ancestor)) {
                if (ancestor in movingIds) reject(LayerMoveRejection.CYCLE)
                ancestor = byId[ancestor]?.parentId
            }
            if (parentId != null && LayerHierarchy.effectiveLocked(layers, byId.getValue(parentId))) reject(LayerMoveRejection.LOCKED)
            val remaining = layers.filterNot { it.id in movingIds }.toMutableList()
            val index = when (drop.position) {
                LayerDropPosition.ABOVE -> remaining.indexOfFirst { it.id == target.id } + 1
                LayerDropPosition.BELOW -> remaining.indexOfFirst { it.id == target.id }
                LayerDropPosition.INSIDE -> remaining.indexOfLast { it.parentId == parentId }.takeIf { it >= 0 }?.plus(1)
                    ?: (remaining.indexOfFirst { it.id == parentId } + 1)
            }
            remaining.addAll(index, moving.asReversed().map { it.copy(parentId = parentId) })
            when (LayerGroupPolicy.rejection(remaining)) {
                LayerGroupRejection.THROUGH_CLIPPING -> reject(LayerMoveRejection.THROUGH_CLIPPING)
                LayerGroupRejection.INVALID_TARGET -> reject(LayerMoveRejection.MISSING_TARGET)
                null -> Unit
            }
            return LayerOrderEdit(primaryId, layers.map { it.id to it.parentId }, remaining.map { it.id to it.parentId })
        }
    }
}
