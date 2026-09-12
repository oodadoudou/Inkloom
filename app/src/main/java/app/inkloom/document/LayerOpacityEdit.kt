package app.inkloom.document

/** Changes one property only; concurrent saved strokes, masks and other layer properties survive. */
data class LayerOpacityEdit(val layerId: String, val before: Float, val after: Float) {
    init { require(before.isFinite() && after.isFinite() && before in 0f..1f && after in 0f..1f) }

    fun apply(page: InkPage, undo: Boolean = false): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) return null
        val expected = if (undo) after else before
        if (layer.opacity != expected) return null
        return page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(opacity = if (undo) before else after) else it })
    }
}
