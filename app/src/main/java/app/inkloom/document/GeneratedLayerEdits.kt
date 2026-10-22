package app.inkloom.document

import app.inkloom.color.pixelFormat
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import kotlin.math.roundToInt

/** Page edits for fill, gradient and tone layers; each is one undoable page change. */
internal object GeneratedLayerEdits {
    /** Generated pixels cover the whole page, before any crop, like every other paint layer. */
    private fun pixelSize(page: InkPage): Pair<Int, Int> =
        page.width.roundToInt().coerceIn(1, 8192) to page.height.roundToInt().coerceIn(1, 8192)

    /**
     * A new generated layer directly above [targetId] (or inside it, for a folder). As in CSP an
     * active selection becomes the layer's mask; on a cropped page the layer is left unmasked.
     */
    fun create(assets: DocumentAssetService, page: InkPage, targetId: String, generator: LayerGenerator, name: String,
               selection: MaskTileSurfaceSnapshot?): PreparedPageGeometry {
        val insertion = requireNotNull(LayerInsertionEdit.create(page, targetId, folder = false)) { "Choose an unlocked layer or folder first" }
        val (width, height) = pixelSize(page)
        val raster = assets.saveRasterTileSurface(GeneratedLayerRenderer.render(generator, width, height, page.dpi, page.pixelFormat()))
        val mask = try { selection?.takeIf { !it.isEmpty && page.crop == PageCrop() }?.let(assets::saveMaskTileSurface) }
            catch (failure: Throwable) { assets.releaseRasterTileReservation(raster); throw failure }
        fun release() { assets.releaseRasterTileReservation(raster); mask?.let { assets.releaseAssetReservation(it.assetPath) } }
        try {
            val layer = insertion.layer.copy(name = name, rasterTileSurface = raster, generator = generator,
                mask = mask?.let { LayerMask(surface = it) })
            val after = requireNotNull(insertion.copy(layer = layer).apply(page)) { "The layer could not be inserted here" }
            return PreparedPageGeometry(PageGeometryEdit(page, after), ::release)
        } catch (failure: Throwable) { release(); throw failure }
    }

    /** New settings re-render the pixels; the previous pixels stay reserved for undo. */
    fun update(assets: DocumentAssetService, page: InkPage, layerId: String, generator: LayerGenerator): PreparedPageGeometry {
        val layer = requireNotNull(page.layers.firstOrNull { it.id == layerId && it.generator != null }) { "This is not a fill, gradient or tone layer" }
        val reserved = layer.rasterTileSurface?.let { assets.saveRasterTileSurface(assets.loadRasterTileSurface(it), it) }
        val (width, height) = pixelSize(page)
        val raster = try { assets.saveRasterTileSurface(GeneratedLayerRenderer.render(generator, width, height, page.dpi, page.pixelFormat())) }
            catch (failure: Throwable) { reserved?.let(assets::releaseRasterTileReservation); throw failure }
        val after = page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(rasterTileSurface = raster, rasterRotationQuarterTurns = 0, generator = generator) else it
        })
        return PreparedPageGeometry(PageGeometryEdit(page, after)) {
            assets.releaseRasterTileReservation(raster); reserved?.let(assets::releaseRasterTileReservation)
        }
    }

    /** CSP Layer > Rasterize: the pixels stay, the settings go, and brushes may paint again. */
    fun rasterize(page: InkPage, layerId: String): PageGeometryEdit {
        require(page.layers.any { it.id == layerId && it.generator != null }) { "This layer is already a pixel layer" }
        return PageGeometryEdit(page, page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(generator = null) else it }))
    }
}
