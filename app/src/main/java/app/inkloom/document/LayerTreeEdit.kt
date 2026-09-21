package app.inkloom.document

import java.util.UUID

enum class LayerTreeAction { GROUP_SELECTED, UNGROUP, DUPLICATE, DELETE }
enum class LayerTreeRejection { MISSING, BACKGROUND, LOCKED, MIXED_PARENTS, CLIPPING, PROTECTED_CHILD, INVALID_STRUCTURE }
class LayerTreeRejected(val reason: LayerTreeRejection) : IllegalArgumentException(reason.name)

/** A layer-tree transaction changes ownership and membership, never a surviving layer's pixels. */
data class LayerTreeEdit(
    val beforeLayers: List<InkLayer>,
    val afterLayers: List<InkLayer>,
    val beforeElements: List<PageElement>,
    val afterElements: List<PageElement>,
    val beforeSelection: String,
    val afterSelection: String,
) {
    val insertedLayers: List<InkLayer> get() = afterLayers.filter { after -> beforeLayers.none { it.id == after.id } }
    val removedLayers: List<InkLayer> get() = beforeLayers.filter { before -> afterLayers.none { it.id == before.id } }

    fun apply(page: InkPage, undo: Boolean): InkPage? {
        val source = if (undo) afterLayers else beforeLayers
        val destination = if (undo) beforeLayers else afterLayers
        if (page.layers.map { it.id to it.parentId } != source.map { it.id to it.parentId }) return null
        val current = page.layers.associateBy(InkLayer::id)
        val layers = destination.map { desired ->
            current[desired.id]?.copy(parentId = desired.parentId) ?: desired
        }
        if (LayerGroupPolicy.rejection(layers) != null) return null
        val sourceElements = if (undo) afterElements else beforeElements
        val destinationElements = if (undo) beforeElements else afterElements
        val sourceIds = sourceElements.mapTo(hashSetOf(), PageElement::id)
        val destinationIds = destinationElements.mapTo(hashSetOf(), PageElement::id)
        if (!page.elements.map(PageElement::id).containsAll(sourceIds - destinationIds)) return null
        val additions = destinationElements.filter { it.id !in sourceIds }
        if (additions.any { candidate -> page.elements.any { it.id == candidate.id } }) return null
        return page.copy(layers = layers,
            elements = page.elements.filterNot { it.id in (sourceIds - destinationIds) } + additions)
    }

    companion object {
        fun create(page: InkPage, action: LayerTreeAction, targetId: String,
            selectedIds: Set<String> = setOf(targetId), nextId: () -> String = { UUID.randomUUID().toString() }): LayerTreeEdit {
            fun reject(reason: LayerTreeRejection): Nothing = throw LayerTreeRejected(reason)
            val original = page.layers
            val target = original.firstOrNull { it.id == targetId } ?: reject(LayerTreeRejection.MISSING)
            val ids = original.mapTo(hashSetOf(), InkLayer::id)
            fun descendants(rootId: String): Set<String> {
                val result = mutableSetOf(rootId)
                do {
                    val size = result.size
                    original.filter { it.parentId in result }.forEach { result += it.id }
                } while (size != result.size)
                return result
            }
            var afterLayers: List<InkLayer> = original
            var afterElements: List<PageElement> = page.elements
            var nextSelection = targetId
            when (action) {
                LayerTreeAction.GROUP_SELECTED -> {
                    if (selectedIds.isEmpty() || targetId !in selectedIds || selectedIds.any { it !in ids }) reject(LayerTreeRejection.MISSING)
                    val roots = LayerOrderEdit.roots(original, selectedIds)
                    if (roots.any { it.role == LayerRole.BACKGROUND }) reject(LayerTreeRejection.BACKGROUND)
                    if (roots.any { LayerHierarchy.effectiveLocked(original, it) }) reject(LayerTreeRejection.LOCKED)
                    if (roots.map(InkLayer::parentId).distinct().size != 1) reject(LayerTreeRejection.MIXED_PARENTS)
                    if (roots.any { it.clippedToBelow }) reject(LayerTreeRejection.CLIPPING)
                    val folderId = nextId()
                    if (folderId in ids) reject(LayerTreeRejection.INVALID_STRUCTURE)
                    val names = original.mapTo(hashSetOf(), InkLayer::name)
                    var suffix = 1
                    while ("Folder $suffix" in names) suffix++
                    val folder = InkLayer(id = folderId, name = "Folder $suffix", role = LayerRole.FOLDER, parentId = roots.first().parentId)
                    val rootIds = roots.mapTo(hashSetOf(), InkLayer::id)
                    val insertion = roots.maxOf { original.indexOf(it) } + 1
                    afterLayers = original.map { if (it.id in rootIds) it.copy(parentId = folderId) else it }
                        .toMutableList().apply { add(insertion, folder) }
                    nextSelection = folderId
                }
                LayerTreeAction.UNGROUP -> {
                    if (target.role != LayerRole.FOLDER) reject(LayerTreeRejection.MISSING)
                    if (LayerHierarchy.effectiveLocked(original, target)) reject(LayerTreeRejection.LOCKED)
                    val children = original.filter { it.parentId == target.id }
                    afterLayers = original.filterNot { it.id == target.id }.map {
                        if (it.parentId == target.id) it.copy(parentId = target.parentId) else it
                    }
                    nextSelection = children.lastOrNull()?.id ?: target.parentId ?: LayerIds.INK
                }
                LayerTreeAction.DUPLICATE -> {
                    if (target.role == LayerRole.BACKGROUND) reject(LayerTreeRejection.BACKGROUND)
                    val subtree = descendants(targetId)
                    val mapping = original.filter { it.id in subtree }.associate { it.id to nextId() }
                    if (mapping.values.toSet().size != mapping.size || mapping.values.any { it in ids }) reject(LayerTreeRejection.INVALID_STRUCTURE)
                    val copies = original.filter { it.id in subtree }.map { source ->
                        source.copy(id = mapping.getValue(source.id),
                            name = if (source.id == targetId) uniqueCopyName(source.name, original.map(InkLayer::name)) else source.name,
                            role = if (source.role == LayerRole.FOLDER) LayerRole.FOLDER else LayerRole.CUSTOM,
                            parentId = source.parentId?.let { mapping[it] ?: it })
                    }
                    afterLayers = original.toMutableList().apply { addAll(original.indexOf(target) + 1, copies) }
                    afterElements = page.elements + page.elements.filter { it.layerId in subtree }.map { source ->
                        val newLayerId = mapping.getValue(source.layerId)
                        copyElement(source, newLayerId, nextId())
                    }
                    nextSelection = mapping.getValue(targetId)
                }
                LayerTreeAction.DELETE -> {
                    if (target.role !in setOf(LayerRole.CUSTOM, LayerRole.FOLDER)) reject(LayerTreeRejection.BACKGROUND)
                    val subtree = descendants(targetId)
                    if (original.filter { it.id in subtree }.any { it.role !in setOf(LayerRole.CUSTOM, LayerRole.FOLDER) })
                        reject(LayerTreeRejection.PROTECTED_CHILD)
                    if (original.filter { it.id in subtree }.any { LayerHierarchy.effectiveLocked(original, it) }) reject(LayerTreeRejection.LOCKED)
                    afterLayers = original.filterNot { it.id in subtree }
                    afterElements = page.elements.filterNot { it.layerId in subtree }
                    nextSelection = target.parentId ?: LayerIds.INK
                }
            }
            if (LayerGroupPolicy.rejection(afterLayers) != null) reject(LayerTreeRejection.CLIPPING)
            return LayerTreeEdit(original, afterLayers, page.elements, afterElements, targetId, nextSelection)
        }

        private fun uniqueCopyName(name: String, names: List<String>): String {
            val used = names.mapTo(hashSetOf()) { it.lowercase() }
            val base = "$name copy"
            var candidate = base
            var suffix = 2
            while (candidate.lowercase() in used) candidate = "$base ${suffix++}"
            return candidate
        }

        private fun copyElement(source: PageElement, layerId: String, id: String): PageElement = when (source) {
            is PageElement.Text -> source.copy(id = id, layerId = layerId)
            is PageElement.Sticky -> source.copy(id = id, layerId = layerId)
            is PageElement.Shape -> source.copy(id = id, layerId = layerId)
            is PageElement.Image -> source.copy(id = id, layerId = layerId)
            is PageElement.Fill -> source.copy(id = id, layerId = layerId)
            is PageElement.Link -> source.copy(id = id, layerId = layerId)
        }
    }
}
