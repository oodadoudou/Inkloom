package app.inkloom.engine.v3.composite

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Engine blend modes. Alpha compositing is deliberately handled separately by [PixelBlendMath]. */
enum class PixelBlendMode {
    NORMAL,
    DARKEN,
    MULTIPLY,
    COLOR_BURN,
    LINEAR_BURN,
    LIGHTEN,
    SCREEN,
    COLOR_DODGE,
    ADD,
    OVERLAY,
    SOFT_LIGHT,
    HARD_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY,
}

/** Premultiplied color in the surface's declared working space. */
data class PremultipliedRgba(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
)

/**
 * Clean-room blend implementation following the source/backdrop separation used by Skia and the
 * W3C compositing model. Inputs and output are premultiplied; blend functions receive straight RGB.
 */
object PixelBlendMath {
    private const val ALPHA_EPSILON = 1e-7f

    fun composite(
        source: PremultipliedRgba,
        destination: PremultipliedRgba,
        mode: PixelBlendMode,
        opacity: Float = 1f,
    ): PremultipliedRgba {
        val output = FloatArray(4)
        compositeInto(
            source.red,
            source.green,
            source.blue,
            source.alpha,
            destination.red,
            destination.green,
            destination.blue,
            destination.alpha,
            mode,
            opacity,
            output,
        )
        return PremultipliedRgba(output[0], output[1], output[2], output[3])
    }

    /** Allocation-free hot-path entry point. [output] must contain at least four floats. */
    internal fun compositeInto(
        sourceRedPremultiplied: Float,
        sourceGreenPremultiplied: Float,
        sourceBluePremultiplied: Float,
        sourceAlpha: Float,
        destinationRedPremultiplied: Float,
        destinationGreenPremultiplied: Float,
        destinationBluePremultiplied: Float,
        destinationAlpha: Float,
        mode: PixelBlendMode,
        opacity: Float,
        output: FloatArray,
    ) {
        require(output.size >= 4) { "Composite output needs four channels" }
        val layerOpacity = finite(opacity).coerceIn(0f, 1f)
        val rawSourceAlpha = finite(sourceAlpha).coerceIn(0f, 1f)
        val sourceScale = layerOpacity
        val sourceA = rawSourceAlpha * sourceScale
        val destinationA = finite(destinationAlpha).coerceIn(0f, 1f)
        if (sourceA <= ALPHA_EPSILON) {
            output[0] = finite(destinationRedPremultiplied)
            output[1] = finite(destinationGreenPremultiplied)
            output[2] = finite(destinationBluePremultiplied)
            output[3] = destinationA
            return
        }

        val sourceRed = straight(sourceRedPremultiplied, rawSourceAlpha)
        val sourceGreen = straight(sourceGreenPremultiplied, rawSourceAlpha)
        val sourceBlue = straight(sourceBluePremultiplied, rawSourceAlpha)
        val destinationRed = straight(destinationRedPremultiplied, destinationA)
        val destinationGreen = straight(destinationGreenPremultiplied, destinationA)
        val destinationBlue = straight(destinationBluePremultiplied, destinationA)
        blendInto(
            destinationRed,
            destinationGreen,
            destinationBlue,
            sourceRed,
            sourceGreen,
            sourceBlue,
            mode,
            output,
        )

        val sourceRedEffective = finite(sourceRedPremultiplied) * sourceScale
        val sourceGreenEffective = finite(sourceGreenPremultiplied) * sourceScale
        val sourceBlueEffective = finite(sourceBluePremultiplied) * sourceScale
        val sourceOnly = 1f - destinationA
        val destinationOnly = 1f - sourceA
        val overlap = sourceA * destinationA
        output[0] = finite(sourceRedEffective * sourceOnly +
            finite(destinationRedPremultiplied) * destinationOnly + output[0] * overlap)
        output[1] = finite(sourceGreenEffective * sourceOnly +
            finite(destinationGreenPremultiplied) * destinationOnly + output[1] * overlap)
        output[2] = finite(sourceBlueEffective * sourceOnly +
            finite(destinationBluePremultiplied) * destinationOnly + output[2] * overlap)
        output[3] = (sourceA + destinationA * (1f - sourceA)).coerceIn(0f, 1f)
    }

    private fun blendInto(
        backdropRed: Float,
        backdropGreen: Float,
        backdropBlue: Float,
        sourceRed: Float,
        sourceGreen: Float,
        sourceBlue: Float,
        mode: PixelBlendMode,
        output: FloatArray,
    ) {
        if (mode in NON_SEPARABLE_MODES) {
            val result = nonSeparable(
                Rgb(backdropRed, backdropGreen, backdropBlue),
                Rgb(sourceRed, sourceGreen, sourceBlue),
                mode,
            )
            output[0] = result.red
            output[1] = result.green
            output[2] = result.blue
            return
        }
        output[0] = separable(backdropRed, sourceRed, mode)
        output[1] = separable(backdropGreen, sourceGreen, mode)
        output[2] = separable(backdropBlue, sourceBlue, mode)
    }

