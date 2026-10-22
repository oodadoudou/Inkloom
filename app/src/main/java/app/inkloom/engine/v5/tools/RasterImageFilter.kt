package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bounded, tile-aware image filters. The input is immutable and every output tile reads its
 * neighbors from the same input generation, so tile boundaries cannot become filter boundaries.
 */
internal object RasterImageFilter {
    sealed interface Effect {
        val sigma: Float

        data class ConvertToDrawingColor(val colorArgb: Int) : Effect { override val sigma = 1f }
        data object BrightnessToOpacity : Effect { override val sigma = 1f }
        data class GaussianBlur(override val sigma: Float) : Effect
        data class UnsharpMask(
            override val sigma: Float,
            val amount: Float,
            val threshold: Float = 0f,
        ) : Effect
        /** Median RGB filtering is deliberately restricted to a small halo. */
        data class Denoise(override val sigma: Float) : Effect
        /** Removes isolated low-alpha specks while preserving connected ink. */
        data class CleanLineArt(override val sigma: Float) : Effect
        /** Grows or shrinks transparent ink edges with a bounded neighborhood. */
        data class AdjustLineWidth(override val sigma: Float, val grow: Boolean) : Effect
        /** A bounded horizontal wave; unlike a mesh transform it does not alter layer structure. */
        data class Wave(override val sigma: Float, val wavelength: Float) : Effect
        data class MotionBlur(override val sigma: Float,val angle: Float=0f) : Effect
        data class RadialBlur(override val sigma: Float,val centerX: Float=.5f,val centerY: Float=.5f,val zoom:Boolean=false) : Effect
        data class LensBlur(override val sigma: Float) : Effect
        data class Mosaic(override val sigma: Float) : Effect
        data class Noise(override val sigma: Float,val seed:Int=1,val monochrome:Boolean=true) : Effect
        data class ChromaticAberration(override val sigma: Float,val angle:Float=0f) : Effect
        /** Fixed-strength 3x3 sharpening (Sharpen / Sharpen More); it has no user parameters. */
        data class Sharpen(val strong: Boolean) : Effect { override val sigma = 1f }
        /** CSP Blur / Strong blur / Smoothing with fixed kernels. */
        data class FixedBlur(val kind: FixedBlurKind) : Effect { override val sigma = 1f }
        /** CSP Render > Perlin noise: opaque grey noise replaces the selected pixels. */
        data class PerlinNoise(val scale: Float, val amplitude: Float, val attenuation: Float, val octaves: Int, val seed: Int) : Effect {
            override val sigma = 1f
            init {
                require(scale in 2f..2000f && amplitude in 0f..1f && attenuation in 0f..1f && octaves in 1..8) { "Perlin noise settings are out of range" }
            }
        }
        data class Distort(val kind: DistortKind, val parameters: DistortParameters) : Effect { override val sigma = 1f }
        /** CSP Edit > Tonal correction applied to pixels, with exactly the math of an adjustment layer. */
        data class Tonal(val adjustment: app.inkloom.document.LayerAdjustment) : Effect { override val sigma = 1f }
    }

