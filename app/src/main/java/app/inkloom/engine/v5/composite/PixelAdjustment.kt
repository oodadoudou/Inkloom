package app.inkloom.engine.v5.composite

import app.inkloom.document.GradientColorStop
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Deterministic, allocation-bounded color operations used by adjustment layers and previews. */
object PixelAdjustmentMath {
    fun apply(red: Float, green: Float, blue: Float, adjustment: LayerAdjustment): FloatArray {
        val input = floatArrayOf(finite(red), finite(green), finite(blue))
        val output = when (adjustment.kind) {
            LayerAdjustmentKind.BRIGHTNESS_CONTRAST -> brightnessContrast(input, adjustment)
            LayerAdjustmentKind.HUE_SATURATION -> hueSaturation(input, adjustment)
            LayerAdjustmentKind.LEVELS -> levels(input, adjustment)
            LayerAdjustmentKind.CURVES -> curve(input, adjustment)
            LayerAdjustmentKind.COLOR_BALANCE -> colorBalance(input, adjustment)
            LayerAdjustmentKind.GRADIENT_MAP -> gradientMap(input, adjustment.gradient)
        }
        for (index in output.indices) output[index] = finite(output[index]).coerceIn(0f, 1f)
        return output
    }

    private fun brightnessContrast(rgb: FloatArray, value: LayerAdjustment): FloatArray {
        val brightness = value.brightness.coerceIn(-1f, 1f)
        val contrast = value.contrast.coerceIn(-.99f, .99f)
        val factor = (1f + contrast) / (1f - contrast)
        return FloatArray(3) { ((rgb[it] - .5f) * factor + .5f + brightness) }
    }

    private fun hueSaturation(rgb: FloatArray, value: LayerAdjustment): FloatArray {
        val hsv = rgbToHsv(rgb[0], rgb[1], rgb[2])
        hsv[0] = (hsv[0] + value.hueDegrees).mod(360f)
        hsv[1] = (hsv[1] * (1f + value.saturation.coerceIn(-1f, 1f))).coerceIn(0f, 1f)
        return hsvToRgb(hsv[0], hsv[1], hsv[2])
    }

    private fun levels(rgb: FloatArray, value: LayerAdjustment): FloatArray {
        val black = value.inputBlack.coerceIn(0f, .999f)
        val white = value.inputWhite.coerceIn(black + .001f, 1f)
        val gamma = value.inputGamma.coerceIn(.05f, 10f)
        val outBlack = value.outputBlack.coerceIn(0f, 1f)
        val outWhite = value.outputWhite.coerceIn(outBlack, 1f)
        return FloatArray(3) { index ->
            val normalized = ((rgb[index] - black) / (white - black)).coerceIn(0f, 1f).pow(1f / gamma)
            outBlack + normalized * (outWhite - outBlack)
        }
    }

    private fun curve(rgb: FloatArray, value: LayerAdjustment): FloatArray {
        val points = value.curve.filter { it.input.isFinite() && it.output.isFinite() }
            .sortedBy { it.input }.ifEmpty { listOf(app.inkloom.document.AdjustmentCurvePoint(0f, 0f), app.inkloom.document.AdjustmentCurvePoint(1f, 1f)) }
        fun sample(input: Float): Float {
            val x = input.coerceIn(0f, 1f)
            val upper = points.indexOfFirst { it.input >= x }
            if (upper <= 0) return points.first().output
            if (upper < 0) return points.last().output
            val left = points[upper - 1]; val right = points[upper]
            val amount = if (right.input == left.input) 0f else (x - left.input) / (right.input - left.input)
            return left.output + (right.output - left.output) * amount
        }
        return FloatArray(3) { sample(rgb[it]) }
    }