    private fun separable(backdrop: Float, source: Float, mode: PixelBlendMode): Float = when (mode) {
        PixelBlendMode.NORMAL -> source
        PixelBlendMode.DARKEN -> min(backdrop, source)
        PixelBlendMode.MULTIPLY -> backdrop * source
        PixelBlendMode.COLOR_BURN -> colorBurn(backdrop, source)
        PixelBlendMode.LINEAR_BURN -> backdrop + source - 1f
        PixelBlendMode.LIGHTEN -> max(backdrop, source)
        PixelBlendMode.SCREEN -> backdrop + source - backdrop * source
        PixelBlendMode.COLOR_DODGE -> colorDodge(backdrop, source)
        PixelBlendMode.ADD -> backdrop + source
        PixelBlendMode.OVERLAY -> hardLight(source, backdrop)
        PixelBlendMode.SOFT_LIGHT -> softLight(backdrop, source)
        PixelBlendMode.HARD_LIGHT -> hardLight(backdrop, source)
        PixelBlendMode.DIFFERENCE -> abs(backdrop - source)
        PixelBlendMode.EXCLUSION -> backdrop + source - 2f * backdrop * source
        PixelBlendMode.HUE,
        PixelBlendMode.SATURATION,
        PixelBlendMode.COLOR,
        PixelBlendMode.LUMINOSITY,
        -> error("Non-separable mode routed to separable function")
    }

    private fun hardLight(backdrop: Float, source: Float): Float =
        if (source <= .5f) 2f * backdrop * source
        else backdrop + 2f * source - 1f - 2f * backdrop * source

    private fun softLight(backdropValue: Float, sourceValue: Float): Float {
        val backdrop = backdropValue.coerceIn(0f, 1f)
        val source = sourceValue.coerceIn(0f, 1f)
        return if (source <= .5f) {
            backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
        } else {
            val d = if (backdrop <= .25f) {
                ((16f * backdrop - 12f) * backdrop + 4f) * backdrop
            } else {
                sqrt(backdrop)
            }
            backdrop + (2f * source - 1f) * (d - backdrop)
        }
    }

    private fun colorDodge(backdropValue: Float, sourceValue: Float): Float {
        val backdrop = backdropValue.coerceIn(0f, 1f)
        val source = sourceValue.coerceIn(0f, 1f)
        return if (source >= 1f) 1f else min(1f, backdrop / (1f - source))
    }

    private fun colorBurn(backdropValue: Float, sourceValue: Float): Float {
        val backdrop = backdropValue.coerceIn(0f, 1f)
        val source = sourceValue.coerceIn(0f, 1f)
        return if (source <= 0f) 0f else 1f - min(1f, (1f - backdrop) / source)
    }

    private fun nonSeparable(backdropValue: Rgb, sourceValue: Rgb, mode: PixelBlendMode): Rgb {
        val backdrop = backdropValue.clamped()
        val source = sourceValue.clamped()
        return when (mode) {
            PixelBlendMode.HUE -> setLum(setSat(source, saturation(backdrop)), luminosity(backdrop))
            PixelBlendMode.SATURATION -> setLum(setSat(backdrop, saturation(source)), luminosity(backdrop))
            PixelBlendMode.COLOR -> setLum(source, luminosity(backdrop))
            PixelBlendMode.LUMINOSITY -> setLum(backdrop, luminosity(source))
            else -> error("Separable mode routed to non-separable function")
        }
    }

    private fun luminosity(color: Rgb): Float = .3f * color.red + .59f * color.green + .11f * color.blue

    private fun saturation(color: Rgb): Float =
        max(color.red, max(color.green, color.blue)) - min(color.red, min(color.green, color.blue))

    private fun setLum(color: Rgb, targetLuminosity: Float): Rgb {
        val delta = targetLuminosity - luminosity(color)
        return clipColor(Rgb(color.red + delta, color.green + delta, color.blue + delta))
    }

    private fun clipColor(color: Rgb): Rgb {
        val luminosity = luminosity(color)
        val minimum = min(color.red, min(color.green, color.blue))
        val maximum = max(color.red, max(color.green, color.blue))
        var result = color
        if (minimum < 0f && luminosity > minimum) {
            result = Rgb(
                luminosity + (result.red - luminosity) * luminosity / (luminosity - minimum),
                luminosity + (result.green - luminosity) * luminosity / (luminosity - minimum),
                luminosity + (result.blue - luminosity) * luminosity / (luminosity - minimum),
            )
        }
        if (maximum > 1f && maximum > luminosity) {
            result = Rgb(
                luminosity + (result.red - luminosity) * (1f - luminosity) / (maximum - luminosity),
                luminosity + (result.green - luminosity) * (1f - luminosity) / (maximum - luminosity),
                luminosity + (result.blue - luminosity) * (1f - luminosity) / (maximum - luminosity),
            )
        }
        return result.clamped()
    }

    private fun setSat(color: Rgb, targetSaturation: Float): Rgb {
        val channels = floatArrayOf(color.red, color.green, color.blue)
        val order = intArrayOf(0, 1, 2).sortedBy { channels[it] }
        val minimum = order[0]
        val middle = order[1]
        val maximum = order[2]
        if (channels[maximum] > channels[minimum]) {
            channels[middle] = (channels[middle] - channels[minimum]) * targetSaturation /
                (channels[maximum] - channels[minimum])
            channels[maximum] = targetSaturation
        } else {
            channels[middle] = 0f
            channels[maximum] = 0f
        }
        channels[minimum] = 0f
        return Rgb(channels[0], channels[1], channels[2])
    }

    private fun straight(premultiplied: Float, alpha: Float): Float =
        if (alpha <= ALPHA_EPSILON) 0f else finite(premultiplied) / alpha

    private fun finite(value: Float): Float = if (value.isFinite()) value else 0f

    private data class Rgb(val red: Float, val green: Float, val blue: Float) {
        fun clamped(): Rgb = Rgb(red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f))
    }

    private val NON_SEPARABLE_MODES = setOf(
        PixelBlendMode.HUE,
        PixelBlendMode.SATURATION,
        PixelBlendMode.COLOR,
        PixelBlendMode.LUMINOSITY,
    )
}
