package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.composite.ExtendedSrgb
import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** Colour statistics in CIE Lab: per-channel mean and standard deviation, weighted by alpha. */
data class ColorStatistics(val meanL: Float, val meanA: Float, val meanB: Float, val spreadL: Float, val spreadA: Float, val spreadB: Float) {
    companion object {
        /** Null when the surface has no visible pixels to learn from. */
        fun of(surface: PixelTileSurfaceSnapshot, step: Int = 2): ColorStatistics? {
            var weight = 0.0; val sum = DoubleArray(3); val squares = DoubleArray(3); val lab = FloatArray(3)
            surface.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
                val left = info.key.column * surface.tileSize; val top = info.key.row * surface.tileSize
                for (y in top until min(surface.logicalHeight, top + surface.tileSize) step step)
                    for (x in left until min(surface.logicalWidth, left + surface.tileSize) step step) {
                        val p = surface.linearPixelAt(x, y)
                        if (p.alpha <= 0f) continue
                        Lab.fromLinear(p.red / p.alpha, p.green / p.alpha, p.blue / p.alpha, lab)
                        for (c in 0..2) { sum[c] += lab[c] * p.alpha; squares[c] += lab[c] * lab[c] * p.alpha }
                        weight += p.alpha
                    }
            }
            if (weight <= 0.0) return null
            val mean = DoubleArray(3) { sum[it] / weight }
            val spread = DoubleArray(3) { sqrt(max(0.0, squares[it] / weight - mean[it] * mean[it])) }
            return ColorStatistics(mean[0].toFloat(), mean[1].toFloat(), mean[2].toFloat(), spread[0].toFloat(), spread[1].toFloat(), spread[2].toFloat())
        }
    }
}

/** sRGB (linear light, D65) to CIE Lab and back. */
internal object Lab {
    private fun f(t: Float) = if (t > 216f / 24389f) cbrt(t) else (24389f / 27f * t + 16f) / 116f
    private fun inverse(t: Float) = if (t * t * t > 216f / 24389f) t * t * t else (116f * t - 16f) / (24389f / 27f)
    fun fromLinear(r: Float, g: Float, b: Float, out: FloatArray) {
        val x = (.4124564f * r + .3575761f * g + .1804375f * b) / .95047f
        val y = .2126729f * r + .7151522f * g + .0721750f * b
        val z = (.0193339f * r + .1191920f * g + .9503041f * b) / 1.08883f
        val fx = f(x); val fy = f(y); val fz = f(z)
        out[0] = 116f * fy - 16f; out[1] = 500f * (fx - fy); out[2] = 200f * (fy - fz)
    }
    fun toLinear(l: Float, a: Float, b: Float, out: FloatArray) {
        val fy = (l + 16f) / 116f; val fx = fy + a / 500f; val fz = fy - b / 200f
        val x = inverse(fx) * .95047f; val y = inverse(fy); val z = inverse(fz) * 1.08883f
        out[0] = 3.2404542f * x - 1.5371385f * y - .4985314f * z
        out[1] = -.9692660f * x + 1.8760108f * y + .0415560f * z
        out[2] = .0556434f * x - .2040259f * y + 1.0572252f * z
    }
}

/**
 * Photoshop-style Match Color (colour reference matching): the layer's Lab statistics move to
 * the reference's, by [strength]; [keepLuminance] changes only the colour, not the lightness.
 * Transparency and the selection are respected; without a reference the layer is unchanged.
 */
internal object RasterMatchColor {
    fun apply(source: PixelTileSurfaceSnapshot, reference: ColorStatistics?, strength: Float, keepLuminance: Boolean,
              selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val own = ColorStatistics.of(source)
        if (reference == null || own == null || strength <= 0f) return PixelSurfaceResampling.convert(source, source.pixelFormat)
        fun scale(target: Float, current: Float) = if (current < 1e-3f) 1f else target / current
        val sl = scale(reference.spreadL, own.spreadL); val sa = scale(reference.spreadA, own.spreadA); val sb = scale(reference.spreadB, own.spreadB)
        val width = source.logicalWidth; val height = source.logicalHeight
        val output = PixelTileSurface(width, height, source.tileSize, pixelFormat = source.pixelFormat)
        val high = source.pixelFormat.isHighPrecision
        val lab = FloatArray(3); val rgb = FloatArray(3)
        output.beginWrite("match color").use { write ->
            source.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                val key = info.key; val tile = source.tileSize
                val w = min(tile, width - key.column * tile); val h = min(tile, height - key.row * tile)
                val packed = LongArray(tile * tile); val argb = IntArray(tile * tile)
                for (y in 0 until h) for (x in 0 until w) {
                    val px = key.column * tile + x; val py = key.row * tile + y
                    val p = source.linearPixelAt(px, py)
                    if (p.alpha <= 0f) continue
                    val weight = strength * (selection?.alphaAt((px.toLong() * selection.logicalWidth / width).toInt(),
                        (py.toLong() * selection.logicalHeight / height).toInt())?.div(255f) ?: 1f)
                    // Unselected pixels are copied exactly rather than round-tripped through Lab.
                    if (weight <= 0f) {
                        if (high) packed[y * tile + x] = source.packedF16At(px, py) else argb[y * tile + x] = source.pixelAt(px, py)
                        continue
                    }
                    Lab.fromLinear(p.red / p.alpha, p.green / p.alpha, p.blue / p.alpha, lab)
                    val l = if (keepLuminance) lab[0] else (lab[0] - own.meanL) * sl + reference.meanL
                    val a = (lab[1] - own.meanA) * sa + reference.meanA
                    val b = (lab[2] - own.meanB) * sb + reference.meanB
                    Lab.toLinear(lab[0] + (l - lab[0]) * weight, lab[1] + (a - lab[1]) * weight, lab[2] + (b - lab[2]) * weight, rgb)
                    if (high) packed[y * tile + x] = PackedRgbaF16.packPremultiplied(rgb[0] * p.alpha, rgb[1] * p.alpha, rgb[2] * p.alpha, p.alpha)
                    else {
                        val a8 = (p.alpha * 255f).roundToInt()
                        fun channel(v: Float) = (ExtendedSrgb.encode(v.coerceIn(0f, 1f)) * a8).roundToInt().coerceIn(0, a8)
                        argb[y * tile + x] = (a8 shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
                    }
                }
                if (high) write.editTileF16(key, PixelRect(0, 0, w, h)) { pixels, stride -> for (y in 0 until h) packed.copyInto(pixels, y * stride, y * tile, y * tile + w) }
                else write.editTile(key, PixelRect(0, 0, w, h)) { pixels, stride -> for (y in 0 until h) argb.copyInto(pixels, y * stride, y * tile, y * tile + w) }
            }
            write.commit().close()
        }
        return output
    }
}
