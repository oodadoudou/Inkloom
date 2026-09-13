package app.inkloom.document

enum class LayerGroupRejection { INVALID_TARGET, THROUGH_CLIPPING }

object LayerGroupPolicy {
    fun rejection(layers: List<InkLayer>): LayerGroupRejection? {
        for (siblings in layers.groupBy(InkLayer::parentId).values) {
            var base: InkLayer? = null
            for (layer in siblings) {
                if (layer.passThrough && layer.role != LayerRole.FOLDER) return LayerGroupRejection.INVALID_TARGET
                if (layer.clippedToBelow && (layer.passThrough || base?.passThrough == true)) return LayerGroupRejection.THROUGH_CLIPPING
                if (!layer.clippedToBelow) base = layer
            }
        }
        return null
    }
    fun canPassThrough(layers: List<InkLayer>, id: String): Boolean =
        layers.any { it.id == id && it.role == LayerRole.FOLDER } &&
            rejection(layers.map { if (it.id == id) it.copy(passThrough = true) else it }) == null
}

data class LayerGroupCompositingEdit(override val layerId: String, val before: Boolean, val after: Boolean) : LayerAppearanceEdit {
    override val isNoOp get() = before == after
    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId && it.role == LayerRole.FOLDER } ?: return null
        if (layer.passThrough != if (undo) after else before) return null
        val value = if (undo) before else after
        if (value && !LayerGroupPolicy.canPassThrough(page.layers, layerId)) return null
        return page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(passThrough = value) else it })
    }
}
