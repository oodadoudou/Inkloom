package app.inkloom.document

/** A scoped appearance patch in the same chronological history as paint, never a page snapshot. */
sealed interface LayerAppearanceEdit {
    val layerId: String
    val isNoOp: Boolean
    fun apply(page: InkPage, undo: Boolean = false): InkPage?
}

data class LayerBlendEdit(
    override val layerId: String,
    val before: LayerBlendMode,
    val after: LayerBlendMode,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.role == LayerRole.BACKGROUND) return null
        if (layer.blendMode != if (undo) after else before) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(blendMode = if (undo) before else after) else it
        })
    }
}
