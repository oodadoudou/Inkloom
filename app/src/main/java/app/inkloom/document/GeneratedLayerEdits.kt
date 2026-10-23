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

    /** A new 3D layer above [targetId] holding [figure], rendered at the page's resolution. */
    fun createFigure(assets: DocumentAssetService, page: InkPage, targetId: String, figure: DrawingFigure, name: String, withRuler: Boolean): PreparedPageGeometry {
        val insertion = requireNotNull(LayerInsertionEdit.create(page, targetId, folder = false)) { "Choose an unlocked layer or folder first" }
        val raster = renderFigure(assets, page, figure)
        try {
            val layer = insertion.layer.copy(name = name, rasterTileSurface = raster, figure = figure, rulers = cameraRuler(page, figure, withRuler, null))
            val after = requireNotNull(insertion.copy(layer = layer).apply(page)) { "The layer could not be inserted here" }
            return PreparedPageGeometry(PageGeometryEdit(page, after)) { assets.releaseRasterTileReservation(raster) }
        } catch (failure: Throwable) { assets.releaseRasterTileReservation(raster); throw failure }
    }

    /** A new pose or camera re-renders the 3D layer; the previous pixels stay reserved for undo. */
    fun updateFigure(assets: DocumentAssetService, page: InkPage, layerId: String, figure: DrawingFigure, withRuler: Boolean): PreparedPageGeometry {
        val layer = requireNotNull(page.layers.firstOrNull { it.id == layerId && it.figure != null }) { "This is not a 3D layer" }
        val reserved = layer.rasterTileSurface?.let { assets.saveRasterTileSurface(assets.loadRasterTileSurface(it), it) }
        val raster = try { renderFigure(assets, page, figure) } catch (failure: Throwable) { reserved?.let(assets::releaseRasterTileReservation); throw failure }
        val after = page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(rasterTileSurface = raster, rasterRotationQuarterTurns = 0, figure = figure, rulers = cameraRuler(page, figure, withRuler, it.rulers)) else it
        })
        return PreparedPageGeometry(PageGeometryEdit(page, after)) { assets.releaseRasterTileReservation(raster); reserved?.let(assets::releaseRasterTileReservation) }
    }

    private fun renderFigure(assets: DocumentAssetService, page: InkPage, figure: DrawingFigure): RasterTileSurfaceRef {
        val (width, height) = pixelSize(page)
        val surface = app.inkloom.engine.v5.surface.PixelTileSurface(width, height).also { s ->
            s.beginWrite("3D figure").run {
                replacePremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, width, height), DrawingFigureRenderer.render(figure, width, height))
                commit().close()
            }
        }
        val converted = if (page.pixelFormat() == app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_SRGB_8888) surface
            else app.inkloom.engine.v5.tools.PixelSurfaceResampling.convert(surface.snapshot(), page.pixelFormat())
        return assets.saveRasterTileSurface(converted)
    }

    /** CSP's 3D layer perspective ruler: vanishing points matching the camera, kept in step with it. */
    private fun cameraRuler(page: InkPage, figure: DrawingFigure, withRuler: Boolean, current: LayerRulers?): LayerRulers? {
        if (!withRuler) return current?.copy(perspective = null)?.takeUnless { it.isEmpty }
        val perspective = FigureRig.perspective(figure, page.width, page.height) ?: return current
        return (current ?: LayerRulers()).copy(perspective = perspective, visible = true)
    }

    /** CSP Layer > Rasterize: the pixels stay, the settings or vector lines go, and every tool works again. */
    fun rasterize(page: InkPage, layerId: String): PageGeometryEdit {
        require(page.layers.any { it.id == layerId && (it.generator != null || it.vector != null || it.figure != null) }) { "This layer is already a pixel layer" }
        return PageGeometryEdit(page, page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(generator = null, vector = null, figure = null) else it }))
    }
}
