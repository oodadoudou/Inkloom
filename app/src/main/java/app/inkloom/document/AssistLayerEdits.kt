package app.inkloom.document

import app.inkloom.color.pixelFormat
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.ColorizeSettings
import app.inkloom.engine.v5.tools.LineArtColorize
import app.inkloom.engine.v5.tools.PixelSurfaceResampling
import app.inkloom.engine.v5.tools.ShadingAssist
import app.inkloom.engine.v5.tools.ShadingSettings
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Shading Assist and Colorize as page edits: the result is a new layer (shading multiplies over
 * the colours above them; colours go under the line art), and the hint layer is hidden, in one
 * undo step. Both work at the page's own resolution.
 */
internal object AssistLayerEdits {
    private fun pixelWidth(page: InkPage) = page.width.roundToInt().coerceIn(1, 8192)

    private fun requireUncropped(page: InkPage) = require(page.crop == PageCrop()) { "Remove the page crop first" }

    /** The page's reference layers (line art), merged; null when there are none. */
    fun referenceLines(assets: DocumentAssetService, page: InkPage, except: String, width: Int): PixelTileSurfaceSnapshot? {
        val references = page.layers.filter { it.id != except && it.isPaintSurfaceLayer() && LayerHierarchy.effectiveReference(page.layers, it) }
        if (references.isEmpty()) return null
        val merged = references.map { assets.renderRasterLayerSurface(page, it.id, width).snapshot() }
        if (merged.size == 1) return merged.single()
        val w = merged.first().logicalWidth; val h = merged.first().logicalHeight
        val alpha = IntArray(w * h)
        merged.forEach { s -> for (y in 0 until h) for (x in 0 until w) {
            val a = if (s.pixelFormat.isHighPrecision) (s.linearPixelAt(x, y).alpha * 255f).roundToInt() else s.pixelAt(x, y) ushr 24
            if (a > alpha[y * w + x] ushr 24) alpha[y * w + x] = a shl 24
        } }
        return surfaceOf(alpha, w, h).snapshot()
    }

    fun shading(assets: DocumentAssetService, page: InkPage, colourLayerId: String, settings: ShadingSettings, useLineArt: Boolean, name: String): PreparedPageGeometry {
        requireUncropped(page)
        val colourLayer = requireNotNull(page.layers.firstOrNull { it.id == colourLayerId && it.isPaintSurfaceLayer() }) { "Choose the layer with the flat colours" }
        val width = pixelWidth(page)
        val colours = assets.renderRasterLayerSurface(page, colourLayer.id, width).snapshot()
        val lines = if (useLineArt) referenceLines(assets, page, colourLayer.id, width) else null
        val pixels = ShadingAssist.render(colours, lines, settings)
        return insert(assets, page, colourLayer, above = true, pixels, colours.logicalWidth, colours.logicalHeight, name, LayerBlendMode.MULTIPLY, hide = null)
    }

    fun colorize(assets: DocumentAssetService, page: InkPage, lineArtId: String, hintId: String?, settings: ColorizeSettings, name: String): PreparedPageGeometry {
        requireUncropped(page)
        val lineArt = requireNotNull(page.layers.firstOrNull { it.id == lineArtId && it.isPaintSurfaceLayer() }) { "Choose the line art layer" }
        val width = pixelWidth(page)
        val lines = assets.renderRasterLayerSurface(page, lineArt.id, width).snapshot()
        val hint = hintId?.let { assets.renderRasterLayerSurface(page, it, width).snapshot() }
        val pixels = LineArtColorize.render(lines, hint, settings)
        return insert(assets, page, lineArt, above = false, pixels, lines.logicalWidth, lines.logicalHeight, name, LayerBlendMode.NORMAL, hide = hintId)
    }

    private fun surfaceOf(pixels: IntArray, width: Int, height: Int) = PixelTileSurface(width, height).also { s ->
        s.beginWrite("assist layer").run { replacePremultipliedPixels(PixelRect(0, 0, width, height), pixels); commit().close() }
    }

    private fun insert(assets: DocumentAssetService, page: InkPage, next: InkLayer, above: Boolean, pixels: IntArray, width: Int, height: Int,
                       name: String, blend: LayerBlendMode, hide: String?): PreparedPageGeometry {
        val surface = surfaceOf(pixels, width, height).let { if (page.pixelFormat() == PixelTileFormat.PREMULTIPLIED_SRGB_8888) it else PixelSurfaceResampling.convert(it.snapshot(), page.pixelFormat()) }
        val raster = assets.saveRasterTileSurface(surface)
        try {
            val layer = InkLayer(id = UUID.randomUUID().toString(), name = name, parentId = next.parentId, rasterTileSurface = raster, blendMode = blend)
            val index = page.layers.indexOfFirst { it.id == next.id }
            val layers = page.layers.map { if (it.id == hide) it.copy(visible = false) else it }.toMutableList()
            layers.add(if (above) index + 1 else index, layer)
            return PreparedPageGeometry(PageGeometryEdit(page, page.copy(layers = layers))) { assets.releaseRasterTileReservation(raster) }
        } catch (failure: Throwable) { assets.releaseRasterTileReservation(raster); throw failure }
    }
}
