package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A bounded, reusable pixel-smudge session. It samples only existing layer pigment and never
 * injects the active paint color. One source/output buffer is retained and resized only when a
 * larger dirty region is encountered. */
class RasterBlendSession(
    private val bitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    private val selectionSurface = selectionMask?.takeUnless(Bitmap::isRecycled)?.let { mask ->
        if (mask.width == bitmap.width && mask.height == bitmap.height) mask.copy(Bitmap.Config.ARGB_8888, false)
        else Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
    }
    private var sourcePixels = IntArray(0)
    private var outputPixels = IntArray(0)
    private var selectionPixels = IntArray(0)
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
        val radius = radiusPixels.coerceIn(.5f, min(bitmap.width, bitmap.height).toFloat())
        val dx = currentX - previousX
        val dy = currentY - previousY
        val destination = Rect(
            floor(currentX - radius).toInt().coerceIn(0, bitmap.width),
            floor(currentY - radius).toInt().coerceIn(0, bitmap.height),
            ceil(currentX + radius).toInt().coerceIn(0, bitmap.width),
            ceil(currentY + radius).toInt().coerceIn(0, bitmap.height),
        )
        if (destination.isEmpty) return null
        val source = Rect(
            floor(min(destination.left.toFloat(), destination.left - dx)).toInt().coerceIn(0, bitmap.width),
            floor(min(destination.top.toFloat(), destination.top - dy)).toInt().coerceIn(0, bitmap.height),
            ceil(max(destination.right.toFloat(), destination.right - dx)).toInt().coerceIn(0, bitmap.width),
            ceil(max(destination.bottom.toFloat(), destination.bottom - dy)).toInt().coerceIn(0, bitmap.height),
        )
        if (source.isEmpty) return null
        val sourceCount = source.width() * source.height()
        if (sourcePixels.size < sourceCount) sourcePixels = IntArray(sourceCount)
        val outputCount = destination.width() * destination.height()
        if (outputPixels.size < outputCount) outputPixels = IntArray(outputCount)
        if (selectionSurface != null && selectionPixels.size < outputCount) selectionPixels = IntArray(outputCount)
        bitmap.getPixels(sourcePixels, 0, source.width(), source.left, source.top, source.width(), source.height())
        bitmap.getPixels(outputPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        selectionSurface?.getPixels(selectionPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())

        val effectiveStrength = strength.coerceIn(0f, 1f)
        val hardEdge = hardness.coerceIn(0f, 1f)
        var changed = false
        for (y in destination.top until destination.bottom) {
            for (x in destination.left until destination.right) {
                val nx = (x + .5f - currentX) / radius
                val ny = (y + .5f - currentY) / radius
                val distanceSquared = nx * nx + ny * ny
                if (distanceSquared >= 1f) continue
                val feather = if (distanceSquared <= hardEdge * hardEdge || hardEdge >= .999f) 1f else {
                    val distance = sqrt(distanceSquared)
                    val t = ((1f - distance) / (1f - hardEdge)).coerceIn(0f, 1f)
                    t * t * (3f - 2f * t)
                }
                val outputIndex = (y - destination.top) * destination.width() + x - destination.left
                val selectionAlpha = if (selectionSurface == null) 1f else Color.alpha(selectionPixels[outputIndex]) / 255f
                val amount = effectiveStrength * feather * selectionAlpha
                if (amount <= .001f) continue
                val sampleX = (x - dx).coerceIn(0f, bitmap.width - 1f)
                val sampleY = (y - dy).coerceIn(0f, bitmap.height - 1f)
                val sampled = bilinear(sourcePixels, source, sampleX, sampleY)
                val current = outputPixels[outputIndex]
                val mixed = premultipliedMix(current, sampled, amount)
                if (mixed != current) {
                    outputPixels[outputIndex] = mixed
                    changed = true
                }
            }
        }
        if (!changed) return null
        bitmap.setPixels(outputPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        return destination
    }

    fun restoreOriginal() {
        if (closed || bitmap.isRecycled || original.isRecycled) return
        bitmap.eraseColor(Color.TRANSPARENT)
        android.graphics.Canvas(bitmap).drawBitmap(original, 0f, 0f, null)
    }

    private fun bilinear(pixels: IntArray, bounds: Rect, x: Float, y: Float): Int {
        val x0 = floor(x).toInt().coerceIn(bounds.left, bounds.right - 1)
        val y0 = floor(y).toInt().coerceIn(bounds.top, bounds.bottom - 1)
        val x1 = (x0 + 1).coerceAtMost(bounds.right - 1)
        val y1 = (y0 + 1).coerceAtMost(bounds.bottom - 1)
        val tx = (x - x0).coerceIn(0f, 1f)
        val ty = (y - y0).coerceIn(0f, 1f)
        val row = bounds.width()
        val c00 = pixels[(y0 - bounds.top) * row + x0 - bounds.left]
        val c10 = pixels[(y0 - bounds.top) * row + x1 - bounds.left]
        val c01 = pixels[(y1 - bounds.top) * row + x0 - bounds.left]
        val c11 = pixels[(y1 - bounds.top) * row + x1 - bounds.left]
        val w00 = (1f - tx) * (1f - ty)
        val w10 = tx * (1f - ty)
        val w01 = (1f - tx) * ty
        val w11 = tx * ty
        val a00 = c00 ushr 24
        val a10 = c10 ushr 24
        val a01 = c01 ushr 24
        val a11 = c11 ushr 24
        val alpha = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11
        if (alpha <= .01f) return Color.TRANSPARENT
        val red = ((((c00 ushr 16) and 0xFF) * a00 * w00 + ((c10 ushr 16) and 0xFF) * a10 * w10 + ((c01 ushr 16) and 0xFF) * a01 * w01 + ((c11 ushr 16) and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        val green = ((((c00 ushr 8) and 0xFF) * a00 * w00 + ((c10 ushr 8) and 0xFF) * a10 * w10 + ((c01 ushr 8) and 0xFF) * a01 * w01 + ((c11 ushr 8) and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        val blue = (((c00 and 0xFF) * a00 * w00 + (c10 and 0xFF) * a10 * w10 + (c01 and 0xFF) * a01 * w01 + (c11 and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        return ((alpha + .5f).toInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun premultipliedMix(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return from
        if (t >= 1f) return to
        val fromAlpha = (from ushr 24) / 255f
        val toAlpha = (to ushr 24) / 255f
        val alpha = fromAlpha + (toAlpha - fromAlpha) * t
        if (alpha <= .0001f) return Color.TRANSPARENT
        val fromRed = (from ushr 16) and 0xFF; val toRed = (to ushr 16) and 0xFF
        val fromGreen = (from ushr 8) and 0xFF; val toGreen = (to ushr 8) and 0xFF
        val fromBlue = from and 0xFF; val toBlue = to and 0xFF
        val red = ((fromRed * fromAlpha + (toRed * toAlpha - fromRed * fromAlpha) * t) / alpha + .5f).toInt().coerceIn(0, 255)
        val green = ((fromGreen * fromAlpha + (toGreen * toAlpha - fromGreen * fromAlpha) * t) / alpha + .5f).toInt().coerceIn(0, 255)
        val blue = ((fromBlue * fromAlpha + (toBlue * toAlpha - fromBlue * fromAlpha) * t) / alpha + .5f).toInt().coerceIn(0, 255)
        return ((alpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    override fun close() {
        if (closed) return
        closed = true
        original.recycle()
        selectionSurface?.recycle()
        sourcePixels = IntArray(0)
        outputPixels = IntArray(0)
        selectionPixels = IntArray(0)
    }
}
