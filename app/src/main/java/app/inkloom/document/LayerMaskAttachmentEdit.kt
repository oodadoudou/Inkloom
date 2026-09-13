package app.inkloom.document

/** Only the mask attachment changes; child pixels, ordering and unrelated layer properties survive history. */
data class LayerMaskAttachmentEdit(
    override val layerId: String,
    val before: LayerMask?,
    val after: LayerMask?,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.role == LayerRole.BACKGROUND || layer.mask != if (undo) after else before) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(mask = if (undo) before else after) else it
        })
    }
}
