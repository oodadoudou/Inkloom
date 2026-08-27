package app.inkloom.engine.v5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import java.util.LinkedHashMap

/** Android presentation adapter with a bounded generation cache for animated selection overlays. */
internal object MaskTileCanvasRenderer : PixelMemoryTrimmable {
    private data class CacheKey(
        val surfaceId: String,
        val generation: Long,
        val key: PixelTileKey,
        val defaultAlpha: Int,
    )

    private const val MAX_CACHED_TILES = 96
    private val cache = object : LinkedHashMap<CacheKey, Bitmap>(MAX_CACHED_TILES, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Bitmap>?): Boolean {
            val remove = size > MAX_CACHED_TILES
            if (remove) eldest?.value?.takeUnless(Bitmap::isRecycled)?.recycle()
            return remove
        }
    }
    @Suppress("unused")
    private val memoryRegistration = PixelMemoryPressureCoordinator.register(this)
    override val memoryTrimPriority: Int get() = 10
    internal val retainedTileCount: Int get() = synchronized(cache) { cache.size }

    override fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult = synchronized(cache) {
        val before = cache.values.sumOf { it.allocationByteCount.toLong() }
        // Mask projections are cheap to reconstruct and are not authoritative selection state.
        cache.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        cache.clear()
        PixelMemoryTrimResult(releasedCpuBytes = before)
    }

    fun draw(snapshot: MaskTileSurfaceSnapshot, canvas: Canvas, destination: RectF, paint: Paint? = null) {
        if (snapshot.defaultAlpha == 255 && snapshot.tileCount == 0) {
            val requestedAlpha = paint?.alpha ?: 255
            val solid = Paint(paint ?: Paint()).apply { color = Color.WHITE; alpha = requestedAlpha }
            canvas.drawRect(destination, solid)
            return
        }
        val columns = (snapshot.logicalWidth + snapshot.tileSize - 1) / snapshot.tileSize
        val rows = (snapshot.logicalHeight + snapshot.tileSize - 1) / snapshot.tileSize
        val keys = if (snapshot.defaultAlpha == 0) snapshot.tileInfo.map { it.key } else buildList(columns * rows) {
            for (row in 0 until rows) for (column in 0 until columns) add(PixelTileKey(column, row))
        }
        val scaleX = destination.width() / snapshot.logicalWidth
        val scaleY = destination.height() / snapshot.logicalHeight
        val generations = snapshot.tileInfo.associate { it.key to it.generation }
        keys.forEach { key ->
            val left = key.column * snapshot.tileSize
            val top = key.row * snapshot.tileSize
            val bounds = PixelRect(
                left,
                top,
                minOf(left + snapshot.tileSize, snapshot.logicalWidth),
                minOf(top + snapshot.tileSize, snapshot.logicalHeight),
            )
            val cacheKey = CacheKey(snapshot.surfaceId, generations[key] ?: -1L, key, snapshot.defaultAlpha)
            val tile = synchronized(cache) { cache[cacheKey] ?: createTile(snapshot, bounds).also { cache[cacheKey] = it } }
            canvas.drawBitmap(
                tile,
                null,
                RectF(
                    destination.left + bounds.left * scaleX,
                    destination.top + bounds.top * scaleY,
                    destination.left + bounds.right * scaleX,
                    destination.top + bounds.bottom * scaleY,
                ),
                paint,
            )
        }
    }

    private fun createTile(snapshot: MaskTileSurfaceSnapshot, bounds: PixelRect): Bitmap {
        val alpha = snapshot.copyAlpha(bounds)
        val pixels = IntArray(alpha.size) { index ->
            (alpha[index].toInt().and(0xff) shl 24) or (Color.WHITE and 0x00ffffff)
        }
        return Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, bounds.width, 0, 0, bounds.width, bounds.height)
        }
    }
}
