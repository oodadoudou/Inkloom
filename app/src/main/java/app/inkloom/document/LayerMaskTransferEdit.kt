package app.inkloom.document

/** Moves or copies one immutable mask attachment without altering either layer's pixels. */
data class LayerMaskTransferEdit(
    val sourceLayerId: String,
    val targetLayerId: String,
    val sourceBefore: LayerMask,
    val targetBefore: LayerMask?,
    val copy: Boolean,
) : LayerAppearanceEdit {
    override val layerId: String get() = sourceLayerId
    val sourceAfter: LayerMask? get() = sourceBefore.takeIf { copy }
    val targetAfter: LayerMask get() = sourceBefore
    override val isNoOp: Boolean get() = sourceLayerId == targetLayerId

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        if (sourceLayerId == targetLayerId) return null
        val source = page.layers.firstOrNull { it.id == sourceLayerId } ?: return null
        val target = page.layers.firstOrNull { it.id == targetLayerId } ?: return null
        if (source.role == LayerRole.BACKGROUND || target.role == LayerRole.BACKGROUND) return null
        val expectedSource = if (undo) sourceAfter else sourceBefore
        val expectedTarget = if (undo) targetAfter else targetBefore
        if (source.mask != expectedSource || target.mask != expectedTarget) return null
        val nextSource = if (undo) sourceBefore else sourceAfter
        val nextTarget = if (undo) targetBefore else targetAfter
        return page.copy(layers = page.layers.map {
            when (it.id) {
                sourceLayerId -> it.copy(mask = nextSource)
                targetLayerId -> it.copy(mask = nextTarget)
                else -> it
            }
        })
    }
}
