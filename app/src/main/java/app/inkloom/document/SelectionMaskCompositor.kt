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
import app.inkloom.settings.SelectionMode
import java.util.BitSet
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

    fun invert(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var hasSelection = false
        pixels.indices.forEach { index ->
            val alpha = 255 - Color.alpha(pixels[index])
            if (alpha != 0) hasSelection = true
            pixels[index] = Color.argb(alpha, 255, 255, 255)
        }
        if (!hasSelection) return null
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        }
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

    fun morph(source: Bitmap?, pixels: Float, pageWidth: Float): Bitmap? {
        if (source == null || source.isRecycled || pixels == 0f) return source?.copy(Bitmap.Config.ARGB_8888, true)
        val radius = ceil(kotlin.math.abs(pixels) * source.width / pageWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        return binaryMorph(source, radius, dilate = pixels > 0f)
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

    fun boundaryPoints(source: Bitmap?, maximumPoints: Int = 12_000): FloatArray {
        if (source == null || source.isRecycled || maximumPoints <= 0) return FloatArray(0)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val step = ceil(sqrt(width.toDouble() * height / maximumPoints)).toInt().coerceAtLeast(1)
        val points = ArrayList<Float>(maximumPoints * 2)
        fun selected(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && Color.alpha(pixels[y * width + x]) >= 128
        for (y in 0 until height step step) for (x in 0 until width step step) {
            if (!selected(x, y)) continue
            if (!selected(x - 1, y) || !selected(x + 1, y) || !selected(x, y - 1) || !selected(x, y + 1)) {
                points += (x + .5f) / width
                points += (y + .5f) / height
                if (points.size >= maximumPoints * 2) return points.toFloatArray()
            }
        }
        return points.toFloatArray()
    }

    fun isEmpty(source: Bitmap?): Boolean {
        if (source == null || source.isRecycled) return true
        val row = IntArray(source.width)
        for (y in 0 until source.height) {
            source.getPixels(row, 0, source.width, 0, y, source.width, 1)
            if (row.any { Color.alpha(it) != 0 }) return false
        }
        return true
    }

    private fun binaryMorph(source: Bitmap, radius: Int, dilate: Boolean): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val selected = BitSet(width * height)
        pixels.forEachIndexed { index, color -> if (Color.alpha(color) >= 128) selected.set(index) }
        val target = if (dilate) selected else BitSet(width * height).apply { set(0, width * height); andNot(selected) }
        val distance = distanceTo(target, width, height)
        val output = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            val inside = if (dilate) distance[index] <= radius else {
                val edgeDistance = minOf(x + 1, y + 1, width - x, height - y)
                selected[index] && distance[index] > radius && edgeDistance > radius
            }
            if (inside) output[index] = Color.WHITE
        }
        if (output.none { Color.alpha(it) != 0 }) return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, width, 0, 0, width, height) }
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

    private fun distanceTo(target: BitSet, width: Int, height: Int): IntArray {
        val infinity = width + height + 1
        val result = IntArray(width * height) { if (target[it]) 0 else infinity }
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (x > 0) result[index] = minOf(result[index], result[index - 1] + 1)
            if (y > 0) result[index] = minOf(result[index], result[index - width] + 1)
        }
        for (y in height - 1 downTo 0) for (x in width - 1 downTo 0) {
            val index = y * width + x
            if (x + 1 < width) result[index] = minOf(result[index], result[index + 1] + 1)
            if (y + 1 < height) result[index] = minOf(result[index], result[index + width] + 1)
        }
        return result
    }

    private fun whitePaint(filter: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG or if (filter) Paint.FILTER_BITMAP_FLAG else 0).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
}
