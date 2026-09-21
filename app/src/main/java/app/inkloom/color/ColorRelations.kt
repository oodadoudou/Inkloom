package app.inkloom.color

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ColorHarmony(val offsets: List<Float>) {
    COMPLEMENTARY(listOf(0f, 180f)),
    SPLIT_COMPLEMENTARY(listOf(0f, 150f, 210f)),
    ANALOGOUS(listOf(-30f, 0f, 30f)),
    TRIADIC(listOf(0f, 120f, 240f)),
    TETRADIC(listOf(0f, 60f, 180f, 240f)),
}

object ColorRelations {
    fun harmony(hue: Float, saturation: Float, value: Float, mode: ColorHarmony): List<Int> =
        mode.offsets.map { hsv(hue + it, saturation, value) }

    /** Four-corner interpolation in linear RGB prevents the muddy midpoint of direct sRGB averaging. */
    fun cornerMix(topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int, column: Int, row: Int, size: Int): Int {
        require(size >= 2 && column in 0 until size && row in 0 until size)
        val x = column / (size - 1f)
        val y = row / (size - 1f)
        fun channel(shift: Int): Int {
            fun linear(color: Int): Double {
                val value = (color ushr shift and 0xff) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            val top = linear(topLeft) * (1 - x) + linear(topRight) * x
            val bottom = linear(bottomLeft) * (1 - x) + linear(bottomRight) * x
            val mixed = top * (1 - y) + bottom * y
            val srgb = if (mixed <= .0031308) mixed * 12.92 else 1.055 * mixed.pow(1.0 / 2.4) - .055
            return (srgb * 255).roundToInt().coerceIn(0, 255)
        }
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val normalized = ((hue % 360f) + 360f) % 360f / 60f
        val chroma = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
        val secondary = chroma * (1f - abs(normalized % 2f - 1f))
        val offset = value.coerceIn(0f, 1f) - chroma
        val (red, green, blue) = when (floor(normalized).toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        fun byte(channel: Float) = ((channel + offset) * 255f).roundToInt().coerceIn(0, 255)
        return (0xff shl 24) or (byte(red) shl 16) or (byte(green) shl 8) or byte(blue)
    }
}
