package app.inkloom.document

/** Document-only raster layer operations. Removed immutable assets become GC candidates. */
object RasterLayerDocumentOperations {
    fun clearUnlocked(page: InkPage): InkPage = page.copy(layers = page.layers.map { layer ->
        if (layer.contentKind != LayerContentKind.RASTER || LayerHierarchy.effectiveLocked(page.layers, layer)) {
            layer
        } else {
            layer.copy(rasterTileSurface = null, rasterAssetPath = null, rasterRotationQuarterTurns = 0)
        }
    })
}
