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
    val beforePassThrough: Boolean = false,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after && !beforePassThrough

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.role == LayerRole.BACKGROUND) return null
        if (layer.blendMode != if (undo) after else before) return null
        if (layer.passThrough != if (undo) false else beforePassThrough) return null
        if (undo && beforePassThrough && !LayerGroupPolicy.canPassThrough(page.layers, layerId)) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(blendMode = if (undo) before else after, passThrough = undo && beforePassThrough) else it
        })
    }
}

data class LayerPaletteColorEdit(
    override val layerId: String,
    val before: LayerPaletteColor,
    val after: LayerPaletteColor,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val expected = if (undo) after else before
        val replacement = if (undo) before else after
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.paletteColor != expected) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(paletteColor = replacement) else it
        })
    }
}
