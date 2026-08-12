package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Continuous capsule eraser for a raster layer. Each input segment touches one bounded pixel
 * patch, preserving partial alpha for soft/low-opacity erasers and clipping to the active selection. */
class RasterEraserSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask.copy(Bitmap.Config.ARGB_8888, false)
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private var pixels = IntArray(0)
    private var selectionPixels = IntArray(0)
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
            floor(minOf(startX, endX) - radius - 1f).toInt().coerceIn(0, bitmap.width),
            floor(minOf(startY, endY) - radius - 1f).toInt().coerceIn(0, bitmap.height),
            ceil(maxOf(startX, endX) + radius + 1f).toInt().coerceIn(0, bitmap.width),
            ceil(maxOf(startY, endY) + radius + 1f).toInt().coerceIn(0, bitmap.height),
        )
        if (bounds.isEmpty) return null
        val count = bounds.width() * bounds.height()
        if (pixels.size < count) pixels = IntArray(count)
        if (selectionPixels.size < count) selectionPixels = IntArray(count)
        bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
        selectionSurface?.getPixels(selectionPixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
        val segmentX = endX - startX; val segmentY = endY - startY
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
        val hardRadius = radius * hardness.coerceIn(0f, 1f)
        val maximumOpacity = opacity.coerceIn(0f, 1f)
        var changed = false
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            val projection = if (segmentLengthSquared <= .0001f) 0f else (((x + .5f - startX) * segmentX + (y + .5f - startY) * segmentY) / segmentLengthSquared).coerceIn(0f, 1f)
            val nearestX = startX + segmentX * projection; val nearestY = startY + segmentY * projection
            val dx = x + .5f - nearestX; val dy = y + .5f - nearestY
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared >= radius * radius) continue
            val distance = sqrt(distanceSquared)
            val coverage = if (distance <= hardRadius || hardRadius >= radius - .01f) 1f else {
                val t = ((radius - distance) / (radius - hardRadius).coerceAtLeast(.01f)).coerceIn(0f, 1f)
                t * t * (3f - 2f * t)
            }
            val index = (y - bounds.top) * bounds.width() + x - bounds.left
            val selectionAlpha = if (selectionSurface == null) 1f else Color.alpha(selectionPixels[index]) / 255f
            val removal = maximumOpacity * coverage * selectionAlpha
            if (removal <= .001f) continue
            val current = pixels[index]
            val alpha = current ushr 24
            if (alpha == 0) continue
            val nextAlpha = (alpha * (1f - removal) + .5f).toInt().coerceIn(0, 255)
            val next = (current and 0x00FFFFFF) or (nextAlpha shl 24)
            if (next != current) { pixels[index] = next; changed = true }
        }
        if (!changed) return null
        bitmap.setPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
        return bounds
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled || original.isRecycled) return
        bitmap.eraseColor(Color.TRANSPARENT)
        android.graphics.Canvas(bitmap).drawBitmap(original, 0f, 0f, null)
    }

    override fun close() {
        if (closed) return
        closed = true
        original.recycle(); selectionSurface?.recycle()
        pixels = IntArray(0); selectionPixels = IntArray(0)
    }
}
