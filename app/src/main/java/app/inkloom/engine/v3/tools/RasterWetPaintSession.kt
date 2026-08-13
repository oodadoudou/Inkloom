package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Native-Skia local pigment transport for a wet brush.
 *
 * Each input frame snapshots only the touched patch, translates/softens it in native code and
 * clips the result by the brush footprint and active selection. Cancellation keeps lazily copied
 * 128 px tiles, so neither pen-down nor motion performs a full-layer clone or Kotlin pixel loop.
 */
class RasterWetPaintSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
    private val preserveDestinationAlpha: Boolean = false,
) : AutoCloseable {
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private val ownsSelectionSurface = selectionSurface != null && selectionSurface !== selectionMask
    private val targetCanvas = Canvas(bitmap)
    private val originalTiles = linkedMapOf<Int, OriginalTile>()
    private var sourceBitmap: Bitmap? = null
    private var sourceCanvas: Canvas? = null
    private var sampleBitmap: Bitmap? = null
    private var sampleCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val pickupBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val pickupCanvas = Canvas(pickupBitmap)
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val restorePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()
    private val pickupSource = Rect()
    private val pickupDestination = Rect(0, 0, 1, 1)
    private var closed = false

    /** Reusable bounded pickup well. The one scalar readback is needed by the durable pigment point
     * program; raster sampling and downscaling remain native and allocate no pixel array. */
    fun samplePigment(centerX: Float, centerY: Float, radiusPixels: Float): Int {
        if (closed || bitmap.isRecycled) return Color.TRANSPARENT
        val radius = (radiusPixels * .32f).coerceAtLeast(1f)
        pickupSource.set(
            floor(centerX - radius).toInt().coerceIn(0, bitmap.width - 1),
            floor(centerY - radius).toInt().coerceIn(0, bitmap.height - 1),
            ceil(centerX + radius).toInt().coerceIn(1, bitmap.width),
            ceil(centerY + radius).toInt().coerceIn(1, bitmap.height),
        )
        if (pickupSource.isEmpty) return Color.TRANSPARENT
        pickupCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        pickupCanvas.drawBitmap(bitmap, pickupSource, pickupDestination, sourcePaint)
        return pickupBitmap.getPixel(0, 0)
    }

    fun advance(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        pull: Float,
        blur: Float,
        hardness: Float,
        absoluteBlurRadiusPixels: Float = -1f,
    ): Rect? {
        if (closed || bitmap.isRecycled || radiusPixels <= .25f) return null
        val safePull = pull.coerceIn(0f, 1f)
        val safeBlur = blur.coerceIn(0f, 1f)
        if (safePull <= .001f && safeBlur <= .001f) return null
        val radius = radiusPixels.coerceIn(.5f, min(bitmap.width, bitmap.height).toFloat())
        val segmentX = currentX - previousX
        val segmentY = currentY - previousY
        val length = kotlin.math.hypot(segmentX, segmentY).coerceAtLeast(.001f)
        val directionX = segmentX / length
        val directionY = segmentY / length
        // Full Pull transports one nib radius. Lowering the maximum to 0.8 radius caused the
        // pickup well to lag behind a fast stroke until pigment disappeared from its footprint.
        val dragDistance = radius * safePull
        val blurRadius = if (absoluteBlurRadiusPixels >= 0f) {
            absoluteBlurRadiusPixels.coerceIn(0f, radius * 2f)
        } else radius * safeBlur * .35f
        val pad = radius + blurRadius + 3f
        val destination = Rect(
            floor(min(previousX, currentX) - pad).toInt().coerceIn(0, bitmap.width),
            floor(min(previousY, currentY) - pad).toInt().coerceIn(0, bitmap.height),
            ceil(max(previousX, currentX) + pad).toInt().coerceIn(0, bitmap.width),
            ceil(max(previousY, currentY) + pad).toInt().coerceIn(0, bitmap.height),
        )
        if (destination.isEmpty) return null
        val surfaces = ensureSurfaces(destination.width(), destination.height()) ?: return null
        val source = surfaces.source
        val sample = surfaces.sample
        val coverage = surfaces.coverage
        source.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        sample.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        captureOriginalTiles(destination)
        localRect.set(0, 0, destination.width(), destination.height())

        source.drawBitmap(
            bitmap,
            -destination.left + directionX * dragDistance,
            -destination.top + directionY * dragDistance,
            sourcePaint,
        )
        if (blurRadius > .01f) {
            val blurred = runCatching {
                NativeRasterOps.blur(
                    requireNotNull(sourceBitmap),
                    requireNotNull(sampleBitmap),
                    destination.width(),
                    destination.height(),
                    blurRadius.coerceAtMost(MAX_BLUR_RADIUS),
                )
            }.getOrDefault(false)
            if (!blurred) sample.drawBitmap(requireNotNull(sourceBitmap), localRect, localRect, sourcePaint)
        } else {
            sample.drawBitmap(requireNotNull(sourceBitmap), localRect, localRect, sourcePaint)
        }

        val hardRadius = radius * hardness.coerceIn(0f, 1f)
        val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
        // Keep the nominal brush footprint represented even for very soft tools. A tiny hard core
        // blurred across a large feather loses all 8-bit coverage at the outer half of the nib.
        coveragePaint.strokeWidth = (hardRadius * 2f).coerceAtLeast(radius).coerceAtLeast(1f)
        coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
        coverage.drawLine(
            previousX - destination.left,
            previousY - destination.top,
            currentX - destination.left,
            currentY - destination.top,
            coveragePaint,
        )
        coveragePaint.maskFilter = null

        localRect.set(0, 0, destination.width(), destination.height())
        sample.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
        selectionSurface?.let { selection -> sample.drawBitmap(selection, destination, localRect, maskPaint) }
        depositPaint.alpha = (max(safePull * .82f, safeBlur * .55f) * 255f + .5f).toInt().coerceIn(0, 255)
        if (depositPaint.alpha == 0) return null
        depositPaint.xfermode = PorterDuffXfermode(if (preserveDestinationAlpha) PorterDuff.Mode.SRC_ATOP else PorterDuff.Mode.SRC_OVER)
        targetCanvas.drawBitmap(requireNotNull(sampleBitmap), localRect, destination, depositPaint)
        depositPaint.xfermode = null
        return destination
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled) return
        originalTiles.values.forEach { tile ->
            if (!tile.bitmap.isRecycled) targetCanvas.drawBitmap(tile.bitmap, tile.left.toFloat(), tile.top.toFloat(), restorePaint)
        }
    }

    private fun captureOriginalTiles(destination: Rect) {
        val firstTileX = destination.left / CANCEL_TILE_SIZE
        val lastTileX = (destination.right - 1) / CANCEL_TILE_SIZE
        val firstTileY = destination.top / CANCEL_TILE_SIZE
        val lastTileY = (destination.bottom - 1) / CANCEL_TILE_SIZE
        val columns = ceil(bitmap.width / CANCEL_TILE_SIZE.toDouble()).toInt()
        for (tileY in firstTileY..lastTileY) for (tileX in firstTileX..lastTileX) {
            val key = tileY * columns + tileX
            if (key in originalTiles) continue
            val left = tileX * CANCEL_TILE_SIZE
            val top = tileY * CANCEL_TILE_SIZE
            val width = min(CANCEL_TILE_SIZE, bitmap.width - left)
            val height = min(CANCEL_TILE_SIZE, bitmap.height - top)
            val copy = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(copy).drawBitmap(
                bitmap,
                Rect(left, top, left + width, top + height),
                Rect(0, 0, width, height),
                restorePaint,
            )
            originalTiles[key] = OriginalTile(left, top, copy)
        }
    }

    private fun ensureSurfaces(width: Int, height: Int): Surfaces? {
        val currentSource = sourceBitmap
        val currentSample = sampleBitmap
        val currentCoverage = coverageBitmap
        if (currentSource != null && currentSample != null && currentCoverage != null &&
            !currentSource.isRecycled && !currentSample.isRecycled && !currentCoverage.isRecycled &&
            currentSource.width >= width && currentSource.height >= height &&
            currentSample.width >= width && currentSample.height >= height &&
            currentCoverage.width >= width && currentCoverage.height >= height
        ) return Surfaces(requireNotNull(sourceCanvas), requireNotNull(sampleCanvas), requireNotNull(coverageCanvas))
        currentSource?.recycle()
        currentSample?.recycle()
        currentCoverage?.recycle()
        val nextWidth = max(width, max(currentSource?.width ?: 0, currentSample?.width ?: 0))
        val nextHeight = max(height, max(currentSource?.height ?: 0, currentSample?.height ?: 0))
        val nextSource = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val nextSample = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextSample == null) {
            nextSource.recycle()
            return null
        }
        val nextCoverage = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextCoverage == null) {
            nextSource.recycle()
            nextSample.recycle()
            return null
        }
        sourceBitmap = nextSource
        sampleBitmap = nextSample
        coverageBitmap = nextCoverage
        sourceCanvas = Canvas(nextSource)
        sampleCanvas = Canvas(nextSample)
        coverageCanvas = Canvas(nextCoverage)
        return Surfaces(requireNotNull(sourceCanvas), requireNotNull(sampleCanvas), requireNotNull(coverageCanvas))
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(MAX_BLUR_RADIUS) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurFilters[bucket] = it }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (ownsSelectionSurface) selectionSurface?.recycle()
        originalTiles.values.forEach { tile -> tile.bitmap.recycle() }
        originalTiles.clear()
        sourceBitmap?.recycle()
        sampleBitmap?.recycle()
        coverageBitmap?.recycle()
        pickupBitmap.recycle()
        sourceBitmap = null
        sampleBitmap = null
        coverageBitmap = null
        sourceCanvas = null
        sampleCanvas = null
        coverageCanvas = null
        blurFilters.clear()
    }

    private data class OriginalTile(val left: Int, val top: Int, val bitmap: Bitmap)
    private data class Surfaces(val source: Canvas, val sample: Canvas, val coverage: Canvas)

    private companion object {
        const val CANCEL_TILE_SIZE = 128
        const val MAX_BLUR_RADIUS = 256f
    }
}
