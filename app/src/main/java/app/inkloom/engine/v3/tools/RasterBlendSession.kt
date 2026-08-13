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

/** Native-Skia pigment transport over one bounded dirty patch.
 *
 * A frame first snapshots the translated source patch, clips it by the soft brush footprint and
 * active selection, then deposits that existing pigment back into the active layer. No active
 * paint color is introduced and no Kotlin pixel array is created on the interaction path.
 */
class RasterBlendSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask.copy(Bitmap.Config.ARGB_8888, false)
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private val targetCanvas = Canvas(bitmap)
    private var sampleBitmap: Bitmap? = null
    private var sampleCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()
    private var closed = false

    fun blend(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        strength: Float,
        hardness: Float,
    ): Rect? {
        if (closed || bitmap.isRecycled || radiusPixels <= .25f) return null
        val radius = radiusPixels.coerceIn(.5f, minOf(bitmap.width, bitmap.height).toFloat())
        val destination = Rect(
            floor(currentX - radius - 2f).toInt().coerceIn(0, bitmap.width),
            floor(currentY - radius - 2f).toInt().coerceIn(0, bitmap.height),
            ceil(currentX + radius + 2f).toInt().coerceIn(0, bitmap.width),
            ceil(currentY + radius + 2f).toInt().coerceIn(0, bitmap.height),
        )
        if (destination.isEmpty) return null
        val canvases = ensureSurfaces(destination.width(), destination.height()) ?: return null
        val sample = canvases.first
        val coverage = canvases.second
        sample.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        sample.save()
        sample.clipRect(0, 0, destination.width(), destination.height())
        sample.drawBitmap(bitmap, -destination.left + deltaX, -destination.top + deltaY, sourcePaint)
        sample.restore()

        val localCenterX = currentX - destination.left
        val localCenterY = currentY - destination.top
        val hardRadius = radius * hardness.coerceIn(0f, 1f)
        val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
        coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
        coverage.drawCircle(localCenterX, localCenterY, hardRadius.coerceAtLeast(.5f), coveragePaint)
        coveragePaint.maskFilter = null

        localRect.set(0, 0, destination.width(), destination.height())
        sample.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
        selectionSurface?.let { selection -> sample.drawBitmap(selection, destination, localRect, maskPaint) }

        depositPaint.alpha = (strength.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
        if (depositPaint.alpha == 0) return null
        targetCanvas.drawBitmap(requireNotNull(sampleBitmap), localRect, destination, depositPaint)
        return destination
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled || original.isRecycled) return
        bitmap.eraseColor(Color.TRANSPARENT)
        targetCanvas.drawBitmap(original, 0f, 0f, null)
    }

    private fun ensureSurfaces(width: Int, height: Int): Pair<Canvas, Canvas>? {
        val currentSample = sampleBitmap
        val currentCoverage = coverageBitmap
        if (currentSample != null && currentCoverage != null && !currentSample.isRecycled && !currentCoverage.isRecycled &&
            currentSample.width >= width && currentSample.height >= height && currentCoverage.width >= width && currentCoverage.height >= height
        ) return requireNotNull(sampleCanvas) to requireNotNull(coverageCanvas)
        currentSample?.recycle()
        currentCoverage?.recycle()
        val nextWidth = maxOf(width, currentSample?.width ?: 0)
        val nextHeight = maxOf(height, currentSample?.height ?: 0)
        val nextSample = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val nextCoverage = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ALPHA_8) }.getOrNull()
        if (nextCoverage == null) {
            nextSample.recycle()
            return null
        }
        sampleBitmap = nextSample
        coverageBitmap = nextCoverage
        val nextSampleCanvas = Canvas(nextSample)
        val nextCoverageCanvas = Canvas(nextCoverage)
        sampleCanvas = nextSampleCanvas
        coverageCanvas = nextCoverageCanvas
        return nextSampleCanvas to nextCoverageCanvas
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(MAX_BLUR_RADIUS) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also {
            blurFilters[bucket] = it
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        original.recycle()
        selectionSurface?.recycle()
        sampleBitmap?.recycle()
        coverageBitmap?.recycle()
        sampleBitmap = null
        coverageBitmap = null
        sampleCanvas = null
        coverageCanvas = null
        blurFilters.clear()
    }

    private companion object {
        const val MAX_BLUR_RADIUS = 256f
    }
}
