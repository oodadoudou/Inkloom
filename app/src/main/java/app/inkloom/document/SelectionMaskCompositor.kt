package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import app.inkloom.engine.v3.tools.NativeRasterOps
import app.inkloom.settings.SelectionMode
import kotlin.math.ceil
import kotlin.math.sqrt

/** Bounded, page-normalized alpha masks used as the authoritative selection geometry. */
object SelectionMaskCompositor {
    const val MAX_WIDTH = 1_600
    const val MAX_HEIGHT = 2_048
    const val MAX_PIXELS = 1_920_000
    private const val BASE_MASK_PIXELS = 1_200_000

    data class MaskSize(val width: Int, val height: Int)

    fun maskSize(pageWidth: Float, pageHeight: Float): MaskSize? {
        if (!pageWidth.isFinite() || !pageHeight.isFinite() || pageWidth <= 0f || pageHeight <= 0f) return null
        val dimensionScale = minOf(1f, MAX_WIDTH / pageWidth, MAX_HEIGHT / pageHeight)
        val dimensionWidth = pageWidth * dimensionScale
        val dimensionHeight = pageHeight * dimensionScale
        // Keep headroom for BlurMaskFilter's expanded intermediate alpha bitmap.
        val pixelScale = minOf(1f, sqrt(BASE_MASK_PIXELS / (dimensionWidth * dimensionHeight)))
        return MaskSize(
            (dimensionWidth * pixelScale).toInt().coerceIn(1, MAX_WIDTH),
            (dimensionHeight * pixelScale).toInt().coerceIn(1, MAX_HEIGHT),
        )
    }

