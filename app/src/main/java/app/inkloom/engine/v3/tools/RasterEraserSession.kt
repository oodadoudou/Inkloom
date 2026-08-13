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

/** Bounded native-Skia preview for a raster brush eraser.
 *
 * The input thread records one capsule into a reusable alpha surface and composites only that
 * dirty rectangle with DST_OUT. There is no Bitmap.getPixels/setPixels or Kotlin per-pixel loop.
 * Imported brush-tip replay remains the authoritative pen-up pass until the V3 brush pipeline
 * writes directly into sparse layer tiles.
 */
class RasterEraserSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask.copy(Bitmap.Config.ARGB_8888, false)
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private val targetCanvas = Canvas(bitmap)
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val sourceRect = Rect()
    private var closed = false

    fun eraseSegment(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        radiusPixels: Float,
        opacity: Float,
        hardness: Float,
    ): Rect? {
        if (closed || bitmap.isRecycled) return null
        val radius = radiusPixels.coerceAtLeast(.5f)
        val bounds = Rect(
            floor(minOf(startX, endX) - radius - 2f).toInt().coerceIn(0, bitmap.width),
            floor(minOf(startY, endY) - radius - 2f).toInt().coerceIn(0, bitmap.height),
            ceil(maxOf(startX, endX) + radius + 2f).toInt().coerceIn(0, bitmap.width),
            ceil(maxOf(startY, endY) + radius + 2f).toInt().coerceIn(0, bitmap.height),
        )
        if (bounds.isEmpty) return null
        val canvas = ensureCoverage(bounds.width(), bounds.height()) ?: return null
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val hardRadius = radius * hardness.coerceIn(0f, 1f)
        val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
        coveragePaint.strokeWidth = (hardRadius * 2f).coerceAtLeast(1f)
        coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
        canvas.drawLine(
            startX - bounds.left,
            startY - bounds.top,
            endX - bounds.left,
            endY - bounds.top,
            coveragePaint,
        )
        coveragePaint.maskFilter = null

        sourceRect.set(0, 0, bounds.width(), bounds.height())
        selectionSurface?.let { selection ->
            canvas.drawBitmap(selection, bounds, sourceRect, selectionPaint)
        }
        erasePaint.alpha = (opacity.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
        if (erasePaint.alpha == 0) return null
        targetCanvas.drawBitmap(requireNotNull(coverageBitmap), sourceRect, bounds, erasePaint)
        return bounds
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled || original.isRecycled) return
        bitmap.eraseColor(Color.TRANSPARENT)
        targetCanvas.drawBitmap(original, 0f, 0f, null)
    }

    private fun ensureCoverage(width: Int, height: Int): Canvas? {
        val current = coverageBitmap
        if (current != null && !current.isRecycled && current.width >= width && current.height >= height) return coverageCanvas
        current?.recycle()
        val next = runCatching {
            Bitmap.createBitmap(maxOf(width, current?.width ?: 0), maxOf(height, current?.height ?: 0), Bitmap.Config.ALPHA_8)
        }.getOrNull() ?: return null
        coverageBitmap = next
        return Canvas(next).also { coverageCanvas = it }
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
        coverageBitmap?.recycle()
        coverageBitmap = null
        coverageCanvas = null
        blurFilters.clear()
    }

    private companion object {
        const val MAX_BLUR_RADIUS = 256f
    }
}
