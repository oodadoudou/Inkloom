package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class SelectionInterpolation { NEAREST, BILINEAR, BICUBIC }

/** Filters premultiplied color and selection coverage together, with transparent page borders. */
internal class SelectionPixelSampler(
    private val pixels: PixelTileSurfaceSnapshot,
    private val selection: MaskTileSurfaceSnapshot,
    private val interpolation: SelectionInterpolation,
    private val source: SelectionSamplerSource = SelectionSamplerSource(pixels, selection),
) {
    fun selectedArgb(x: Float, y: Float): Int {
        if (interpolation == SelectionInterpolation.NEAREST) return atArgb(floor(x).toInt(), floor(y).toInt())
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        weights(x, y) { sx, sy, weight ->
            val value = atArgb(sx, sy)
            a += (value ushr 24) * weight
            r += (value ushr 16 and 255) * weight
            g += (value ushr 8 and 255) * weight
            b += (value and 255) * weight
        }
        val alpha = a.roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (r.roundToInt().coerceIn(0, alpha) shl 16) or
            (g.roundToInt().coerceIn(0, alpha) shl 8) or b.roundToInt().coerceIn(0, alpha)
    }

    fun selectedF16(x: Float, y: Float): Long {
        if (interpolation == SelectionInterpolation.NEAREST) {
            val sx = floor(x).toInt(); val sy = floor(y).toInt()
            return scaleF16(source.f16(sx, sy), alpha(sx, sy) / 255f)
        }
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        weights(x, y) { sx, sy, weight ->
            val value = source.f16(sx, sy)
            val coverage = alpha(sx, sy) / 255f * weight
            a += PackedRgbaF16.alpha(value) * coverage
            r += PackedRgbaF16.premultipliedRed(value) * coverage
            g += PackedRgbaF16.premultipliedGreen(value) * coverage
            b += PackedRgbaF16.premultipliedBlue(value) * coverage
        }
        // Extended linear colors may legitimately be negative or exceed alpha. Do not clip HDR.
        return if (a <= 0f) 0L else PackedRgbaF16.packPremultiplied(r, g, b, a.coerceAtMost(1f))
    }

    fun maskAlpha(x: Float, y: Float): Int {
        if (interpolation == SelectionInterpolation.NEAREST) return source.mask(floor(x).toInt(), floor(y).toInt())
        var value = 0f
        weights(x, y) { sx, sy, weight -> value += source.mask(sx, sy) * weight }
        return value.roundToInt().coerceIn(0, 255)
    }

    private fun atArgb(x: Int, y: Int) = scaleArgb(source.argb(x, y), alpha(x, y))
    fun alpha(x: Int, y: Int): Int = if (x !in 0 until pixels.logicalWidth || y !in 0 until pixels.logicalHeight) 0
        else source.mask(((x + .5f) * selection.logicalWidth / pixels.logicalWidth).toInt(),
            ((y + .5f) * selection.logicalHeight / pixels.logicalHeight).toInt())

    private inline fun weights(x: Float, y: Float, consume: (Int, Int, Float) -> Unit) {
        val px = x - .5f; val py = y - .5f
        val left = floor(px).toInt(); val top = floor(py).toInt()
        if (interpolation == SelectionInterpolation.BILINEAR) {
            val dx = px - left; val dy = py - top
            consume(left, top, (1f - dx) * (1f - dy))
            consume(left + 1, top, dx * (1f - dy))
            consume(left, top + 1, (1f - dx) * dy)
            consume(left + 1, top + 1, dx * dy)
        } else {
            for (row in -1..2) for (column in -1..2) {
                val weight = cubic(px - left - column) * cubic(py - top - row)
                if (weight != 0f) consume(left + column, top + row, weight)
            }
        }
    }

    private fun cubic(distance: Float): Float {
        val x = abs(distance)
        return when {
            x < 1f -> 1.5f * x * x * x - 2.5f * x * x + 1f
            x < 2f -> -.5f * x * x * x + 2.5f * x * x - 4f * x + 2f
            else -> 0f
        }
    }

    companion object {
        fun scaleArgb(color: Int, alpha: Int): Int {
            if (alpha == 255) return color
            fun channel(shift: Int) = ((color ushr shift and 255) * alpha + 127) / 255
            return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        fun scaleF16(color: Long, alpha: Float): Long = when {
            alpha <= 0f -> 0L
            alpha >= 1f -> color
            else -> PackedRgbaF16.packPremultiplied(PackedRgbaF16.premultipliedRed(color) * alpha,
                PackedRgbaF16.premultipliedGreen(color) * alpha, PackedRgbaF16.premultipliedBlue(color) * alpha,
                PackedRgbaF16.alpha(color) * alpha)
        }
        fun overF16(source: Long, destination: Long): Long {
            val inverse = 1f - PackedRgbaF16.alpha(source)
            if (inverse <= 0f) return source
            if (inverse >= 1f) return destination
            return PackedRgbaF16.packPremultiplied(
                PackedRgbaF16.premultipliedRed(source) + PackedRgbaF16.premultipliedRed(destination) * inverse,
                PackedRgbaF16.premultipliedGreen(source) + PackedRgbaF16.premultipliedGreen(destination) * inverse,
                PackedRgbaF16.premultipliedBlue(source) + PackedRgbaF16.premultipliedBlue(destination) * inverse,
                PackedRgbaF16.alpha(source) + PackedRgbaF16.alpha(destination) * inverse)
        }
    }
}

/** Bounded occupied-tile copies survive successive previews. The cursor avoids allocation and
 * hashing at every filter tap; source generations remain immutable for the whole gesture. */
internal class SelectionSamplerSource(private val pixels: PixelTileSurfaceSnapshot, private val selection: MaskTileSurfaceSnapshot) {
    private val rgba = HashMap<PixelTileKey, IntArray?>()
    private val half = HashMap<PixelTileKey, LongArray?>()
    private val masks = HashMap<PixelTileKey, ByteArray?>()
    private var pixelColumn = -1; private var pixelRow = -1
    private var maskColumn = -1; private var maskRow = -1
    private var currentRgba: IntArray? = null
    private var currentHalf: LongArray? = null
    private var currentMask: ByteArray? = null
    private fun visitPixel(x: Int, y: Int): Boolean {
        if (x !in 0 until pixels.logicalWidth || y !in 0 until pixels.logicalHeight) return false
        val col = x / pixels.tileSize; val row = y / pixels.tileSize
        if (col != pixelColumn || row != pixelRow) {
            pixelColumn = col; pixelRow = row
            val key = PixelTileKey(col, row)
            if (pixels.pixelFormat.isHighPrecision) {
                if (!half.containsKey(key)) half[key] = pixels.copyTileF16(key)
                currentHalf = half[key]
            } else {
                if (!rgba.containsKey(key)) rgba[key] = pixels.copyTilePixels(key)
                currentRgba = rgba[key]
            }
        }
        return true
    }
    fun argb(x: Int, y: Int): Int = if (!visitPixel(x, y)) 0 else
        currentRgba?.get((y % pixels.tileSize) * pixels.tileSize + x % pixels.tileSize) ?: 0
    fun f16(x: Int, y: Int): Long = if (!visitPixel(x, y)) 0L else
        currentHalf?.get((y % pixels.tileSize) * pixels.tileSize + x % pixels.tileSize) ?: 0L
    fun mask(x: Int, y: Int): Int {
        if (x !in 0 until selection.logicalWidth || y !in 0 until selection.logicalHeight) return 0
        val col = x / selection.tileSize; val row = y / selection.tileSize
        if (col != maskColumn || row != maskRow) {
            maskColumn = col; maskRow = row
            val key = PixelTileKey(col, row)
            if (!masks.containsKey(key)) masks[key] = selection.copyTileAlpha(key)
            currentMask = masks[key]
        }
        return currentMask?.get((y % selection.tileSize) * selection.tileSize + x % selection.tileSize)?.toInt()?.and(255) ?: selection.defaultAlpha
    }
}