    private fun colorBalance(rgb: FloatArray, value: LayerAdjustment): FloatArray {
        val luminance = (.2126f * rgb[0] + .7152f * rgb[1] + .0722f * rgb[2]).coerceIn(0f, 1f)
        val shadows = (1f - luminance).let { it * it }
        val highlights = luminance * luminance
        val midtones = (1f - shadows - highlights).coerceIn(0f, 1f)
        val offsets = floatArrayOf(
            value.shadowsRed * shadows + value.midtonesRed * midtones + value.highlightsRed * highlights,
            value.shadowsGreen * shadows + value.midtonesGreen * midtones + value.highlightsGreen * highlights,
            value.shadowsBlue * shadows + value.midtonesBlue * midtones + value.highlightsBlue * highlights,
        )
        return FloatArray(3) { rgb[it] + offsets[it].coerceIn(-1f, 1f) }
    }

    private fun gradientMap(rgb: FloatArray, rawStops: List<GradientColorStop>): FloatArray {
        val stops = rawStops.filter { it.position.isFinite() }.sortedBy { it.position }
            .ifEmpty { listOf(GradientColorStop(0f, 0xFF000000L), GradientColorStop(1f, 0xFFFFFFFFL)) }
        val luminance = (.2126f * rgb[0] + .7152f * rgb[1] + .0722f * rgb[2]).coerceIn(0f, 1f)
        val upper = stops.indexOfFirst { it.position >= luminance }
        val left = when { upper <= 0 -> stops.first(); upper < 0 -> stops.last(); else -> stops[upper - 1] }
        val right = when { upper < 0 -> stops.last(); else -> stops[upper.coerceAtLeast(0)] }
        val amount = if (right.position == left.position) 0f else ((luminance - left.position) / (right.position - left.position)).coerceIn(0f, 1f)
        fun channel(color: Long, shift: Int) = (color ushr shift and 255L).toFloat() / 255f
        return intArrayOf(16, 8, 0).map { shift ->
            val from = channel(left.colorArgb, shift); from + (channel(right.colorArgb, shift) - from) * amount
        }.toFloatArray()
    }

    private fun rgbToHsv(red: Float, green: Float, blue: Float): FloatArray {
        val maximum = maxOf(red, green, blue); val minimum = minOf(red, green, blue); val delta = maximum - minimum
        val hue = when {
            delta == 0f -> 0f
            maximum == red -> 60f * (((green - blue) / delta).mod(6f))
            maximum == green -> 60f * ((blue - red) / delta + 2f)
            else -> 60f * ((red - green) / delta + 4f)
        }
        return floatArrayOf(hue.mod(360f), if (maximum == 0f) 0f else delta / maximum, maximum)
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): FloatArray {
        val chroma = value * saturation
        val section = (hue / 60f).mod(6f)
        val x = chroma * (1f - kotlin.math.abs(section.mod(2f) - 1f))
        val base = when (floor(section).toInt()) {
            0 -> floatArrayOf(chroma, x, 0f); 1 -> floatArrayOf(x, chroma, 0f)
            2 -> floatArrayOf(0f, chroma, x); 3 -> floatArrayOf(0f, x, chroma)
            4 -> floatArrayOf(x, 0f, chroma); else -> floatArrayOf(chroma, 0f, x)
        }
        val match = value - chroma
        return FloatArray(3) { base[it] + match }
    }

    private fun finite(value: Float) = if (value.isFinite()) value else 0f
}

/** Parameter-derived work is prepared once per projection update, not for every pixel. */
internal class PixelAdjustmentKernel(private val value: LayerAdjustment) {
    private val curvePoints = value.curve.filter { it.input.isFinite() && it.output.isFinite() }
        .sortedBy { it.input }.ifEmpty { listOf(app.inkloom.document.AdjustmentCurvePoint(0f, 0f),
            app.inkloom.document.AdjustmentCurvePoint(1f, 1f)) }
    private val gradientStops = value.gradient.filter { it.position.isFinite() }.sortedBy { it.position }
        .ifEmpty { listOf(GradientColorStop(0f, 0xFF000000L), GradientColorStop(1f, 0xFFFFFFFFL)) }

