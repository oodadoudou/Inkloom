package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import app.inkloom.document.LiquifyMode
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Local inverse-mapped Liquify deformation for a persistent raster layer. The session keeps one
 * gesture-start snapshot for Reconstruct/cancel and reuses bounded pixel buffers per frame. */
class RasterLiquifySession(
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
    private var originalPixels = IntArray(0)
    private var selectionPixels = IntArray(0)
    private var closed = false

    fun deform(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        strength: Float,
        hardness: Float,
        mode: LiquifyMode,
    ): Rect? {
        if (closed || bitmap.isRecycled || radiusPixels <= .25f) return null
        val radius = radiusPixels.coerceIn(.5f, min(bitmap.width, bitmap.height).toFloat())
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        if (mode in setOf(LiquifyMode.PUSH, LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT, LiquifyMode.EDGE) && hypot(deltaX, deltaY) <= .01f) return null
        val displacement = when (mode) {
            LiquifyMode.PUSH, LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT, LiquifyMode.EDGE -> hypot(deltaX, deltaY)
            LiquifyMode.CRYSTALS -> radius * .13f * strength.coerceIn(0f, 1f)
            else -> radius * .22f * strength.coerceIn(0f, 1f)
        }
        val pad = ceil(radius + displacement + 2f).toInt()
        val destination = Rect(
            floor(currentX).toInt().coerceIn(0, bitmap.width) - pad,
            floor(currentY).toInt().coerceIn(0, bitmap.height) - pad,
            ceil(currentX).toInt().coerceIn(0, bitmap.width) + pad,
            ceil(currentY).toInt().coerceIn(0, bitmap.height) + pad,
        ).also { it.intersect(0, 0, bitmap.width, bitmap.height) }
        if (destination.isEmpty) return null
        val sourcePad = ceil(displacement + 3f).toInt()
        val source = Rect(
            (destination.left - sourcePad).coerceAtLeast(0),
            (destination.top - sourcePad).coerceAtLeast(0),
            (destination.right + sourcePad).coerceAtMost(bitmap.width),
            (destination.bottom + sourcePad).coerceAtMost(bitmap.height),
        )
        ensureCapacity(source.width() * source.height(), destination.width() * destination.height())
        bitmap.getPixels(sourcePixels, 0, source.width(), source.left, source.top, source.width(), source.height())
        bitmap.getPixels(outputPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        if (mode == LiquifyMode.RECONSTRUCT) original.getPixels(originalPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())
        selectionSurface?.getPixels(selectionPixels, 0, destination.width(), destination.left, destination.top, destination.width(), destination.height())

        val safeStrength = strength.coerceIn(0f, 1f)
        val exponent = 1f + hardness.coerceIn(0f, 1f) * 4f
        val deltaLength = hypot(deltaX, deltaY).coerceAtLeast(.0001f)
        val tangentX = deltaX / deltaLength
        val tangentY = deltaY / deltaLength
        var changed = false
        for (y in destination.top until destination.bottom) for (x in destination.left until destination.right) {
            val dx = x + .5f - currentX
            val dy = y + .5f - currentY
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared >= radius * radius) continue
            val distance = sqrt(distanceSquared)
            val falloff = (1f - distance / radius).coerceIn(0f, 1f).pow(exponent) * safeStrength
            val outputIndex = (y - destination.top) * destination.width() + x - destination.left
            val selectionAlpha = if (selectionSurface == null) 1f else Color.alpha(selectionPixels[outputIndex]) / 255f
            val amount = (falloff * selectionAlpha).coerceIn(0f, 1f)
            if (amount <= .001f) continue
            val current = outputPixels[outputIndex]
            val transformed = if (mode == LiquifyMode.RECONSTRUCT) {
                mixPremultiplied(current, originalPixels[outputIndex], amount)
            } else {
                var sampleX = x + .5f
                var sampleY = y + .5f
                when (mode) {
                    LiquifyMode.PUSH -> { sampleX -= deltaX * falloff; sampleY -= deltaY * falloff }
                    LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                        val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                        sampleX += deltaY * falloff * direction; sampleY -= deltaX * falloff * direction
                    }
                    LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                        val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                        val angle = -direction * falloff * .28f
                        val cosine = cos(angle); val sine = sin(angle)
                        sampleX = currentX + dx * cosine - dy * sine; sampleY = currentY + dx * sine + dy * cosine
                    }
                    LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                        val scale = (if (mode == LiquifyMode.PINCH) 1f - falloff * .12f else 1f + falloff * .12f).coerceAtLeast(.2f)
                        sampleX = currentX + dx / scale; sampleY = currentY + dy / scale
                    }
                    LiquifyMode.CRYSTALS -> {
                        val sector = (Math.PI / 8.0).toFloat(); val shard = (atan2(dy, dx) / sector).toInt() * sector
                        val push = radius * .13f * falloff
                        sampleX -= cos(shard) * push; sampleY -= sin(shard) * push
                    }
                    LiquifyMode.EDGE -> {
                        val perpendicular = dx * -tangentY + dy * tangentX
                        sampleX -= tangentY * perpendicular * falloff; sampleY += tangentX * perpendicular * falloff
                    }
                    LiquifyMode.RECONSTRUCT -> Unit
                }
                val sampled = bilinear(sourcePixels, source, sampleX, sampleY)
                mixPremultiplied(current, sampled, selectionAlpha.coerceIn(0f, 1f))
            }
            if (transformed != current) {
                outputPixels[outputIndex] = transformed
                changed = true
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

    private fun ensureCapacity(sourceCount: Int, outputCount: Int) {
        if (sourcePixels.size < sourceCount) sourcePixels = IntArray(sourceCount)
        if (outputPixels.size < outputCount) outputPixels = IntArray(outputCount)
        if (originalPixels.size < outputCount) originalPixels = IntArray(outputCount)
        if (selectionPixels.size < outputCount) selectionPixels = IntArray(outputCount)
    }

    private fun bilinear(pixels: IntArray, bounds: Rect, x: Float, y: Float): Int {
        val x0 = floor(x).toInt().coerceIn(bounds.left, bounds.right - 1); val y0 = floor(y).toInt().coerceIn(bounds.top, bounds.bottom - 1)
        val x1 = (x0 + 1).coerceAtMost(bounds.right - 1); val y1 = (y0 + 1).coerceAtMost(bounds.bottom - 1)
        val tx = (x - x0).coerceIn(0f, 1f); val ty = (y - y0).coerceIn(0f, 1f)
        val row = bounds.width()
        val c00 = pixels[(y0 - bounds.top) * row + x0 - bounds.left]; val c10 = pixels[(y0 - bounds.top) * row + x1 - bounds.left]
        val c01 = pixels[(y1 - bounds.top) * row + x0 - bounds.left]; val c11 = pixels[(y1 - bounds.top) * row + x1 - bounds.left]
        val w00 = (1f - tx) * (1f - ty); val w10 = tx * (1f - ty); val w01 = (1f - tx) * ty; val w11 = tx * ty
        val a00 = c00 ushr 24; val a10 = c10 ushr 24; val a01 = c01 ushr 24; val a11 = c11 ushr 24
        val alpha = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11
        if (alpha <= .01f) return Color.TRANSPARENT
        val red = ((((c00 ushr 16) and 0xFF) * a00 * w00 + ((c10 ushr 16) and 0xFF) * a10 * w10 + ((c01 ushr 16) and 0xFF) * a01 * w01 + ((c11 ushr 16) and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        val green = ((((c00 ushr 8) and 0xFF) * a00 * w00 + ((c10 ushr 8) and 0xFF) * a10 * w10 + ((c01 ushr 8) and 0xFF) * a01 * w01 + ((c11 ushr 8) and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        val blue = (((c00 and 0xFF) * a00 * w00 + (c10 and 0xFF) * a10 * w10 + (c01 and 0xFF) * a01 * w01 + (c11 and 0xFF) * a11 * w11) / alpha + .5f).toInt().coerceIn(0, 255)
        return ((alpha + .5f).toInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun mixPremultiplied(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f); if (t <= 0f) return from; if (t >= 1f) return to
        val fromAlpha = (from ushr 24) / 255f; val toAlpha = (to ushr 24) / 255f
        val alpha = fromAlpha + (toAlpha - fromAlpha) * t; if (alpha <= .0001f) return Color.TRANSPARENT
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF; val b = (to ushr shift) and 0xFF
            return ((a * fromAlpha + (b * toAlpha - a * fromAlpha) * t) / alpha + .5f).toInt().coerceIn(0, 255)
        }
        return ((alpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    override fun close() {
        if (closed) return
        closed = true
        original.recycle(); selectionSurface?.recycle()
        sourcePixels = IntArray(0); outputPixels = IntArray(0); originalPixels = IntArray(0); selectionPixels = IntArray(0)
    }
}
