package app.inkloom.document

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class TextureCoordinate(val x: Int, val y: Int)

object TextureSampling {
    /**
     * Converts an RGBA texture pixel into the effective luminance expected by [mask].
     * CSP material thumbnails frequently carry their density in alpha over black RGB;
     * treating RGB alone turns those textures into a uniform solid mask.
     */
    fun effectiveLuminance(luminance: Float, alpha: Float): Float {
        val ink = (1f - luminance.coerceIn(0f, 1f)) * alpha.coerceIn(0f, 1f)
        return 1f - ink
    }

    fun coordinate(
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
        scalePercent: Float,
        rotationDegrees: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): TextureCoordinate {
        val packed = rawCoordinate(x, y, sourceWidth, sourceHeight, textureWidth, textureHeight, scalePercent, rotationDegrees, offsetX, offsetY)
        val textureX = floor(Float.fromBits((packed ushr 32).toInt()).toDouble()).toInt().floorMod(textureWidth.coerceAtLeast(1))
        val textureY = floor(Float.fromBits(packed.toInt()).toDouble()).toInt().floorMod(textureHeight.coerceAtLeast(1))
        return TextureCoordinate(textureX, textureY)
    }

    /** Samples a repeating grain without allocating a coordinate object for every destination
     * pixel. Classic uses bilinear filtering; Improved uses a bounded Catmull-Rom reconstruction. */
    fun sampleEffectiveLuminance(
        pixels: IntArray,
        textureWidth: Int,
        textureHeight: Int,
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        scalePercent: Float,
        rotationDegrees: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        filteringMode: Int = ProcreateFiltering.NONE,
    ): Float {
        if (pixels.isEmpty() || textureWidth <= 0 || textureHeight <= 0 || pixels.size.toLong() < textureWidth.toLong() * textureHeight) return 1f
        val packed = rawCoordinate(x, y, sourceWidth, sourceHeight, textureWidth, textureHeight, scalePercent, rotationDegrees, offsetX, offsetY)
        val sampleX = Float.fromBits((packed ushr 32).toInt())
        val sampleY = Float.fromBits(packed.toInt())
        return when (ProcreateFiltering.normalize(filteringMode)) {
            ProcreateFiltering.NONE -> sample(pixels, textureWidth, textureHeight, floor(sampleX.toDouble()).toInt(), floor(sampleY.toDouble()).toInt())
            ProcreateFiltering.CLASSIC -> bilinear(pixels, textureWidth, textureHeight, sampleX, sampleY)
            else -> bicubic(pixels, textureWidth, textureHeight, sampleX, sampleY)
        }.coerceIn(0f, 1f)
    }

    private fun rawCoordinate(
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
        scalePercent: Float,
        rotationDegrees: Float,
        offsetX: Float,
        offsetY: Float,
    ): Long {
        val scale = (scalePercent / 100f).takeIf(Float::isFinite)?.coerceIn(.01f, 40f) ?: 1f
        val safeRotation = rotationDegrees.takeIf(Float::isFinite) ?: 0f
        val safeOffsetX = offsetX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val safeOffsetY = offsetY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val radians = Math.toRadians((-safeRotation).toDouble())
        val localX = (x + .5f - sourceWidth / 2f) / scale
        val localY = (y + .5f - sourceHeight / 2f) / scale
        val rotatedX = localX * cos(radians) - localY * sin(radians)
        val rotatedY = localX * sin(radians) + localY * cos(radians)
        val textureX = (rotatedX * textureWidth / sourceWidth.coerceAtLeast(1) + textureWidth / 2f + safeOffsetX * textureWidth).toFloat()
        val textureY = (rotatedY * textureHeight / sourceHeight.coerceAtLeast(1) + textureHeight / 2f + safeOffsetY * textureHeight).toFloat()
        return (textureX.toBits().toLong() shl 32) or (textureY.toBits().toLong() and 0xffffffffL)
    }

    private fun sample(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Float {
        val pixel = pixels[y.floorMod(height) * width + x.floorMod(width)]
        val red = pixel ushr 16 and 0xff
        val green = pixel ushr 8 and 0xff
        val blue = pixel and 0xff
        val alpha = pixel ushr 24 and 0xff
        val luminance = (red * .299f + green * .587f + blue * .114f) / 255f
        return effectiveLuminance(luminance, alpha / 255f)
    }

    private fun bilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Float {
        val x0 = floor(x.toDouble()).toInt(); val y0 = floor(y.toDouble()).toInt()
        val tx = x - floor(x.toDouble()).toFloat(); val ty = y - floor(y.toDouble()).toFloat()
        val top = lerp(sample(pixels, width, height, x0, y0), sample(pixels, width, height, x0 + 1, y0), tx)
        val bottom = lerp(sample(pixels, width, height, x0, y0 + 1), sample(pixels, width, height, x0 + 1, y0 + 1), tx)
        return lerp(top, bottom, ty)
    }

    private fun bicubic(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Float {
        val x0 = floor(x.toDouble()).toInt(); val y0 = floor(y.toDouble()).toInt()
        val tx = x - floor(x.toDouble()).toFloat(); val ty = y - floor(y.toDouble()).toFloat()
        fun row(row: Int): Float = cubic(
            sample(pixels, width, height, x0 - 1, y0 + row),
            sample(pixels, width, height, x0, y0 + row),
            sample(pixels, width, height, x0 + 1, y0 + row),
            sample(pixels, width, height, x0 + 2, y0 + row),
            tx,
        )
        return cubic(row(-1), row(0), row(1), row(2), ty)
    }

    private fun cubic(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return .5f * ((2f * b) + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    fun mask(luminance: Float, brightness: Float, contrast: Float, reversed: Boolean, emphasized: Boolean): Float {
        val brightened = (luminance + brightness.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 1f)
        val contrasted = ((brightened - .5f) * (1f + contrast.coerceIn(-100f, 100f) / 100f) + .5f).coerceIn(0f, 1f)
        val base = if (reversed) contrasted else 1f - contrasted
        return if (emphasized) ((base - .5f) * 1.5f + .5f).coerceIn(0f, 1f) else base
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
