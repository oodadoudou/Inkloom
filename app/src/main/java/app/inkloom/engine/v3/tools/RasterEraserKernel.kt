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

/** Reusable native-Skia capsule kernel independent of document storage and undo policy. */
internal class RasterEraserKernel(
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val selection = RasterSelectionMask(selectionMask, surfaceWidth, surfaceHeight)
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()

    fun boundsFor(startX: Float, startY: Float, endX: Float, endY: Float, radiusPixels: Float): Rect {
        val radius = radiusPixels.coerceAtLeast(.5f)
        return Rect(
            floor(minOf(startX, endX) - radius - 2f).toInt().coerceIn(0, surfaceWidth),
            floor(minOf(startY, endY) - radius - 2f).toInt().coerceIn(0, surfaceHeight),
            ceil(maxOf(startX, endX) + radius + 2f).toInt().coerceIn(0, surfaceWidth),
            ceil(maxOf(startY, endY) + radius + 2f).toInt().coerceIn(0, surfaceHeight),
        )
    }

    /** Applies a global-space eraser footprint into [destination] on [target]. */
    fun erase(
        target: Canvas,
        destination: Rect,
        globalBounds: Rect,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        radiusPixels: Float,
        opacity: Float,
        hardness: Float,
    ): Boolean {
        if (globalBounds.isEmpty || destination.isEmpty) return false
        val radius = radiusPixels.coerceAtLeast(.5f)
        val canvas = ensureCoverage(globalBounds.width(), globalBounds.height()) ?: return false
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val hardRadius = radius * hardness.coerceIn(0f, 1f)
        val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
        coveragePaint.strokeWidth = (hardRadius * 2f).coerceAtLeast(1f)
        coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
        canvas.drawLine(
            startX - globalBounds.left,
            startY - globalBounds.top,
            endX - globalBounds.left,
            endY - globalBounds.top,
            coveragePaint,
        )
        coveragePaint.maskFilter = null
        localRect.set(0, 0, globalBounds.width(), globalBounds.height())
        selection.apply(requireNotNull(coverageBitmap), globalBounds)
        erasePaint.alpha = (opacity.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
        if (erasePaint.alpha == 0) return false
        target.drawBitmap(requireNotNull(coverageBitmap), localRect, destination, erasePaint)
        return true
    }

    private fun ensureCoverage(width: Int, height: Int): Canvas? {
        val current = coverageBitmap
        if (current != null && !current.isRecycled && current.width >= width && current.height >= height) return coverageCanvas
        current?.recycle()
        val next = runCatching {
            Bitmap.createBitmap(maxOf(width, current?.width ?: 0), maxOf(height, current?.height ?: 0), Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        coverageBitmap = next
        return Canvas(next).also { coverageCanvas = it }
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(MAX_BLUR_RADIUS) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurFilters[bucket] = it }
    }

    override fun close() {
        coverageBitmap?.recycle()
        coverageBitmap = null
        coverageCanvas = null
        blurFilters.clear()
    }

    private companion object {
        const val MAX_BLUR_RADIUS = 256f
    }
}
