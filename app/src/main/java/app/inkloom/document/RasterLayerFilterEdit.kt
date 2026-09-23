package app.inkloom.document

/** One reversible pixel-filter publication. Neither preview nor cancellation enters page history. */
data class RasterLayerFilterEdit(
    val layerId: String,
    val before: RasterTileSurfaceRef?,
    val beforeRotation: Int,
    val after: RasterTileSurfaceRef,
) {
    fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() } ?: return null
        val expected = if (undo) after else before
        val expectedRotation = if (undo) 0 else beforeRotation
        if (layer.rasterTileSurface != expected || layer.rasterRotationQuarterTurns != expectedRotation) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(
                rasterTileSurface = if (undo) before else after,
                rasterRotationQuarterTurns = if (undo) beforeRotation else 0,
            ) else it
        })
    }
}
