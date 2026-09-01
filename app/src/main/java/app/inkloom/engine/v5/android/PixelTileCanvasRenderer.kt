package app.inkloom.engine.v5.android

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws an immutable sparse surface projection without first materializing a page-sized bitmap.
 *
 * Android bitmaps are retained per immutable tile generation. A document transaction therefore
 * invalidates only the tiles whose generation changed, while pan/zoom merely changes their target
 * rectangles. This is the Android edge adapter for the V5 tile renderer; document truth remains in
 * [PixelTileSurfaceSnapshot].
 */
internal class PixelTileCanvasRenderer(
    private val maximumCacheBytes: Long = DEFAULT_CACHE_BYTES,
) : AutoCloseable, PixelMemoryTrimmable {
    init {
        require(maximumCacheBytes > 0L) { "maximumCacheBytes must be positive" }
    }

    private data class CacheKey(
        val surfaceId: String,
        val pixelFormat: PixelTileFormat,
        val tileKey: PixelTileKey,
        val generation: Long,
    )

    private class CachedTile(
        val bitmap: Bitmap,
    ) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }

        fun close() {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private val cache = LinkedHashMap<CacheKey, CachedTile>(16, .75f, true)
    private val shaderPaint = Paint().apply { isAntiAlias = false }
    private val shaderMatrix = Matrix()
    private var retainedBytes = 0L
    private val memoryRegistration = PixelMemoryPressureCoordinator.register(this)

    override val memoryTrimPriority: Int get() = 10

    val retainedByteSize: Long get() = retainedBytes
    val retainedTileCount: Int get() = cache.size
    internal var lastCandidateTileCount: Int = 0
        private set
    internal var lastDrawnTileCount: Int = 0
        private set

    @Synchronized
    fun draw(
        canvas: Canvas,
        snapshot: PixelTileSurfaceSnapshot,
        destination: RectF,
    ) {
        lastCandidateTileCount = 0
        lastDrawnTileCount = 0
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

        val firstColumn = visibleSource.left / snapshot.tileSize
        val lastColumn = (visibleSource.right - 1) / snapshot.tileSize
        val firstRow = visibleSource.top / snapshot.tileSize
        val lastRow = (visibleSource.bottom - 1) / snapshot.tileSize
        fun drawCandidate(key: PixelTileKey, generation: Long) {
            lastCandidateTileCount++
            val fullTile = tileBounds(snapshot, key)
            val source = fullTile.intersect(visibleSource)
            if (source.isEmpty) return
            lastDrawnTileCount++
            val cached = tileFor(snapshot, key, generation, fullTile)
            val bitmap = cached.bitmap
            val fullTarget = RectF(
                destination.left + fullTile.left * scaleX,
                destination.top + fullTile.top * scaleY,
                destination.left + fullTile.right * scaleX,
                destination.top + fullTile.bottom * scaleY,
            )
            // A CLAMP shader supplies a real edge texel to Skia's linear filter. drawBitmap and
            // child RenderNode paths both use transparent DECAL pixels just outside an isolated
            // tile; at fractional zoom those samples exposed the paper as a regular 128 px grid.
            // Rect edges are deliberately not anti-aliased so adjacent tile coverage is exact.
            shaderMatrix.reset()
            shaderMatrix.setScale(fullTarget.width() / bitmap.width, fullTarget.height() / bitmap.height)
            shaderMatrix.postTranslate(fullTarget.left, fullTarget.top)
            cached.shader.setLocalMatrix(shaderMatrix)
            shaderPaint.shader = cached.shader
            canvas.drawRect(fullTarget, shaderPaint)
            shaderPaint.shader = null
        }

        val coordinateCount = (lastColumn - firstColumn + 1L) * (lastRow - firstRow + 1L)
        if (snapshot.tileCount.toLong() < coordinateCount) {
            // A zoomed-out sparse page may expose thousands of empty coordinates. Iterate the
            // immutable allocated-key view and reject off-clip keys instead of probing every hole.
            snapshot.tileKeys.forEach { key ->
                if (key.column in firstColumn..lastColumn && key.row in firstRow..lastRow) {
                    snapshot.generationAt(key)?.let { generation -> drawCandidate(key, generation) }
                }
            }
        } else {
            // A small damage clip is cheaper to query directly than scanning all document tiles.
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                val key = PixelTileKey(column, row)
                val generation = snapshot.generationAt(key)
                if (generation == null) lastCandidateTileCount++ else drawCandidate(key, generation)
            }
        }
    }

    /** Removes cached generations for a surface that is no longer displayed. */
    @Synchronized
    fun releaseSurface(surfaceId: String) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.surfaceId == surfaceId) {
                retainedBytes -= entry.value.bitmap.allocationByteCount.toLong()
                entry.value.close()
                iterator.remove()
            }
        }
    }

    @Synchronized
    override fun close() {
        memoryRegistration.close()
        clearCache()
    }

    @Synchronized
    override fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult {
        val beforeCpu = retainedBytes
        when (pressure) {
            PixelMemoryPressure.UI_HIDDEN -> Unit
            PixelMemoryPressure.MODERATE -> {
                trimToBytes((maximumCacheBytes / 4L).coerceAtLeast(1L))
            }
            PixelMemoryPressure.CRITICAL -> clearCache()
        }
        return PixelMemoryTrimResult(
            releasedCpuBytes = (beforeCpu - retainedBytes).coerceAtLeast(0L),
            releasedGpuBytes = 0L,
        )
    }

    private fun tileFor(
        snapshot: PixelTileSurfaceSnapshot,
        key: PixelTileKey,
        generation: Long,
        bounds: PixelRect,
    ): CachedTile {
        val cacheKey = CacheKey(snapshot.surfaceId, snapshot.pixelFormat, key, generation)
        cache[cacheKey]?.takeUnless { it.bitmap.isRecycled }?.let { return it }
        removeObsoleteGeneration(snapshot.surfaceId, key, generation)
        val bitmap = PixelTileBitmapAdapter.render(snapshot, bounds)
        val cached = CachedTile(bitmap)
        cache[cacheKey] = cached
        retainedBytes += bitmap.allocationByteCount.toLong()
        trimToBytes(maximumCacheBytes)
        return cached
    }

    private fun removeObsoleteGeneration(surfaceId: String, key: PixelTileKey, generation: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.surfaceId == surfaceId && entry.key.tileKey == key && entry.key.generation != generation) {
                retainedBytes -= entry.value.bitmap.allocationByteCount.toLong()
                entry.value.close()
                iterator.remove()
            }
        }
    }

    private fun trimToBytes(targetBytes: Long) {
        val iterator = cache.entries.iterator()
        while (retainedBytes > targetBytes && cache.isNotEmpty() && iterator.hasNext()) {
            val entry = iterator.next()
            retainedBytes -= entry.value.bitmap.allocationByteCount.toLong()
            entry.value.close()
            iterator.remove()
        }
    }

    private fun clearCache() {
        cache.values.forEach(CachedTile::close)
        cache.clear()
        retainedBytes = 0L
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