    fun applyInto(inputRed: Float, inputGreen: Float, inputBlue: Float, output: FloatArray) {
        require(output.size >= 3)
        val red = finite(inputRed); val green = finite(inputGreen); val blue = finite(inputBlue)
        when (value.kind) {
            LayerAdjustmentKind.BRIGHTNESS_CONTRAST -> {
                val contrast = value.contrast.coerceIn(-.99f, .99f)
                val factor = (1f + contrast) / (1f - contrast)
                val brightness = value.brightness.coerceIn(-1f, 1f)
                output[0] = (red - .5f) * factor + .5f + brightness
                output[1] = (green - .5f) * factor + .5f + brightness
                output[2] = (blue - .5f) * factor + .5f + brightness
            }
            LayerAdjustmentKind.HUE_SATURATION -> {
                val maximum = maxOf(red, green, blue)
                val minimum = minOf(red, green, blue)
                val delta = maximum - minimum
                val hue = when {
                    delta == 0f -> 0f
                    maximum == red -> 60f * (((green - blue) / delta).mod(6f))
                    maximum == green -> 60f * ((blue - red) / delta + 2f)
                    else -> 60f * ((red - green) / delta + 4f)
                }
                val shiftedHue = (hue.mod(360f) + value.hueDegrees).mod(360f)
                val saturation = ((if (maximum == 0f) 0f else delta / maximum) *
                    (1f + value.saturation.coerceIn(-1f, 1f))).coerceIn(0f, 1f)
                val chroma = maximum * saturation
                val section = (shiftedHue / 60f).mod(6f)
                val x = chroma * (1f - kotlin.math.abs(section.mod(2f) - 1f))
                val match = maximum - chroma
                when (floor(section).toInt()) {
                    0 -> { output[0] = chroma + match; output[1] = x + match; output[2] = match }
                    1 -> { output[0] = x + match; output[1] = chroma + match; output[2] = match }
                    2 -> { output[0] = match; output[1] = chroma + match; output[2] = x + match }
                    3 -> { output[0] = match; output[1] = x + match; output[2] = chroma + match }
                    4 -> { output[0] = x + match; output[1] = match; output[2] = chroma + match }
                    else -> { output[0] = chroma + match; output[1] = match; output[2] = x + match }
                }
            }
            LayerAdjustmentKind.LEVELS -> {
                val black = value.inputBlack.coerceIn(0f, .999f)
                val white = value.inputWhite.coerceIn(black + .001f, 1f)
                val gamma = value.inputGamma.coerceIn(.05f, 10f)
                val outBlack = value.outputBlack.coerceIn(0f, 1f)
                val outWhite = value.outputWhite.coerceIn(outBlack, 1f)
                fun level(channel: Float) = outBlack +
                    ((channel - black) / (white - black)).coerceIn(0f, 1f).pow(1f / gamma) * (outWhite - outBlack)
                output[0] = level(red); output[1] = level(green); output[2] = level(blue)
            }
            LayerAdjustmentKind.CURVES -> {
                fun sample(channel: Float): Float {
                    val x = channel.coerceIn(0f, 1f)
                    val upper = curvePoints.indexOfFirst { it.input >= x }
                    if (upper <= 0) return curvePoints.first().output
                    if (upper < 0) return curvePoints.last().output
                    val left = curvePoints[upper - 1]; val right = curvePoints[upper]
                    val amount = if (right.input == left.input) 0f else (x - left.input) / (right.input - left.input)
                    return left.output + (right.output - left.output) * amount
                }
                output[0] = sample(red); output[1] = sample(green); output[2] = sample(blue)
            }
            LayerAdjustmentKind.COLOR_BALANCE -> {
                val luminance = (.2126f * red + .7152f * green + .0722f * blue).coerceIn(0f, 1f)
                val shadows = (1f - luminance) * (1f - luminance)
                val highlights = luminance * luminance
                val midtones = (1f - shadows - highlights).coerceIn(0f, 1f)
                output[0] = red + (value.shadowsRed * shadows + value.midtonesRed * midtones + value.highlightsRed * highlights).coerceIn(-1f, 1f)
                output[1] = green + (value.shadowsGreen * shadows + value.midtonesGreen * midtones + value.highlightsGreen * highlights).coerceIn(-1f, 1f)
                output[2] = blue + (value.shadowsBlue * shadows + value.midtonesBlue * midtones + value.highlightsBlue * highlights).coerceIn(-1f, 1f)
            }
            LayerAdjustmentKind.GRADIENT_MAP -> {
                val luminance = (.2126f * red + .7152f * green + .0722f * blue).coerceIn(0f, 1f)
                val upper = gradientStops.indexOfFirst { it.position >= luminance }
                val left = when { upper <= 0 -> gradientStops.first(); upper < 0 -> gradientStops.last(); else -> gradientStops[upper - 1] }
                val right = when { upper < 0 -> gradientStops.last(); else -> gradientStops[upper.coerceAtLeast(0)] }
                val amount = if (right.position == left.position) 0f else
                    ((luminance - left.position) / (right.position - left.position)).coerceIn(0f, 1f)
                fun channel(shift: Int): Float {
                    val from = (left.colorArgb ushr shift and 255L).toFloat() / 255f
                    val to = (right.colorArgb ushr shift and 255L).toFloat() / 255f
                    return from + (to - from) * amount
                }
                output[0] = channel(16); output[1] = channel(8); output[2] = channel(0)
            }
        }
        for (index in 0..2) output[index] = finite(output[index]).coerceIn(0f, 1f)
    }

