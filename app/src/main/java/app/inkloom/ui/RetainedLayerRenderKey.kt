package app.inkloom.ui

import app.inkloom.document.InkPage
import app.inkloom.document.LayerHierarchy

/** Raw retained content has no opacity/blend/mask or raster-checkpoint dependency. */
internal data class RetainedLayerRenderKey(
    val id: String,
    val visible: Boolean,
    val capturedAlphaIds: Set<String>,
) {
    companion object {
        fun forPage(page: InkPage): List<RetainedLayerRenderKey> {
            val contentLayers = page.elements.mapTo(hashSetOf()) { it.layerId }
            return page.layers.filter { it.id in contentLayers }.map { layer ->
                RetainedLayerRenderKey(layer.id, LayerHierarchy.effectiveVisible(page.layers, layer),
                    if (layer.alphaLock) layer.alphaLockBaseContentIds.toSet() else emptySet())
            }.sortedBy { it.id }
        }
    }
}
