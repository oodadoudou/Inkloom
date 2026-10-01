package app.inkloom.engine.v5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.inkloom.engine.v5.composite.PixelGroupProjectionCache
import app.inkloom.engine.v5.composite.PixelLayerProjectionInput
import app.inkloom.engine.v5.composite.PixelProjectionNode
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import java.util.IdentityHashMap

/** Converts retained content only when its bitmap changes; live raster deltas stay sparse. */
internal class LayerContentProjectionCache(
    private val width: Int,
    private val height: Int,
    private val tileSize: Int,
    private val format: PixelTileFormat,
) {
    private data class Converted(val generation: Int, val pixels: PixelTileSurfaceSnapshot)
    private val converted = IdentityHashMap<Bitmap, Converted>()
    private val caches = mutableMapOf<String, PixelGroupProjectionCache>()
    private val usedBitmaps = java.util.Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
    private val usedLayers = hashSetOf<String>()
    internal var bitmapConversionCount = 0
        private set

    fun beginFrame() { usedBitmaps.clear(); usedLayers.clear() }
    fun endFrame() { converted.keys.retainAll(usedBitmaps); caches.keys.retainAll(usedLayers) }

    fun resolve(layerId: String, raster: PixelLayerProjectionInput?, baseBitmap: Bitmap?, additionsBitmap: Bitmap?,
        capturedAlphaLock: Boolean, baseSurface: PixelTileSurfaceSnapshot? = null, additionsSurface: PixelTileSurfaceSnapshot? = null): PixelTileSurfaceSnapshot? {
        if (baseBitmap == null && additionsBitmap == null && baseSurface == null && additionsSurface == null) return null
        usedLayers += layerId
        val cache = caches.getOrPut(layerId) { PixelGroupProjectionCache(width, height, tileSize, format, "content/$layerId") }
        val base = buildList<PixelProjectionNode> {
            raster?.let { add(PixelProjectionNode.Paint(it.copy(layerId = "raster/$layerId"))) }
            (baseSurface ?: baseBitmap?.let(::pixels))?.let { add(PixelProjectionNode.Paint(PixelLayerProjectionInput("elements/$layerId", it))) }
        }
        val nodes = mutableListOf<PixelProjectionNode>()
        nodes += PixelProjectionNode.Group("base/$layerId", base)
        (additionsSurface ?: additionsBitmap?.let(::pixels))?.let {
            nodes += PixelProjectionNode.Paint(PixelLayerProjectionInput("additions/$layerId", it,
                clipToBaseBelow = capturedAlphaLock))
        }
        return cache.update(nodes).snapshot
    }

    private fun pixels(bitmap: Bitmap): PixelTileSurfaceSnapshot {
        require(!bitmap.isRecycled) { "Retained layer bitmap has been released" }
        usedBitmaps += bitmap
        converted[bitmap]?.takeIf { it.generation == bitmap.generationId }?.let { return it.pixels }
        val requiredConfig = if (format.isHighPrecision) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
        val needsConversion = bitmap.width != width || bitmap.height != height || bitmap.config != requiredConfig
        val prepared = if (needsConversion) Bitmap.createBitmap(width, height, requiredConfig).also {
            Canvas(it).drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else bitmap
        val snapshot = try { PixelTileBitmapAdapter.import(prepared, tileSize).snapshot() }
            finally { if (prepared !== bitmap) prepared.recycle() }
        converted[bitmap] = Converted(bitmap.generationId, snapshot)
        bitmapConversionCount++
        return snapshot
    }
}
