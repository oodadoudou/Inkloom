package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pixel-local line-art operations; no page bitmap, halo or change to the unselected payload. */
internal object RasterColorConversion {
    fun apply(
        source: PixelTileSurfaceSnapshot,
        effect: RasterImageFilter.Effect,
        selection: MaskTileSurfaceSnapshot?,
        cancelled: () -> Boolean,
    ): PixelTileSurface {
        require(effect is RasterImageFilter.Effect.ConvertToDrawingColor || effect == RasterImageFilter.Effect.BrightnessToOpacity)
        val result = PixelTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize, pixelFormat = source.pixelFormat)
        val color = (effect as? RasterImageFilter.Effect.ConvertToDrawingColor)?.colorArgb ?: 0
        val target = PackedRgbaF16.premultiplied(PackedRgbaF16.fromPremultipliedArgb8888(color or 0xFF000000.toInt()))
        result.beginWrite("line art color conversion").use { write ->
            source.tileInfo.forEach { info ->
                if (cancelled()) throw java.util.concurrent.CancellationException()
                val left = info.key.column * source.tileSize
                val top = info.key.row * source.tileSize
                val w = minOf(source.tileSize, source.logicalWidth - left)
                val h = minOf(source.tileSize, source.logicalHeight - top)
                fun weight(x: Int, y: Int): Float = selection?.alphaAt(
                    (x.toLong() * selection.logicalWidth / source.logicalWidth).toInt(),
                    (y.toLong() * selection.logicalHeight / source.logicalHeight).toInt(),
                )?.div(255f) ?: 1f
                val local = PixelRect(0, 0, w, h)
                if (source.pixelFormat.isHighPrecision) write.editTileF16(info.key, local) { pixels, stride ->
                    for (y in 0 until h) {
                        if (cancelled()) throw java.util.concurrent.CancellationException()
                        for (x in 0 until w) {
                            val px = left + x; val py = top + y
                            val amount = weight(px, py)
                            pixels[y * stride + x] = if (amount == 0f) source.packedF16At(px, py) else {
                                val old = source.linearPixelAt(px, py)
                                val changed = if (effect is RasterImageFilter.Effect.ConvertToDrawingColor)
                                    LinearRgba(target.red * old.alpha, target.green * old.alpha, target.blue * old.alpha, old.alpha)
                                else {
                                    fun encoded(v: Float) = if (old.alpha <= 0f) 0f else linearToSrgb(v / old.alpha)
                                    val brightness = (.299f * encoded(old.red) + .587f * encoded(old.green) + .114f * encoded(old.blue)).coerceIn(0f, 1f)
                                    LinearRgba(0f, 0f, 0f, old.alpha * (1f - brightness))
                                }
                                PackedRgbaF16.packPremultiplied(
                                    old.red + (changed.red - old.red) * amount,
                                    old.green + (changed.green - old.green) * amount,
                                    old.blue + (changed.blue - old.blue) * amount,
                                    old.alpha + (changed.alpha - old.alpha) * amount,
                                )
                            }
                        }
                    }
                } else write.editTile(info.key, local) { pixels, stride ->
                    for (y in 0 until h) {
                        if (cancelled()) throw java.util.concurrent.CancellationException()
                        for (x in 0 until w) {
                            val px = left + x; val py = top + y
                            val old = source.pixelAt(px, py)
                            val alpha = old ushr 24
                            val amount = weight(px, py)
                            val changed = if (effect is RasterImageFilter.Effect.ConvertToDrawingColor) {
                                fun channel(shift: Int) = ((color ushr shift and 255) * alpha + 127) / 255
                                (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                            } else {
                                val brightness = if (alpha == 0) 0f else
                                    (.299f * (old ushr 16 and 255) + .587f * (old ushr 8 and 255) + .114f * (old and 255)) / alpha
                                ((alpha * (1f - brightness.coerceIn(0f, 1f))).roundToInt() shl 24)
                            }
                            fun mix(shift: Int) = ((old ushr shift and 255) +
                                ((changed ushr shift and 255) - (old ushr shift and 255)) * amount).roundToInt().coerceIn(0, 255)
                            pixels[y * stride + x] = if (amount == 0f) old else
                                (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
                        }
                    }
                }
            }
            write.commit().close()
        }
        return result
    }

    private fun linearToSrgb(value: Float): Float = if (value <= .0031308f) value * 12.92f
        else 1.055f * value.pow(1f / 2.4f) - .055f
}
