package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Incremental destination-pigment phase for a wet brush. The active shape/paint deposit remains
 * in the shared brush rasterizer; this session moves and softens only existing layer pigment in
 * bounded dirty regions, so Pull and Blur are visible before pen-up. */
class RasterWetPaintSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private val ownsSelectionSurface = selectionSurface != null && selectionSurface !== selectionMask
    private val originalTiles = LinkedHashMap<Int, OriginalTile>()
    private var sourcePixels = IntArray(0)
    private var outputPixels = IntArray(0)
    private var selectionPixels = IntArray(0)
    private var closed = false

    fun advance(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        pull: Float,
        blur: Float,
        hardness: Float,
    ): Rect? {
        if (closed || bitmap.isRecycled || radiusPixels <= .25f) return null
        val dx = currentX - previousX
        val dy = currentY - previousY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val radius = radiusPixels.coerceIn(.5f, min(bitmap.width, bitmap.height).toFloat())
        val steps = ceil(distance / (radius * .45f).coerceAtLeast(1f)).toInt().coerceIn(1, MAX_DABS_PER_FRAME)
        var dirty: Rect? = null
        for (step in 1..steps) {
            val t = step / steps.toFloat()
            val centerX = previousX + dx * t
            val centerY = previousY + dy * t
            val changed = applyDab(centerX, centerY, dx, dy, radius, pull, blur, hardness) ?: continue
            if (dirty == null) dirty = Rect(changed) else dirty.union(changed)
        }
        return dirty
    }

    private fun applyDab(
        centerX: Float,
        centerY: Float,
        segmentX: Float,
        segmentY: Float,
        radius: Float,
        pullValue: Float,
        blurValue: Float,
        hardnessValue: Float,
    ): Rect? {
        val pull = pullValue.coerceIn(0f, 1f)
        val blur = blurValue.coerceIn(0f, 1f)
        if (pull <= .001f && blur <= .001f) return null
        val length = hypot(segmentX.toDouble(), segmentY.toDouble()).toFloat().coerceAtLeast(.001f)
        val directionX = segmentX / length
        val directionY = segmentY / length
        val dragDistance = radius * pull * .8f
        val blurRadius = radius * blur * .35f
        val destination = Rect(
            floor(centerX - radius).toInt().coerceIn(0, bitmap.width),
            floor(centerY - radius).toInt().coerceIn(0, bitmap.height),
            ceil(centerX + radius).toInt().coerceIn(0, bitmap.width),
            ceil(centerY + radius).toInt().coerceIn(0, bitmap.height),
        )
        if (destination.isEmpty) return null
        val source = Rect(
            floor(destination.left - directionX * dragDistance - blurRadius - 1f).toInt().coerceIn(0, bitmap.width),
            floor(destination.top - directionY * dragDistance - blurRadius - 1f).toInt().coerceIn(0, bitmap.height),
            ceil(destination.right - directionX * dragDistance + blurRadius + 1f).toInt().coerceIn(0, bitmap.width),
            ceil(destination.bottom - directionY * dragDistance + blurRadius + 1f).toInt().coerceIn(0, bitmap.height),
        )
        if (source.isEmpty) return null
        val sourceCount = source.width() * source.height()
        val outputCount = destination.width() * destination.height()
        if (sourcePixels.size < sourceCount) sourcePixels = IntArray(sourceCount)
        if (outputPixels.size < outputCount) outputPixels = IntArray(outputCount)
        if (selectionSurface != null && selectionPixels.size < outputCount) selectionPixels = IntArray(outputCount)
        captureOriginalTiles(destination)
        bitmap.getPixels(sourcePixels, 0, source.width(), source.left, source.top, source.width(), source.height())
        bitmap.getPixels(outputPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        selectionSurface?.getPixels(selectionPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        val hardEdge = hardnessValue.coerceIn(0f, 1f)
        var changed = false
        for (y in destination.top until destination.bottom) for (x in destination.left until destination.right) {
            val nx = (x + .5f - centerX) / radius
            val ny = (y + .5f - centerY) / radius
            val distanceSquared = nx * nx + ny * ny
            if (distanceSquared >= 1f) continue
            val feather = if (distanceSquared <= hardEdge * hardEdge || hardEdge >= .999f) 1f else {
                val edge = ((1f - sqrt(distanceSquared)) / (1f - hardEdge)).coerceIn(0f, 1f)
                edge * edge * (3f - 2f * edge)
            }
            val outputIndex = (y - destination.top) * destination.width() + x - destination.left
            val selectionAlpha = if (selectionSurface == null) 1f else Color.alpha(selectionPixels[outputIndex]) / 255f
            val amount = max(pull * .82f, blur * .55f) * feather * selectionAlpha
            if (amount <= .001f) continue
            val sampleX = x + .5f - directionX * dragDistance
            val sampleY = y + .5f - directionY * dragDistance
            val sampled = if (blurRadius <= .01f) bilinear(sourcePixels, source, sampleX, sampleY) else averageFive(sourcePixels, source, sampleX, sampleY, blurRadius)
            val current = outputPixels[outputIndex]
            val mixed = premultipliedMix(current, sampled, amount)
            if (mixed != current) { outputPixels[outputIndex] = mixed; changed = true }
        }
        if (!changed) return null
        bitmap.setPixels(outputPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        return destination
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled) return
        originalTiles.values.forEach { tile ->
            bitmap.setPixels(tile.pixels, 0, tile.width, tile.left, tile.top, tile.width, tile.height)
        }
    }

    private fun captureOriginalTiles(destination: Rect) {
        val firstTileX = destination.left / CANCEL_TILE_SIZE
        val lastTileX = (destination.right - 1) / CANCEL_TILE_SIZE
        val firstTileY = destination.top / CANCEL_TILE_SIZE
        val lastTileY = (destination.bottom - 1) / CANCEL_TILE_SIZE
        val columnCount = ceil(bitmap.width / CANCEL_TILE_SIZE.toDouble()).toInt()
        for (tileY in firstTileY..lastTileY) for (tileX in firstTileX..lastTileX) {
            val key = tileY * columnCount + tileX
            if (key in originalTiles) continue
            val left = tileX * CANCEL_TILE_SIZE
            val top = tileY * CANCEL_TILE_SIZE
            val width = min(CANCEL_TILE_SIZE, bitmap.width - left)
            val height = min(CANCEL_TILE_SIZE, bitmap.height - top)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, left, top, width, height)
            originalTiles[key] = OriginalTile(left, top, width, height, pixels)
        }
    }

    private fun averageFive(pixels: IntArray, bounds: Rect, x: Float, y: Float, radius: Float): Int {
        val first = bilinear(pixels, bounds, x, y)
        val second = bilinear(pixels, bounds, x - radius, y)
        val third = bilinear(pixels, bounds, x + radius, y)
        val fourth = bilinear(pixels, bounds, x, y - radius)
        val fifth = bilinear(pixels, bounds, x, y + radius)
        var alpha = 0f; var red = 0f; var green = 0f; var blue = 0f
        fun accumulate(color: Int) {
            val a = (color ushr 24) / 255f
            alpha += a; red += ((color ushr 16) and 0xFF) * a; green += ((color ushr 8) and 0xFF) * a; blue += (color and 0xFF) * a
        }
        accumulate(first); accumulate(second); accumulate(third); accumulate(fourth); accumulate(fifth)
        alpha /= 5f
        if (alpha <= .0001f) return Color.TRANSPARENT
        val premultipliedScale = .2f
        return ((alpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or
            ((red * premultipliedScale / alpha + .5f).toInt().coerceIn(0, 255) shl 16) or
            ((green * premultipliedScale / alpha + .5f).toInt().coerceIn(0, 255) shl 8) or
            (blue * premultipliedScale / alpha + .5f).toInt().coerceIn(0, 255)
    }

    private fun bilinear(pixels: IntArray, bounds: Rect, x: Float, y: Float): Int {
        val safeX = x.coerceIn(bounds.left.toFloat(), (bounds.right - 1).toFloat())
        val safeY = y.coerceIn(bounds.top.toFloat(), (bounds.bottom - 1).toFloat())
        val x0 = floor(safeX).toInt(); val y0 = floor(safeY).toInt()
        val x1 = (x0 + 1).coerceAtMost(bounds.right - 1); val y1 = (y0 + 1).coerceAtMost(bounds.bottom - 1)
        val tx = safeX - x0; val ty = safeY - y0
        val row = bounds.width()
        val c00 = pixels[(y0 - bounds.top) * row + x0 - bounds.left]
        val c10 = pixels[(y0 - bounds.top) * row + x1 - bounds.left]
        val c01 = pixels[(y1 - bounds.top) * row + x0 - bounds.left]
        val c11 = pixels[(y1 - bounds.top) * row + x1 - bounds.left]
        val w00 = (1f - tx) * (1f - ty); val w10 = tx * (1f - ty); val w01 = (1f - tx) * ty; val w11 = tx * ty
        var alpha = 0f; var red = 0f; var green = 0f; var blue = 0f
        fun accumulate(color: Int, weight: Float) {
            val a = (color ushr 24) / 255f * weight
            alpha += a; red += ((color ushr 16) and 0xFF) * a; green += ((color ushr 8) and 0xFF) * a; blue += (color and 0xFF) * a
        }
        accumulate(c00, w00); accumulate(c10, w10); accumulate(c01, w01); accumulate(c11, w11)
        if (alpha <= .0001f) return Color.TRANSPARENT
        return ((alpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or
            ((red / alpha + .5f).toInt().coerceIn(0, 255) shl 16) or ((green / alpha + .5f).toInt().coerceIn(0, 255) shl 8) or
            (blue / alpha + .5f).toInt().coerceIn(0, 255)
    }

    private fun premultipliedMix(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val fromAlpha = (from ushr 24) / 255f; val toAlpha = (to ushr 24) / 255f
        val alpha = fromAlpha + (toAlpha - fromAlpha) * t
        if (alpha <= .0001f) return Color.TRANSPARENT
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF; val b = (to shr shift) and 0xFF
            return ((a * fromAlpha + (b * toAlpha - a * fromAlpha) * t) / alpha + .5f).toInt().coerceIn(0, 255)
        }
        return ((alpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    override fun close() {
        if (closed) return
        closed = true
        if (ownsSelectionSurface) selectionSurface?.recycle()
        originalTiles.clear()
        sourcePixels = IntArray(0); outputPixels = IntArray(0); selectionPixels = IntArray(0)
    }

    private data class OriginalTile(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )

    private companion object {
        const val MAX_DABS_PER_FRAME = 32
        const val CANCEL_TILE_SIZE = 128
    }
}
