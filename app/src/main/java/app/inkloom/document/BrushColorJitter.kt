package app.inkloom.document

import kotlin.math.max
import kotlin.math.min

object BrushColorJitter {
    fun argb(stroke: InkStroke, point: StrokePoint, stampIndex: Int): Int {
        val source = (point.pigmentArgb ?: stroke.colorArgb).toInt()
        val procreate = stroke.procreateColorDynamics
        if (!hasCspProgram(stroke) && procreate.isDisabled()) return source

        var main = source
        var sub = stroke.secondaryColorArgb.toInt()
        if (stroke.colorJitterPerStrokeEnabled) {
            val hue = signedRandom(stroke.renderSeed, 0, STROKE_HUE_SALT) * kotlin.math.abs(stroke.colorJitterStrokeHueDegrees)
            val saturation = signedRandom(stroke.renderSeed, 0, STROKE_SATURATION_SALT) * kotlin.math.abs(stroke.colorJitterStrokeSaturation) / 100f
            val value = signedRandom(stroke.renderSeed, 0, STROKE_VALUE_SALT) * kotlin.math.abs(stroke.colorJitterStrokeValue) / 100f
            if (CspColorChangeTarget.changesMain(stroke.colorJitterTarget)) main = adjustHsv(main, hue, saturation, value)
            if (CspColorChangeTarget.changesSub(stroke.colorJitterTarget)) sub = adjustHsv(sub, hue, saturation, value)
        }

        if (stroke.colorJitterPerTipEnabled) {
            val hue = stroke.colorJitterHueDegrees * amount(stroke.colorJitterHueDynamics, point, stroke.renderSeed, stampIndex, HUE_SALT)
            val saturation = stroke.colorJitterSaturation / 100f * amount(stroke.colorJitterSaturationDynamics, point, stroke.renderSeed, stampIndex, SATURATION_SALT)
            val value = stroke.colorJitterValue / 100f * amount(stroke.colorJitterValueDynamics, point, stroke.renderSeed, stampIndex, VALUE_SALT)
            if (CspColorChangeTarget.changesMain(stroke.colorJitterTarget)) main = adjustHsv(main, hue, saturation, value)
            if (CspColorChangeTarget.changesSub(stroke.colorJitterTarget)) sub = adjustHsv(sub, hue, saturation, value)
        }

        val strokeSubMix = if (stroke.colorJitterPerStrokeEnabled) {
            stroke.colorJitterStrokeSubColorMix.coerceIn(0f, 1f) * unitRandom(stroke.renderSeed, 0, STROKE_SUB_COLOR_SALT)
        } else 0f
        val tipSubMix = if (stroke.colorJitterPerTipEnabled) {
            stroke.colorJitterSubColorMix.coerceIn(0f, 1f) * parameterAmount(stroke.colorJitterSubColorMixDynamics, point, stroke.renderSeed, stampIndex, SUB_COLOR_SALT)
        } else 0f
        val cspColor = CspPigmentMixer.mixArgb(
            main,
            sub,
            1f - (1f - strokeSubMix) * (1f - tipSubMix),
            stroke.cspPigmentMixingMode,
            stroke.cspBrightnessCorrection,
        )
        return procreateArgb(cspColor, sub, procreate, point, stroke.renderSeed, stampIndex)
    }

    fun hasCspProgram(stroke: InkStroke): Boolean =
        stroke.colorJitterPerTipEnabled && (
            stroke.colorJitterHueDegrees != 0f || stroke.colorJitterSaturation != 0f ||
                stroke.colorJitterValue != 0f || stroke.colorJitterSubColorMix != 0f
            ) || stroke.colorJitterPerStrokeEnabled && (
            stroke.colorJitterStrokeHueDegrees != 0f || stroke.colorJitterStrokeSaturation != 0f ||
                stroke.colorJitterStrokeValue != 0f || stroke.colorJitterStrokeSubColorMix != 0f
            )