    fun apply(
        source: PixelTileSurfaceSnapshot,
        effect: Effect,
        selection: MaskTileSurfaceSnapshot? = null,
        cancelled: () -> Boolean = { false },
    ): PixelTileSurface {
        if (effect is Effect.ConvertToDrawingColor || effect == Effect.BrightnessToOpacity)
            return RasterColorConversion.apply(source, effect, selection, cancelled)
        if (effect is Effect.Tonal) return RasterTonalCorrection.apply(source, effect.adjustment, selection)
        if (effect is Effect.FixedBlur || effect is Effect.PerlinNoise || effect is Effect.Distort)
            return RasterDistortFilter.apply(source, effect, selection, cancelled)
        if(source.pixelFormat.isHighPrecision || effect is Effect.MotionBlur || effect is Effect.RadialBlur ||
            effect is Effect.LensBlur || effect is Effect.Mosaic || effect is Effect.Noise || effect is Effect.ChromaticAberration ||
            effect is Effect.Sharpen)
            return FloatRasterImageFilter.apply(source,effect,selection,cancelled)
        require(selection == null || (selection.logicalWidth > 0 && selection.logicalHeight > 0)) {
            "Selection mask has invalid dimensions"
        }
        require(effect.sigma.isFinite() && effect.sigma in 1f..20f) {
            "Filter strength must be between 1 and 20 pixels"
        }
        if (effect is Effect.Wave) require(effect.wavelength.isFinite() && effect.wavelength in 16f..512f) {
            "Wave length must be between 16 and 512 pixels"
        }
        if (effect is Effect.UnsharpMask) require(
            effect.amount.isFinite() && effect.amount in 0f..4f &&
                effect.threshold.isFinite() && effect.threshold in 0f..1f,
        ) { "Unsharp strength or threshold is outside the supported range" }

        // Three separable box passes approximate a Gaussian while keeping work linear in image
        // area even at the largest supported radius. Their total support is the per-tile halo.
        val radii = boxRadii(effect.sigma)
        val radius = when (effect) {
            is Effect.GaussianBlur, is Effect.UnsharpMask -> radii.sum()
            is Effect.Denoise -> effect.sigma.roundToInt().coerceIn(1, 3)
            is Effect.CleanLineArt -> 1
            is Effect.AdjustLineWidth -> effect.sigma.roundToInt().coerceIn(1, 3)
            is Effect.Wave -> effect.sigma.toInt() + 2
            else -> error("Advanced filter must use float pipeline")
        }

        val result = PixelTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize)
        val candidateTiles = linkedSetOf<PixelTileKey>()
        val columns = (source.logicalWidth + source.tileSize - 1) / source.tileSize
        val rows = (source.logicalHeight + source.tileSize - 1) / source.tileSize
        source.tileInfo.forEach { tile ->
            val left = (tile.key.column * source.tileSize - radius).coerceAtLeast(0) / source.tileSize
            val top = (tile.key.row * source.tileSize - radius).coerceAtLeast(0) / source.tileSize
            val right = ((tile.key.column + 1) * source.tileSize + radius - 1) / source.tileSize
            val bottom = ((tile.key.row + 1) * source.tileSize + radius - 1) / source.tileSize
            for (row in top..bottom.coerceAtMost(rows - 1)) {
                for (column in left..right.coerceAtMost(columns - 1)) {
                    candidateTiles += PixelTileKey(column, row)
                }
            }
        }
        val write = result.beginWrite("image filter")
        try {
            candidateTiles.forEach { key ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                val tile = PixelRect(
                    key.column * source.tileSize,
                    key.row * source.tileSize,
                    minOf((key.column + 1) * source.tileSize, source.logicalWidth),
                    minOf((key.row + 1) * source.tileSize, source.logicalHeight),
                )
                val paddedWidth = tile.width + radius * 2
                val paddedHeight = tile.height + radius * 2
                val input = IntArray(paddedWidth * paddedHeight)
                val copied = PixelRect(
                    (tile.left - radius).coerceAtLeast(0),
                    (tile.top - radius).coerceAtLeast(0),
                    (tile.right + radius).coerceAtMost(source.logicalWidth),
                    (tile.bottom + radius).coerceAtMost(source.logicalHeight),
                )
                val copiedPixels = source.copyPremultipliedPixels(copied)
                for (y in 0 until copied.height) {
                    copiedPixels.copyInto(
                        input,
                        (y + copied.top - tile.top + radius) * paddedWidth + copied.left - tile.left + radius,
                        y * copied.width,
                        (y + 1) * copied.width,
                    )
                }
                val blurredEffect = effect is Effect.GaussianBlur || effect is Effect.UnsharpMask
                val current = if (blurredEffect) FloatArray(input.size * 4) else FloatArray(0)
                if (blurredEffect) {
                    input.forEachIndexed { index, color ->
                        val base = index * 4
                        current[base] = (color ushr 24 and 255).toFloat()
                        current[base + 1] = (color ushr 16 and 255).toFloat()
                        current[base + 2] = (color ushr 8 and 255).toFloat()
                        current[base + 3] = (color and 255).toFloat()
                    }
                    val alternate = FloatArray(current.size)
                    for (boxRadius in radii) {
                        boxPass(current, alternate, paddedWidth, paddedHeight, boxRadius, horizontal = true)
                        boxPass(alternate, current, paddedWidth, paddedHeight, boxRadius, horizontal = false)
                    }
                }
                val local = PixelRect(0, 0, tile.width, tile.height)
                write.editTile(key, local) { pixels, stride ->
                    val old = IntArray(4)
                    val changed = IntArray(4)
                    val medianValues = IntArray((radius * 2 + 1) * (radius * 2 + 1))
                    for (y in 0 until tile.height) for (x in 0 until tile.width) {
                        val original = input[(y + radius) * paddedWidth + x + radius]
                        val maskX = selection?.let { ((tile.left + x).toLong() * it.logicalWidth / source.logicalWidth).toInt() }
                        val maskY = selection?.let { ((tile.top + y).toLong() * it.logicalHeight / source.logicalHeight).toInt() }
                        val weight = (if (selection != null) selection.alphaAt(checkNotNull(maskX), checkNotNull(maskY)) else 255) / 255f
                        if (weight == 0f) {
                            pixels[y * stride + x] = original
                            continue
                        }
                        val blurredBase = ((y + radius) * paddedWidth + x + radius) * 4
                        old[0] = original ushr 24 and 255
                        old[1] = original ushr 16 and 255
                        old[2] = original ushr 8 and 255
                        old[3] = original and 255
                        when (effect) {
                            is Effect.GaussianBlur -> for (channel in 0..3) {
                                changed[channel] = (old[channel] + (current[blurredBase + channel] - old[channel]) * weight)
                                    .roundToInt().coerceIn(0, 255)
                            }
                            is Effect.UnsharpMask -> {
                                changed[0] = old[0]
                                val edge = maxOf(
                                    kotlin.math.abs(old[1] - current[blurredBase + 1]),
                                    kotlin.math.abs(old[2] - current[blurredBase + 2]),
                                    kotlin.math.abs(old[3] - current[blurredBase + 3]),
                                ) / 255f
                                for (channel in 1..3) {
                                    changed[channel] = if (edge < effect.threshold) old[channel] else
                                        (old[channel] + (old[channel] - current[blurredBase + channel]) * effect.amount * weight)
                                            .roundToInt().coerceIn(0, old[0])
                                }
                            }
                            is Effect.Denoise -> {
                                for (channel in 0..3) {
                                    var count = 0
                                    for (dy in -radius..radius) for (dx in -radius..radius) {
                                        val yy = (y + radius + dy).coerceIn(0, paddedHeight - 1)
                                        val xx = (x + radius + dx).coerceIn(0, paddedWidth - 1)
                                        val color = input[yy * paddedWidth + xx]
                                        medianValues[count++] = color ushr (24 - channel * 8) and 255
                                    }
                                    medianValues.sort(0, count)
                                    changed[channel] = (old[channel] + (medianValues[count / 2] - old[channel]) * weight).roundToInt()
                                }
                            }
                            is Effect.CleanLineArt -> {
                                var neighbors = 0
                                for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) {
                                    val yy = y + radius + dy
                                    val xx = x + radius + dx
                                    if (yy in 0 until paddedHeight && xx in 0 until paddedWidth &&
                                        input[yy * paddedWidth + xx] ushr 24 >= 32) neighbors++
                                }
                                val removeAtOrBelow = (effect.sigma.roundToInt() - 1).coerceIn(0, 3)
                                val keep = if (neighbors <= removeAtOrBelow) (1f - weight) else 1f
                                for (channel in 0..3) changed[channel] = (old[channel] * keep).roundToInt()
                            }
                            is Effect.AdjustLineWidth -> {
                                var selected = original
                                var selectedAlpha = original ushr 24
                                for (dy in -radius..radius) for (dx in -radius..radius) {
                                    val yy = (y + radius + dy).coerceIn(0, paddedHeight - 1)
                                    val xx = (x + radius + dx).coerceIn(0, paddedWidth - 1)
                                    val candidate = input[yy * paddedWidth + xx]
                                    val alpha = candidate ushr 24
                                    if ((effect.grow && alpha > selectedAlpha) || (!effect.grow && alpha < selectedAlpha)) {
                                        selected = candidate
                                        selectedAlpha = alpha
                                    }
                                }
                                for (channel in 0..3) {
                                    val sample = selected ushr (24 - channel * 8) and 255
                                    changed[channel] = (old[channel] + (sample - old[channel]) * weight).roundToInt()
                                }
                            }
                            is Effect.Wave -> {
                                val offset = (kotlin.math.sin((tile.top + y) * 2.0 * Math.PI / effect.wavelength) * effect.sigma).roundToInt()
                                val sampleX = (x + radius + offset).coerceIn(0, paddedWidth - 1)
                                val shifted = input[(y + radius) * paddedWidth + sampleX]
                                for (channel in 0..3) {
                                    val sample = shifted ushr (24 - channel * 8) and 255
                                    changed[channel] = (old[channel] + (sample - old[channel]) * weight).roundToInt()
                                }
                            }
                            else -> error("Advanced filter must use float pipeline")
                        }
                        for (channel in 1..3) changed[channel] = changed[channel].coerceAtMost(changed[0])
                        pixels[y * stride + x] = (changed[0] shl 24) or (changed[1] shl 16) or
                            (changed[2] shl 8) or changed[3]
                    }
                }
            }
            write.commit().close()
        } catch (error: Throwable) {
            write.cancel()
            throw error
        }
        return result
    }

    private fun boxRadii(sigma: Float): IntArray {
        val ideal = sqrt(4f * sigma * sigma + 1f)
        val lowerWidth = floor(ideal).toInt().let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(1)
        val upperWidth = lowerWidth + 2
        val lowerCount = ((12f * sigma * sigma - 3f * lowerWidth * lowerWidth -
            12f * lowerWidth - 9f) / (-4f * lowerWidth - 4f)).roundToInt().coerceIn(0, 3)
        return IntArray(3) { if (it < lowerCount) lowerWidth / 2 else upperWidth / 2 }
    }

    private fun boxPass(source: FloatArray, target: FloatArray, width: Int, height: Int,
        radius: Int, horizontal: Boolean) {
        val extent = if (horizontal) width else height
        val lines = if (horizontal) height else width
        val step = if (horizontal) 1 else width
        val divisor = (radius * 2 + 1).toFloat()
        for (line in 0 until lines) {
            val start = if (horizontal) line * width else line
            val sums = FloatArray(4)
            for (offset in 0..radius.coerceAtMost(extent - 1)) {
                val base = (start + offset * step) * 4
                for (channel in 0..3) sums[channel] += source[base + channel]
            }
            for (position in 0 until extent) {
                val base = (start + position * step) * 4
                for (channel in 0..3) target[base + channel] = sums[channel] / divisor
                val leaving = position - radius
                if (leaving >= 0) {
                    val oldBase = (start + leaving * step) * 4
                    for (channel in 0..3) sums[channel] -= source[oldBase + channel]
                }
                val entering = position + radius + 1
                if (entering < extent) {
                    val newBase = (start + entering * step) * 4
                    for (channel in 0..3) sums[channel] += source[newBase + channel]
                }
            }
        }
    }
}
