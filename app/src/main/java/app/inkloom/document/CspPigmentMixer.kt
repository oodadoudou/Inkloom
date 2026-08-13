package app.inkloom.document

import kotlin.math.cbrt
import kotlin.math.pow

/** Deterministic pigment interpolation shared by live raster painting, replay and export. */
object CspPigmentMixer {
    fun mixArgb(existing: Int, fresh: Int, freshRatio: Float, mode: Int, brightnessCorrection: Int): Int {
        val t = freshRatio.coerceIn(0f, 1f)
        if (t <= 0f) return existing
        if (t >= 1f) return fresh
        val alpha = lerp(existing ushr 24, fresh ushr 24, t)
        if (CspPigmentMixingMode.normalize(mode) == CspPigmentMixingMode.STANDARD) {
            return (alpha shl 24) or
                (lerp(existing ushr 16 and 0xFF, fresh ushr 16 and 0xFF, t) shl 16) or
                (lerp(existing ushr 8 and 0xFF, fresh ushr 8 and 0xFF, t) shl 8) or
                lerp(existing and 0xFF, fresh and 0xFF, t)
        }
        val first = toOklab(existing)
        val second = toOklab(fresh)
        val correction = ((CspBrightnessCorrection.normalize(brightnessCorrection).takeIf { it > 0 }
            ?: CspBrightnessCorrection.NEUTRAL) - CspBrightnessCorrection.NEUTRAL) * .028f
        val chromaBalance = 4f * t * (1f - t)
        val mixed = Oklab(
            l = (first.l + (second.l - first.l) * t + correction * chromaBalance).coerceIn(0f, 1f),
            a = first.a + (second.a - first.a) * t,
            b = first.b + (second.b - first.b) * t,
        )
        return (alpha shl 24) or (fromOklab(mixed) and 0x00FFFFFF)
    }

    private fun toOklab(color: Int): Oklab {
        val red = srgbToLinear(color ushr 16 and 0xFF)
        val green = srgbToLinear(color ushr 8 and 0xFF)
        val blue = srgbToLinear(color and 0xFF)
        val l = cbrt((.4122214708f * red + .5363325363f * green + .0514459929f * blue).toDouble()).toFloat()
        val m = cbrt((.2119034982f * red + .6806995451f * green + .1073969566f * blue).toDouble()).toFloat()
        val s = cbrt((.0883024619f * red + .2817188376f * green + .6299787005f * blue).toDouble()).toFloat()
        return Oklab(
            l = .2104542553f * l + .793617785f * m - .0040720468f * s,
            a = 1.9779984951f * l - 2.428592205f * m + .4505937099f * s,
            b = .0259040371f * l + .7827717662f * m - .808675766f * s,
        )
    }

    private fun fromOklab(color: Oklab): Int {
        val l = (color.l + .3963377774f * color.a + .2158037573f * color.b).let { it * it * it }
        val m = (color.l - .1055613458f * color.a - .0638541728f * color.b).let { it * it * it }
        val s = (color.l - .0894841775f * color.a - 1.291485548f * color.b).let { it * it * it }
        val red = 4.0767416621f * l - 3.3077115913f * m + .2309699292f * s
        val green = -1.2684380046f * l + 2.6097574011f * m - .3413193965f * s
        val blue = -.0041960863f * l - .7034186147f * m + 1.707614701f * s
        return (linearToSrgb(red) shl 16) or (linearToSrgb(green) shl 8) or linearToSrgb(blue)
    }

    private fun srgbToLinear(channel: Int): Float {
        val value = channel.coerceIn(0, 255) / 255f
        return if (value <= .04045f) value / 12.92f else ((value + .055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(channel: Float): Int {
        val value = channel.coerceIn(0f, 1f)
        val encoded = if (value <= .0031308f) 12.92f * value else 1.055f * value.pow(1f / 2.4f) - .055f
        return (encoded * 255f + .5f).toInt().coerceIn(0, 255)
    }

    private fun lerp(first: Int, second: Int, amount: Float): Int =
        (first + (second - first) * amount + .5f).toInt().coerceIn(0, 255)

    private data class Oklab(val l: Float, val a: Float, val b: Float)
}
