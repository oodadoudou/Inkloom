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
    ): TextureCoordinate {
        val scale = (scalePercent / 100f).coerceIn(.01f, 40f)
        val radians = Math.toRadians((-rotationDegrees).toDouble())
        val localX = (x + .5f - sourceWidth / 2f) / scale
        val localY = (y + .5f - sourceHeight / 2f) / scale
        val rotatedX = localX * cos(radians) - localY * sin(radians)
        val rotatedY = localX * sin(radians) + localY * cos(radians)
        val textureX = floor(rotatedX * textureWidth / sourceWidth.coerceAtLeast(1) + textureWidth / 2f).toInt().floorMod(textureWidth.coerceAtLeast(1))
        val textureY = floor(rotatedY * textureHeight / sourceHeight.coerceAtLeast(1) + textureHeight / 2f).toInt().floorMod(textureHeight.coerceAtLeast(1))
        return TextureCoordinate(textureX, textureY)
    }

    fun mask(luminance: Float, brightness: Float, contrast: Float, reversed: Boolean, emphasized: Boolean): Float {
        val brightened = (luminance + brightness.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 1f)
        val contrasted = ((brightened - .5f) * (1f + contrast.coerceIn(-100f, 100f) / 100f) + .5f).coerceIn(0f, 1f)
        val base = if (reversed) contrasted else 1f - contrasted
        return if (emphasized) ((base - .5f) * 1.5f + .5f).coerceIn(0f, 1f) else base
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
