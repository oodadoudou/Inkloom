package app.inkloom.document

/** Selection is independent of whether the selected node accepts paint. Never redirect an edit. */
object LayerEditingTarget {
    enum class Rejection { MISSING, FOLDER, ADJUSTMENT, BACKGROUND, LOCKED, HIDDEN, GENERATED }

    fun rejection(layers: List<InkLayer>, layerId: String): Rejection? {
        val layer = layers.firstOrNull { it.id == layerId } ?: return Rejection.MISSING
        return when {
            layer.role == LayerRole.FOLDER -> Rejection.FOLDER
            layer.role == LayerRole.ADJUSTMENT -> Rejection.ADJUSTMENT
            layer.role == LayerRole.BACKGROUND -> Rejection.BACKGROUND
            LayerHierarchy.effectiveLocked(layers, layer) -> Rejection.LOCKED
            !LayerHierarchy.effectiveVisible(layers, layer) -> Rejection.HIDDEN
            // As in CSP, fill, gradient and tone layers are edited by their settings or mask.
            layer.generator != null -> Rejection.GENERATED
            else -> null
        }
    }

    /** A folder mask is paintable; folder content itself remains a structural target. */
    fun maskRejection(layers: List<InkLayer>, layerId: String): Rejection? {
        val layer = layers.firstOrNull { it.id == layerId } ?: return Rejection.MISSING
        return when {
            layer.role == LayerRole.BACKGROUND -> Rejection.BACKGROUND
            LayerHierarchy.effectiveLocked(layers, layer) -> Rejection.LOCKED
            !LayerHierarchy.effectiveVisible(layers, layer) -> Rejection.HIDDEN
            else -> null
        }
    }

    /** Only a removed node may change the selection without an explicit user selection. */
    fun retainSelection(layers: List<InkLayer>, selectedId: String): String =
        selectedId.takeIf { id -> layers.any { it.id == id } }
            ?: layers.firstOrNull { it.id == LayerIds.INK }?.id
            ?: layers.firstOrNull(InkLayer::isPaintSurfaceLayer)?.id
            ?: layers.firstOrNull()?.id.orEmpty()
}
