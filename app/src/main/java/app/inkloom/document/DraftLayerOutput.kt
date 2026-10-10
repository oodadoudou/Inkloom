package app.inkloom.document

/**
 * Draft layers stay editable on the canvas but follow Clip Studio Paint's export default: they are
 * left out of exported files unless the user explicitly includes them as ordinary layers.
 */
object DraftLayerOutput {
    /** Drafts (including everything inside a draft folder) and layers clipped to them are removed. */
    fun withoutDrafts(page: InkPage): InkPage {
        val removed = page.layers.filter { LayerHierarchy.effectiveDraft(page.layers, it) }.mapTo(hashSetOf(), InkLayer::id)
        if (removed.isEmpty()) return page
        // A clipped layer whose base is not exported would re-clip to another layer elsewhere.
        page.layers.groupBy(InkLayer::parentId).values.forEach { siblings ->
            var baseRemoved = false
            siblings.filter { it.role != LayerRole.BACKGROUND }.forEach { layer ->
                if (!layer.clippedToBelow) baseRemoved = layer.id in removed
                else if (baseRemoved) removed += layer.id
            }
        }
        // An omitted folder leaves with its entire subtree, whatever the flat storage order.
        val children = page.layers.groupBy(InkLayer::parentId)
        val pending = ArrayDeque(removed)
        while (pending.isNotEmpty()) children[pending.removeFirst()].orEmpty().forEach { if (removed.add(it.id)) pending += it.id }
        return page.copy(layers = page.layers.filterNot { it.id in removed }, elements = page.elements.filterNot { it.layerId in removed })
    }

    /** Explicitly included drafts are written and composited as ordinary layers. */
    fun asOrdinaryLayers(page: InkPage): InkPage =
        if (page.layers.none(InkLayer::draft)) page else page.copy(layers = page.layers.map { if (it.draft) it.copy(draft = false) else it })
}
