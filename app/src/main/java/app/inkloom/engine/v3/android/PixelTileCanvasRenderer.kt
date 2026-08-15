package app.inkloom.engine.v3.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws an immutable sparse surface projection without first materializing a page-sized bitmap.
 *
 * Android bitmaps are retained per immutable tile generation. A document transaction therefore
 * invalidates only the tiles whose generation changed, while pan/zoom merely changes their target
 * rectangles. This is the Android edge adapter for the V3 tile renderer; document truth remains in
 * [PixelTileSurfaceSnapshot].
 */
internal class PixelTileCanvasRenderer(
    private val maximumCacheBytes: Long = DEFAULT_CACHE_BYTES,
) : AutoCloseable {
    init {
        require(maximumCacheBytes > 0L) { "maximumCacheBytes must be positive" }
    }

    private data class CacheKey(
        val surfaceId: String,
        val tileKey: PixelTileKey,
        val generation: Long,
    )

    private val cache = LinkedHashMap<CacheKey, Bitmap>(16, .75f, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var retainedBytes = 0L

    val retainedByteSize: Long get() = retainedBytes
    val retainedTileCount: Int get() = cache.size

    fun draw(
        canvas: Canvas,
        snapshot: PixelTileSurfaceSnapshot,
        destination: RectF,
    ) {
        if (destination.isEmpty || snapshot.tileCount == 0) return
        val clip = canvas.clipBounds
        val visibleDestination = RectF(destination).apply {
            if (!intersect(RectF(clip))) return
        }
        val scaleX = destination.width() / snapshot.logicalWidth
        val scaleY = destination.height() / snapshot.logicalHeight
        if (!scaleX.isFinite() || !scaleY.isFinite() || scaleX <= 0f || scaleY <= 0f) return
        val visibleSource = PixelRect(
            left = floor((visibleDestination.left - destination.left) / scaleX).toInt().coerceIn(0, snapshot.logicalWidth),
            top = floor((visibleDestination.top - destination.top) / scaleY).toInt().coerceIn(0, snapshot.logicalHeight),
            right = ceil((visibleDestination.right - destination.left) / scaleX).toInt().coerceIn(0, snapshot.logicalWidth),
            bottom = ceil((visibleDestination.bottom - destination.top) / scaleY).toInt().coerceIn(0, snapshot.logicalHeight),
        )
        if (visibleSource.isEmpty) return

        snapshot.tileInfo.forEach { info ->
            val source = tileBounds(snapshot, info.key).intersect(visibleSource)
            if (source.isEmpty) return@forEach
            val fullTile = tileBounds(snapshot, info.key)
            val bitmap = bitmapFor(snapshot, info.key, info.generation, fullTile)
            val sourceInBitmap = android.graphics.Rect(
                source.left - fullTile.left,
                source.top - fullTile.top,
                source.right - fullTile.left,
                source.bottom - fullTile.top,
            )
            val target = RectF(
                destination.left + source.left * scaleX,
                destination.top + source.top * scaleY,
                destination.left + source.right * scaleX,
                destination.top + source.bottom * scaleY,
            )
            canvas.drawBitmap(bitmap, sourceInBitmap, target, paint)
        }
    }

    /** Removes cached generations for a surface that is no longer displayed. */
    fun releaseSurface(surfaceId: String) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.surfaceId == surfaceId) {
                retainedBytes -= entry.value.allocationByteCount.toLong()
                entry.value.recycle()
                iterator.remove()
            }
        }
    }

    override fun close() {
        cache.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        cache.clear()
        retainedBytes = 0L
    }

    private fun bitmapFor(
        snapshot: PixelTileSurfaceSnapshot,
        key: PixelTileKey,
        generation: Long,
        bounds: PixelRect,
    ): Bitmap {
        val cacheKey = CacheKey(snapshot.surfaceId, key, generation)
        cache[cacheKey]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        removeObsoleteGeneration(snapshot.surfaceId, key, generation)
        val bitmap = PixelTileBitmapAdapter.render(snapshot, bounds)
        cache[cacheKey] = bitmap
        retainedBytes += bitmap.allocationByteCount.toLong()
        trimToBudget()
        return bitmap
    }

    private fun removeObsoleteGeneration(surfaceId: String, key: PixelTileKey, generation: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.surfaceId == surfaceId && entry.key.tileKey == key && entry.key.generation != generation) {
                retainedBytes -= entry.value.allocationByteCount.toLong()
                entry.value.recycle()
                iterator.remove()
            }
        }
    }

    private fun trimToBudget() {
        val iterator = cache.entries.iterator()
        while (retainedBytes > maximumCacheBytes && cache.size > 1 && iterator.hasNext()) {
            val entry = iterator.next()
            retainedBytes -= entry.value.allocationByteCount.toLong()
            entry.value.recycle()
            iterator.remove()
        }
    }

    private fun tileBounds(snapshot: PixelTileSurfaceSnapshot, key: PixelTileKey): PixelRect {
        val left = key.column * snapshot.tileSize
        val top = key.row * snapshot.tileSize
        return PixelRect(
            left,
            top,
            minOf(left + snapshot.tileSize, snapshot.logicalWidth),
            minOf(top + snapshot.tileSize, snapshot.logicalHeight),
        )
    }

    companion object {
        private const val DEFAULT_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
