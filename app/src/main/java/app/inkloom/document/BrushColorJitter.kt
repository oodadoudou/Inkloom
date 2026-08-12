package app.inkloom.document

import kotlin.math.max
import kotlin.math.min

object BrushColorJitter {
    fun argb(stroke: InkStroke, point: StrokePoint, stampIndex: Int): Int {
        val source = (point.pigmentArgb ?: stroke.colorArgb).toInt()
        if (stroke.colorJitterHueDegrees == 0f && stroke.colorJitterSaturation == 0f && stroke.colorJitterValue == 0f) return source
        val hsv = rgbToHsv(source)
        hsv[0] = wrapHue(hsv[0] + stroke.colorJitterHueDegrees * amount(stroke.colorJitterHueDynamics, point, stroke.renderSeed, stampIndex, HUE_SALT))
        hsv[1] = (hsv[1] + stroke.colorJitterSaturation / 100f * amount(stroke.colorJitterSaturationDynamics, point, stroke.renderSeed, stampIndex, SATURATION_SALT)).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + stroke.colorJitterValue / 100f * amount(stroke.colorJitterValueDynamics, point, stroke.renderSeed, stampIndex, VALUE_SALT)).coerceIn(0f, 1f)
        return hsvToArgb(source ushr 24 and 0xff, hsv[0], hsv[1], hsv[2])
    }

    fun amount(dynamics: BrushColorDynamics, point: StrokePoint, strokeSeed: Int, stampIndex: Int, salt: Int): Float {
        var value = 1f
        if (dynamics.pressureEnabled) value *= ranged(dynamics.pressureMinimum, BrushDynamics.curve(dynamics.pressureCurve, point.pressure))
        if (dynamics.tiltEnabled) value *= ranged(dynamics.tiltMinimum, (point.tilt / HALF_PI).coerceIn(0f, 1f))
        if (dynamics.velocityEnabled) {
            val velocityInput = (point.velocity * dynamics.velocityScale.coerceIn(0f, 10f)).coerceIn(0f, 1f)
            value *= ranged(dynamics.velocityMinimum, BrushDynamics.curve(dynamics.velocityCurve, velocityInput))
        }
        if (dynamics.randomEnabled) value *= signedRange(dynamics.randomMinimum, random(strokeSeed, point.timeMs, stampIndex, salt))
        return value.coerceIn(-1.5f, 1.5f)
    }

    private fun random(strokeSeed: Int, timeMs: Long, stampIndex: Int, salt: Int): Float =
        BrushDynamics.deterministicRandom(strokeSeed xor (stampIndex * 0x45D9F3B), timeMs, salt)

    private fun ranged(minimum: Float, input: Float): Float = minimum.coerceIn(0f, 1f) + (1f - minimum.coerceIn(0f, 1f)) * input.coerceIn(0f, 1f)
    private fun signedRange(minimum: Float, input: Float): Float = minimum.coerceIn(-1f, 1f) + (1f - minimum.coerceIn(-1f, 1f)) * input.coerceIn(0f, 1f)
    private fun wrapHue(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun rgbToHsv(argb: Int): FloatArray {
        val red = (argb ushr 16 and 0xff) / 255f
        val green = (argb ushr 8 and 0xff) / 255f
        val blue = (argb and 0xff) / 255f
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        val delta = maximum - minimum
        val hue = when {
            delta == 0f -> 0f
            maximum == red -> 60f * (((green - blue) / delta) % 6f)
            maximum == green -> 60f * ((blue - red) / delta + 2f)
            else -> 60f * ((red - green) / delta + 4f)
        }
        return floatArrayOf(wrapHue(hue), if (maximum == 0f) 0f else delta / maximum, maximum)
    }

    private fun hsvToArgb(alpha: Int, hue: Float, saturation: Float, value: Float): Int {
        val chroma = value * saturation
        val h = wrapHue(hue) / 60f
        val x = chroma * (1f - kotlin.math.abs(h % 2f - 1f))
        val (r1, g1, b1) = when (h.toInt().coerceIn(0, 5)) {
            0 -> Triple(chroma, x, 0f); 1 -> Triple(x, chroma, 0f); 2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma); 4 -> Triple(x, 0f, chroma); else -> Triple(chroma, 0f, x)
        }
        val m = value - chroma
        fun channel(component: Float) = ((component + m) * 255f).toInt().coerceIn(0, 255)
        return (alpha.coerceIn(0, 255) shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    private const val HUE_SALT = 0x435348
    private const val SATURATION_SALT = 0x435353
    private const val VALUE_SALT = 0x435356
    private const val HALF_PI = (Math.PI / 2.0).toFloat()
}
