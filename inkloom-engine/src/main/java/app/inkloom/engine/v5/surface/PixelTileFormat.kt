package app.inkloom.engine.v5.surface

import kotlin.math.pow

/** Explicit storage/working-space contract for one sparse pixel surface. */
enum class PixelTileFormat(
    val manifestId: String,
    val bytesPerPixel: Int,
    val isHighPrecision: Boolean,
) {
    PREMULTIPLIED_SRGB_8888("argb8888-premultiplied-srgb", 4, false),
    PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16("rgba-f16-premultiplied-linear-extended-srgb", 8, true),
    ;

    companion object {
        fun fromManifestId(value: String): PixelTileFormat = entries.firstOrNull { it.manifestId == value }
            ?: throw IllegalArgumentException("Unsupported pixel format $value")
    }
}

data class LinearRgba(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
)

/** Four IEEE-754 binary16 channels packed in little-channel order R,G,B,A. */
object PackedRgbaF16 {
    fun packPremultiplied(red: Float, green: Float, blue: Float, alpha: Float): Long =
        HalfFloat.pack(red).toLong() or
            (HalfFloat.pack(green).toLong() shl 16) or
            (HalfFloat.pack(blue).toLong() shl 32) or
            (HalfFloat.pack(alpha).toLong() shl 48)

    fun packStraight(color: LinearRgba): Long {
        val alpha = finite(color.alpha).coerceIn(0f, 1f)
        return packPremultiplied(
            finite(color.red) * alpha,
            finite(color.green) * alpha,
            finite(color.blue) * alpha,
            alpha,
        )
    }

    fun premultiplied(value: Long): LinearRgba = LinearRgba(
        premultipliedRed(value),
        premultipliedGreen(value),
        premultipliedBlue(value),
        alpha(value),
    )

    fun premultipliedRed(value: Long): Float = HalfFloat.unpack((value and 0xffffL).toUShort())
    fun premultipliedGreen(value: Long): Float = HalfFloat.unpack((value ushr 16 and 0xffffL).toUShort())
    fun premultipliedBlue(value: Long): Float = HalfFloat.unpack((value ushr 32 and 0xffffL).toUShort())
    fun alpha(value: Long): Float = HalfFloat.unpack((value ushr 48 and 0xffffL).toUShort())

    fun straight(value: Long): LinearRgba {
        val premultiplied = premultiplied(value)
        val alpha = premultiplied.alpha.coerceIn(0f, 1f)
        if (alpha <= 0.000001f) return LinearRgba(0f, 0f, 0f, 0f)
        return LinearRgba(
            premultiplied.red / alpha,
            premultiplied.green / alpha,
            premultiplied.blue / alpha,
            alpha,
        )
    }

    fun fromPremultipliedArgb8888(value: Int): Long {
        val alpha = (value ushr 24 and 0xff) / 255f
        if (alpha <= 0f) return 0L
        val encodedRed = (value ushr 16 and 0xff) / 255f / alpha
        val encodedGreen = (value ushr 8 and 0xff) / 255f / alpha
        val encodedBlue = (value and 0xff) / 255f / alpha
        return packPremultiplied(
            srgbToLinear(encodedRed) * alpha,
            srgbToLinear(encodedGreen) * alpha,
            srgbToLinear(encodedBlue) * alpha,
            alpha,
        )
    }

    fun toPremultipliedArgb8888(value: Long): Int {
        val color = straight(value)
        val alpha = unitByte(color.alpha)
        if (alpha == 0) return 0
        fun premultipliedByte(channel: Float): Int =
            (unitByte(linearToSrgb(channel)) * alpha + 127) / 255
        return (alpha shl 24) or
            (premultipliedByte(color.red) shl 16) or
            (premultipliedByte(color.green) shl 8) or
            premultipliedByte(color.blue)
    }

    fun isTransparent(value: Long): Boolean = alpha(value) <= 0f

    private fun finite(value: Float): Float = if (value.isFinite()) value.coerceIn(-65504f, 65504f) else 0f

    private fun srgbToLinear(value: Float): Float {
        val channel = value.coerceIn(0f, 1f)
        return if (channel <= .04045f) channel / 12.92f else ((channel + .055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Float {
        val channel = value.coerceIn(0f, 1f)
        return if (channel <= .0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - .055f
    }

    private fun unitByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + .5f).toInt()
}

/** Platform-independent IEEE-754 float/binary16 conversion with round-to-nearest-even. */
object HalfFloat {
    fun pack(value: Float): UShort {
        val bits = value.toRawBits()
        val sign = bits ushr 16 and 0x8000
        val exponent = bits ushr 23 and 0xff
        var mantissa = bits and 0x7fffff
        if (exponent == 0xff) {
            return (sign or if (mantissa == 0) 0x7c00 else 0x7e00).toUShort()
        }
        var halfExponent = exponent - 127 + 15
        if (halfExponent >= 0x1f) return (sign or 0x7c00).toUShort()
        if (halfExponent <= 0) {
            if (halfExponent < -10) return sign.toUShort()
            mantissa = mantissa or 0x800000
            val shift = 14 - halfExponent
            val rounded = (mantissa + (1 shl (shift - 1)) - 1 + (mantissa ushr shift and 1)) ushr shift
            return (sign or rounded).toUShort()
        }
        mantissa += 0x0fff + (mantissa ushr 13 and 1)
        if (mantissa and 0x800000 != 0) {
            mantissa = 0
            halfExponent++
            if (halfExponent >= 0x1f) return (sign or 0x7c00).toUShort()
        }
        return (sign or (halfExponent shl 10) or (mantissa ushr 13)).toUShort()
    }

    fun unpack(value: UShort): Float {
        val half = value.toInt()
        val sign = (half and 0x8000) shl 16
        var exponent = half ushr 10 and 0x1f
        var mantissa = half and 0x03ff
        val bits = when (exponent) {
            0 -> {
                if (mantissa == 0) sign else {
                    exponent = 1
                    while (mantissa and 0x0400 == 0) {
                        mantissa = mantissa shl 1
                        exponent--
                    }
                    mantissa = mantissa and 0x03ff
                    sign or ((exponent + 127 - 15) shl 23) or (mantissa shl 13)
                }
            }
            0x1f -> sign or 0x7f800000.toInt() or (mantissa shl 13)
            else -> sign or ((exponent + 127 - 15) shl 23) or (mantissa shl 13)
        }
        return Float.fromBits(bits)
    }
}
