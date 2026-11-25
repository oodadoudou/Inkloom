package app.inkloom.document

import app.inkloom.color.pixelFormat
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.ColorizeSettings
import app.inkloom.engine.v5.tools.LineArtColorize
import app.inkloom.engine.v5.tools.LineColorBlend
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

    /**
     * Line colour blend: a layer clipped to the line art whose line pixels take a darker shade of the
     * colours painted beneath them (the visible paint layers under the line art, merged).
     */
    fun lineColor(assets: DocumentAssetService, page: InkPage, lineArtId: String, name: String): PreparedPageGeometry {
        requireUncropped(page)
        val index = page.layers.indexOfFirst { it.id == lineArtId }
        val lineArt = requireNotNull(page.layers.getOrNull(index)?.takeIf { it.isPaintSurfaceLayer() }) { "Choose the line art layer" }
        val width = pixelWidth(page)
        val lines = premultiplied(assets.renderRasterLayerSurface(page, lineArt.id, width).snapshot())
        val below = page.layers.take(index).filter { it.visible && it.isPaintSurfaceLayer() && it.rasterTileSurface != null }
        require(below.isNotEmpty()) { "Paint colours under the line art first" }
        val colours = IntArray(lines.pixels.size)
        below.forEach { layer -> sourceOver(colours, premultiplied(assets.renderRasterLayerSurface(page, layer.id, width).snapshot()).pixels) }
        val pixels = LineColorBlend.render(lines.pixels, colours, lines.width, lines.height)
        return insert(assets, page, lineArt, above = true, pixels, lines.width, lines.height, name, LayerBlendMode.NORMAL, hide = null, clipped = true)
    }

    private class Pixels(val pixels: IntArray, val width: Int, val height: Int)

    private fun premultiplied(snapshot: PixelTileSurfaceSnapshot): Pixels {
        val eight = if (snapshot.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) snapshot
            else PixelSurfaceResampling.convert(snapshot, PixelTileFormat.PREMULTIPLIED_SRGB_8888).snapshot()
        return Pixels(eight.copyPremultipliedPixels(PixelRect(0, 0, eight.logicalWidth, eight.logicalHeight)), eight.logicalWidth, eight.logicalHeight)
    }

    /** [top] over [bottom], premultiplied, in place; layers rendered at one width share one size. */
    private fun sourceOver(bottom: IntArray, top: IntArray) {
        for (i in bottom.indices.take(minOf(bottom.size, top.size))) {
            val source = top[i]; val alpha = source ushr 24
            if (alpha == 0) continue
            val inverse = 255 - alpha; val destination = bottom[i]
            fun channel(shift: Int) = ((source ushr shift and 255) + (destination ushr shift and 255) * inverse / 255).coerceAtMost(255)
            bottom[i] = (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }

    private fun surfaceOf(pixels: IntArray, width: Int, height: Int) = PixelTileSurface(width, height).also { s ->
        s.beginWrite("assist layer").run { replacePremultipliedPixels(PixelRect(0, 0, width, height), pixels); commit().close() }
    }

    private fun insert(assets: DocumentAssetService, page: InkPage, next: InkLayer, above: Boolean, pixels: IntArray, width: Int, height: Int,
                       name: String, blend: LayerBlendMode, hide: String?, clipped: Boolean = false): PreparedPageGeometry {
        val surface = surfaceOf(pixels, width, height).let { if (page.pixelFormat() == PixelTileFormat.PREMULTIPLIED_SRGB_8888) it else PixelSurfaceResampling.convert(it.snapshot(), page.pixelFormat()) }
        val raster = assets.saveRasterTileSurface(surface)
        try {
            val layer = InkLayer(id = UUID.randomUUID().toString(), name = name, parentId = next.parentId, rasterTileSurface = raster,
                blendMode = blend, clippedToBelow = clipped)
            val index = page.layers.indexOfFirst { it.id == next.id }
            val layers = page.layers.map { if (it.id == hide) it.copy(visible = false) else it }.toMutableList()
            layers.add(if (above) index + 1 else index, layer)
            return PreparedPageGeometry(PageGeometryEdit(page, page.copy(layers = layers))) { assets.releaseRasterTileReservation(raster) }
        } catch (failure: Throwable) { assets.releaseRasterTileReservation(raster); throw failure }
    }
}