    fun polygon(points: List<StrokePoint>, pageWidth: Float, pageHeight: Float): Bitmap? {
        if (points.size < 3) return null
        val size = maskSize(pageWidth, pageHeight) ?: return null
        return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val path = Path().apply {
                moveTo(points.first().x.coerceIn(0f, 1f) * size.width, points.first().y.coerceIn(0f, 1f) * size.height)
                points.drop(1).forEach { point ->
                    lineTo(point.x.coerceIn(0f, 1f) * size.width, point.y.coerceIn(0f, 1f) * size.height)
                }
                close()
            }
            Canvas(bitmap).drawPath(path, whitePaint())
        }
    }

    fun full(pageWidth: Float, pageHeight: Float): Bitmap? {
        val size = maskSize(pageWidth, pageHeight) ?: return null
        return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
    }

    fun fromFloodFill(
        source: Bitmap,
        sourceLeft: Int,
        sourceTop: Int,
        sourcePageWidth: Int,
        sourcePageHeight: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): Bitmap? {
        if (source.isRecycled || sourcePageWidth <= 0 || sourcePageHeight <= 0) return null
        val size = maskSize(pageWidth, pageHeight) ?: return null
        return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { target ->
            val destination = RectF(
                sourceLeft.toFloat() / sourcePageWidth * size.width,
                sourceTop.toFloat() / sourcePageHeight * size.height,
                (sourceLeft + source.width).toFloat() / sourcePageWidth * size.width,
                (sourceTop + source.height).toFloat() / sourcePageHeight * size.height,
            )
            Canvas(target).drawBitmap(source, null, destination, whitePaint(filter = true))
        }
    }

    fun fromStrokes(strokes: List<InkStroke>, pageWidth: Float, pageHeight: Float): Bitmap? {
        if (strokes.isEmpty()) return null
        val size = maskSize(pageWidth, pageHeight) ?: return null
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = whitePaint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = (stroke.width * size.width / pageWidth.coerceAtLeast(1f)).coerceAtLeast(1f)
            }
            val path = Path().apply {
                moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                stroke.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            }
            if (stroke.points.size == 1) {
                canvas.drawCircle(stroke.points.first().x * size.width, stroke.points.first().y * size.height, paint.strokeWidth / 2f, whitePaint())
            } else canvas.drawPath(path, paint)
        }
        return bitmap.takeUnless(::isEmpty) ?: run { bitmap.recycle(); null }
    }

    /** Returns a new owned bitmap. Inputs remain owned by their callers. */
    fun compose(base: Bitmap?, region: Bitmap?, mode: SelectionMode): Bitmap? {
        if (region == null || region.isRecycled) return if (mode == SelectionMode.REPLACE || mode == SelectionMode.INTERSECT) null else base?.copy(Bitmap.Config.ARGB_8888, true)
        if (mode == SelectionMode.REPLACE) return nonEmptyCopy(region)
        if (base == null || base.isRecycled) {
            return when (mode) {
                SelectionMode.ADD -> nonEmptyCopy(region)
                SelectionMode.SUBTRACT, SelectionMode.INTERSECT -> null
                SelectionMode.REPLACE -> error("handled above")
            }
        }
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val paint = whitePaint(filter = true).apply {
            blendMode = when (mode) {
                SelectionMode.ADD -> BlendMode.SRC_OVER
                SelectionMode.SUBTRACT -> BlendMode.DST_OUT
                SelectionMode.INTERSECT -> BlendMode.DST_IN
                SelectionMode.REPLACE -> null
            }
        }
        Canvas(result).drawBitmap(region, null, Rect(0, 0, result.width, result.height), paint)
        paint.blendMode = null
        if (isEmpty(result)) {
            result.recycle()
            return null
        }
        return result
    }

    /** Intersects a cropped page-space mask with the current full-page selection. */
    fun clipRegion(
        region: Bitmap,
        regionLeft: Int,
        regionTop: Int,
        pageWidth: Int,
        pageHeight: Int,
        selection: Bitmap?,
    ): Bitmap? {
        if (region.isRecycled || pageWidth <= 0 || pageHeight <= 0) return null
        if (selection == null || selection.isRecycled) return nonEmptyCopy(region)
        val result = region.copy(Bitmap.Config.ARGB_8888, true)
        val paint = whitePaint(filter = true).apply { blendMode = BlendMode.DST_IN }
        Canvas(result).drawBitmap(
            selection,
            null,
            RectF(
                -regionLeft.toFloat(),
                -regionTop.toFloat(),
                pageWidth - regionLeft.toFloat(),
                pageHeight - regionTop.toFloat(),
            ),
            paint,
        )
        paint.blendMode = null
        if (!isEmpty(result)) return result
        result.recycle()
        return null
    }

    fun feather(source: Bitmap?, radiusPagePixels: Float, pageWidth: Float): Bitmap? {
        if (source == null || source.isRecycled) return null
        val requestedRadius = radiusPagePixels.coerceAtLeast(0f) * source.width / pageWidth.coerceAtLeast(1f)
        val radius = requestedRadius.coerceAtMost(maximumSafeBlurRadius(source.width, source.height))
        if (radius < .5f) return source.copy(Bitmap.Config.ARGB_8888, true)
        val offset = IntArray(2)
        val alpha = source.extractAlpha(Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }, offset)
        return try {
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { result ->
                Canvas(result).drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                })
            }
        } finally {
            alpha.recycle()
        }
    }

    fun border(source: Bitmap?, widthPixels: Float, pageWidth: Float): Bitmap? {
        if (source == null || source.isRecycled) return null
        val radius = ceil(widthPixels.coerceAtLeast(1f) / 2f * source.width / pageWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val outer = binaryMorph(source, radius, dilate = true)
        val inner = binaryMorph(source, radius, dilate = false)
        return try {
            compose(outer, inner, SelectionMode.SUBTRACT)
        } finally {
            outer?.recycle()
            inner?.recycle()
        }
    }

    /** Builds a cached two-sided alpha ring for CSP-style marching-ants feedback. The ring is
     * derived from the authoritative mask once per geometry change, never once per animation
     * frame. */
    fun boundaryMask(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        if (NativeRasterOps.boundaryAlpha(source, result)) return result
        result.recycle()
        return null
    }

    fun isEmpty(source: Bitmap?): Boolean {
        if (source == null || source.isRecycled) return true
        return NativeRasterOps.isAlphaEmpty(source)
    }

    private fun binaryMorph(source: Bitmap, radius: Int, dilate: Boolean): Bitmap? {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        if (NativeRasterOps.morphAlpha(source, result, radius, dilate)) return result
        result.recycle()
        return null
    }

    private fun nonEmptyCopy(source: Bitmap): Bitmap? {
        val copy = source.copy(Bitmap.Config.ARGB_8888, true)
        if (!isEmpty(copy)) return copy
        copy.recycle()
        return null
    }

    private fun maximumSafeBlurRadius(width: Int, height: Int): Float {
        var low = 0
        var high = maxOf(width, height) / 4
        while (low < high) {
            val candidate = (low + high + 1) / 2
            // BlurMaskFilter may grow by roughly two radii on each axis. Use a conservative
            // four-radius total expansion and keep the intermediate under the global budget.
            val expansion = candidate.toLong() * 4L
            if ((width + expansion) * (height + expansion) <= MAX_PIXELS.toLong()) low = candidate else high = candidate - 1
        }
        return low.toFloat()
    }

    private fun whitePaint(filter: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG or if (filter) Paint.FILTER_BITMAP_FLAG else 0).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
}