    private fun adjustHsv(source: Int, hue: Float, saturation: Float, value: Float): Int {
        if (hue == 0f && saturation == 0f && value == 0f) return source
        val hsv = rgbToHsv(source)
        hsv[0] = wrapHue(hsv[0] + hue)
        hsv[1] = (hsv[1] + saturation).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + value).coerceIn(0f, 1f)
        return hsvToArgb(source ushr 24 and 0xff, hsv[0], hsv[1], hsv[2])
    }

    private fun procreateArgb(source: Int, secondary: Int, dynamics: ProcreateColorDynamics, point: StrokePoint, seed: Int, stampIndex: Int): Int {
        var color = source
        color = jitterProgram(color, secondary, dynamics.strokeHue, dynamics.strokeSaturation, dynamics.strokeLightness, dynamics.strokeDarkness, dynamics.strokeSecondary, seed, 0, STROKE_SALT)
        color = jitterProgram(color, secondary, dynamics.stampHue, dynamics.stampSaturation, dynamics.stampLightness, dynamics.stampDarkness, dynamics.stampSecondary, seed, stampIndex, STAMP_SALT)
        val pressure = point.pressure.coerceIn(0f, 1f)
        color = inputProgram(
            color, secondary,
            dynamics.pressureHue, BrushDynamics.curve(dynamics.pressureHueCurve, pressure),
            dynamics.pressureSaturation, BrushDynamics.curve(dynamics.pressureSaturationCurve, pressure),
            dynamics.pressureBrightness, BrushDynamics.curve(dynamics.pressureBrightnessCurve, pressure),
            dynamics.pressureSecondary, BrushDynamics.curve(dynamics.pressureSecondaryCurve, pressure),
        )
        val tilt = BrushTilt.response(point, dynamics.tiltStart)
        return inputProgram(
            color, secondary,
            dynamics.tiltHue, tilt,
            dynamics.tiltSaturation, tilt,
            dynamics.tiltBrightness, tilt,
            dynamics.tiltSecondary, tilt,
        )
    }

    private fun jitterProgram(source: Int, secondary: Int, hue: Float, saturation: Float, lightness: Float, darkness: Float, secondaryAmount: Float, seed: Int, index: Int, salt: Int): Int {
        if (hue == 0f && saturation == 0f && lightness == 0f && darkness == 0f && secondaryAmount == 0f) return source
        val color = mixArgb(source, secondary, secondaryAmount.coerceIn(0f, 1f) * unitRandom(seed, index, salt + 4))
        val hsv = rgbToHsv(color)
        hsv[0] = wrapHue(hsv[0] + signedRandom(seed, index, salt) * hue.coerceIn(0f, 1f) * 180f)
        hsv[1] = lerp(hsv[1], unitRandom(seed, index, salt + 1), saturation.coerceIn(0f, 1f))
        hsv[2] = lerp(hsv[2], 1f, lightness.coerceIn(0f, 1f) * unitRandom(seed, index, salt + 2))
        hsv[2] = lerp(hsv[2], 0f, darkness.coerceIn(0f, 1f) * unitRandom(seed, index, salt + 3))
        return hsvToArgb(source ushr 24 and 0xff, hsv[0], hsv[1], hsv[2])
    }

    private fun inputProgram(source: Int, secondary: Int, hueAmount: Float, hueInput: Float, saturationAmount: Float, saturationInput: Float, brightnessAmount: Float, brightnessInput: Float, secondaryAmount: Float, secondaryInput: Float): Int {
        var color = mixArgb(source, secondary, directedInput(secondaryAmount, secondaryInput) * kotlin.math.abs(secondaryAmount).coerceIn(0f, 1f))
        val hsv = rgbToHsv(color)
        hsv[0] = wrapHue(hsv[0] + directedInput(hueAmount, hueInput) * kotlin.math.abs(hueAmount).coerceIn(0f, 1f) * 360f)
        hsv[1] = lerp(hsv[1], directedInput(saturationAmount, saturationInput), kotlin.math.abs(saturationAmount).coerceIn(0f, 1f))
        hsv[2] = lerp(hsv[2], 1f - directedInput(brightnessAmount, brightnessInput), kotlin.math.abs(brightnessAmount).coerceIn(0f, 1f))
        return hsvToArgb(source ushr 24 and 0xff, hsv[0], hsv[1], hsv[2])
    }

    private fun directedInput(amount: Float, input: Float): Float = if (amount < 0f) 1f - input.coerceIn(0f, 1f) else input.coerceIn(0f, 1f)
    private fun unitRandom(seed: Int, index: Int, salt: Int): Float = BrushDynamics.deterministicRandom(seed, index.toLong(), salt)
    private fun signedRandom(seed: Int, index: Int, salt: Int): Float = unitRandom(seed, index, salt) * 2f - 1f
    private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)

    private fun mixArgb(first: Int, second: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int) = (lerp((first ushr shift and 0xff).toFloat(), (second ushr shift and 0xff).toFloat(), t) + .5f).toInt().coerceIn(0, 255)
        return (first and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun amount(dynamics: BrushColorDynamics, point: StrokePoint, strokeSeed: Int, stampIndex: Int, salt: Int): Float {
        var value = 1f
        if (dynamics.pressureEnabled) value *= ranged(dynamics.pressureMinimum, BrushDynamics.curve(dynamics.pressureCurve, point.pressure))
        if (dynamics.tiltEnabled) {
            value *= ranged(dynamics.tiltMinimum, BrushTilt.response(point, dynamics.tiltStart))
        }
        if (dynamics.velocityEnabled) {
            val velocityInput = (point.velocity * dynamics.velocityScale.coerceIn(0f, 10f)).coerceIn(0f, 1f)
            value *= ranged(dynamics.velocityMinimum, BrushDynamics.curve(dynamics.velocityCurve, velocityInput))
        }
        if (dynamics.randomEnabled) value *= signedRange(dynamics.randomMinimum, random(strokeSeed, point.timeMs, stampIndex, salt))
        return value.coerceIn(-1.5f, 1.5f)
    }

    private fun parameterAmount(dynamics: BrushParameterDynamics, point: StrokePoint, strokeSeed: Int, stampIndex: Int, salt: Int): Float {
        var value = if (dynamics.pressureEnabled) ranged(dynamics.pressureMinimum, BrushDynamics.curve(dynamics.pressureCurve, point.pressure)) else 1f
        val secondary = dynamics.secondary
        if (secondary.tiltEnabled) value *= ranged(secondary.tiltMinimum, BrushDynamics.curve(secondary.tiltCurve, BrushTilt.response(point, secondary.tiltStart)))
        if (secondary.velocityEnabled) value *= ranged(secondary.velocityMinimum, BrushDynamics.curve(secondary.velocityCurve, (point.velocity * secondary.velocityScale.coerceIn(0f, 10f)).coerceIn(0f, 1f)))
        if (secondary.randomEnabled) value *= ranged(secondary.randomMinimum, random(strokeSeed, point.timeMs, stampIndex, salt))
        return value.coerceIn(0f, 1.5f)
    }

    private fun random(strokeSeed: Int, timeMs: Long, stampIndex: Int, salt: Int): Float =
        BrushDynamics.deterministicRandom(strokeSeed xor (stampIndex * 0x45D9F3B), timeMs, salt)

    private fun ranged(minimum: Float, input: Float): Float = minimum.coerceIn(0f, 1f) + (1f - minimum.coerceIn(0f, 1f)) * input.coerceIn(0f, 1f)
    private fun signedRange(minimum: Float, input: Float): Float = minimum.coerceIn(-1f, 1f) + (1f - minimum.coerceIn(-1f, 1f)) * input.coerceIn(0f, 1f)
    private fun wrapHue(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun rgbToHsv(argb: Int): FloatArray {
        val hsv = HSV_BUFFER.get()!!
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
        hsv[0] = wrapHue(hue)
        hsv[1] = if (maximum == 0f) 0f else delta / maximum
        hsv[2] = maximum
        return hsv
    }

    private fun hsvToArgb(alpha: Int, hue: Float, saturation: Float, value: Float): Int {
        val chroma = value * saturation
        val h = wrapHue(hue) / 60f
        val x = chroma * (1f - kotlin.math.abs(h % 2f - 1f))
        var r1 = 0f
        var g1 = 0f
        var b1 = 0f
        when (h.toInt().coerceIn(0, 5)) {
            0 -> { r1 = chroma; g1 = x }
            1 -> { r1 = x; g1 = chroma }
            2 -> { g1 = chroma; b1 = x }
            3 -> { g1 = x; b1 = chroma }
            4 -> { r1 = x; b1 = chroma }
            else -> { r1 = chroma; b1 = x }
        }
        val m = value - chroma
        fun channel(component: Float) = ((component + m) * 255f).toInt().coerceIn(0, 255)
        return (alpha.coerceIn(0, 255) shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    private const val HUE_SALT = 0x435348
    private const val SATURATION_SALT = 0x435353
    private const val VALUE_SALT = 0x435356
    private const val SUB_COLOR_SALT = 0x435342
    private const val STROKE_HUE_SALT = 0x435448
    private const val STROKE_SATURATION_SALT = 0x435453
    private const val STROKE_VALUE_SALT = 0x435456
    private const val STROKE_SUB_COLOR_SALT = 0x435442
    private const val STAMP_SALT = 0x50435310
    private const val STROKE_SALT = 0x50435320
    private val HSV_BUFFER = ThreadLocal.withInitial { FloatArray(3) }
}