    private fun finite(number: Float) = if (number.isFinite()) number else 0f
}

internal class PixelAdjustmentProjectionCache(
    private val width: Int,
    private val height: Int,
    private val tileSize: Int,
    private val format: PixelTileFormat,
    surfaceId: String,
) {
    private val output = PixelTileSurface(width, height, tileSize, surfaceId, pixelFormat = format)
    private var signatures = emptyMap<PixelTileKey, List<Any?>>()

    fun update(input: PixelTileSurfaceSnapshot, adjustment: LayerAdjustment, opacity: Float,
        mask: MaskTileSurfaceSnapshot?, blendMode: PixelBlendMode = PixelBlendMode.NORMAL): PixelLayerProjectionUpdate {
        require(input.logicalWidth == width && input.logicalHeight == height && input.tileSize == tileSize && input.pixelFormat == format)
        val amount = opacity.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f
        if (amount == 0f) return PixelLayerProjectionUpdate(input, 0, input.tileCount)
        val kernel = PixelAdjustmentKernel(adjustment)
        val next = input.tileKeys.associateWith { key -> listOf(
            input.surfaceId, input.generationAt(key), adjustment.hashCode(), amount.toBits(), blendMode,
            mask?.surfaceId, mask?.revision, mask?.defaultAlpha, mask?.let { maskGenerations(it, bounds(key)) },
        ) }
        val dirty = (signatures.keys + next.keys).filter { signatures[it] != next[it] }
        if (dirty.isNotEmpty()) output.beginWrite("adjustment layer").use { write ->
            dirty.forEach { key ->
                val area = bounds(key); val local = PixelRect(0, 0, area.width, area.height)
                when (format) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> write.editTile(key, local) { pixels, stride ->
                        pixels.fill(0)
                        val blended = FloatArray(4)
                        val changed = FloatArray(3)
                        input.readRgba8Tile(key) { source ->
                            for (y in 0 until area.height) for (x in 0 until area.width) {
                                val index = y * stride + x; val color = source[index]; val alpha = color ushr 24 and 255
                                if (alpha == 0) continue
                                val alphaFloat = alpha / 255f
                                val red = (color ushr 16 and 255) / 255f / alphaFloat
                                val green = (color ushr 8 and 255) / 255f / alphaFloat
                                val blue = (color and 255) / 255f / alphaFloat
                                kernel.applyInto(red, green, blue, changed)
                                val target = blendAdjusted(red, green, blue, changed, blendMode, blended)
                                val weight = amount * maskWeight(mask, area.left + x, area.top + y)
                                fun channel(original: Float, adjusted: Float) = ((original + (adjusted - original) * weight) * alphaFloat * 255f).roundToInt().coerceIn(0, alpha)
                                pixels[index] = (alpha shl 24) or (channel(red, target[0]) shl 16) or
                                    (channel(green, target[1]) shl 8) or channel(blue, target[2])
                            }
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> write.editTileF16(key, local) { pixels, stride ->
                        pixels.fill(0L)
                        val blended = FloatArray(4)
                        val changed = FloatArray(3)
                        input.readF16Tile(key) { source ->
                            for (y in 0 until area.height) for (x in 0 until area.width) {
                                val index = y * stride + x; val color = source[index]; val alpha = PackedRgbaF16.alpha(color)
                                if (alpha <= 0f) continue
                                val red = PackedRgbaF16.premultipliedRed(color) / alpha
                                val green = PackedRgbaF16.premultipliedGreen(color) / alpha
                                val blue = PackedRgbaF16.premultipliedBlue(color) / alpha
                                kernel.applyInto(red, green, blue, changed)
                                val target = blendAdjusted(red, green, blue, changed, blendMode, blended)
                                val weight = amount * maskWeight(mask, area.left + x, area.top + y)
                                fun channel(original: Float, adjusted: Float) = (original + (adjusted - original) * weight) * alpha
                                pixels[index] = PackedRgbaF16.packPremultiplied(channel(red, target[0]), channel(green, target[1]), channel(blue, target[2]), alpha)
                            }
                        }
                    }
                }
            }
            write.commit().close()
        }
        signatures = next
        return PixelLayerProjectionUpdate(output.snapshot(), dirty.size, next.keys.count { it !in dirty })
    }

    private fun bounds(key: PixelTileKey) = PixelRect(key.column * tileSize, key.row * tileSize,
        minOf(width, (key.column + 1) * tileSize), minOf(height, (key.row + 1) * tileSize))
    private fun blendAdjusted(red: Float, green: Float, blue: Float, adjusted: FloatArray,
        mode: PixelBlendMode, scratch: FloatArray): FloatArray {
        if (mode == PixelBlendMode.NORMAL) return adjusted
        PixelBlendMath.compositeInto(adjusted[0], adjusted[1], adjusted[2], 1f,
            red, green, blue, 1f, mode, 1f, scratch, format == PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        return scratch
    }
    private fun maskWeight(mask: MaskTileSurfaceSnapshot?, x: Int, y: Int): Float = mask?.let {
        val mx = ((x + .5f) * it.logicalWidth / width).toInt().coerceIn(0, it.logicalWidth - 1)
        val my = ((y + .5f) * it.logicalHeight / height).toInt().coerceIn(0, it.logicalHeight - 1)
        it.alphaAt(mx, my) / 255f
    } ?: 1f
    private fun maskGenerations(mask: MaskTileSurfaceSnapshot, area: PixelRect): List<Pair<PixelTileKey, Long?>> = buildList {
        fun mx(x: Int) = ((x + .5f) * mask.logicalWidth / width).toInt().coerceIn(0, mask.logicalWidth - 1)
        fun my(y: Int) = ((y + .5f) * mask.logicalHeight / height).toInt().coerceIn(0, mask.logicalHeight - 1)
        for (y in my(area.top) / mask.tileSize..my(area.bottom - 1) / mask.tileSize)
            for (x in mx(area.left) / mask.tileSize..mx(area.right - 1) / mask.tileSize) {
                val key = PixelTileKey(x, y); add(key to mask.generationAt(key))
            }
    }
}
