package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** CSP 5.1 Filter > Effect > Glow: bright areas spread soft light. Strength in %, area 0–100, blur range in px. */
data class GlowParameters(val strength: Float = 100f, val area: Float = 50f, val blur: Float = 30f) {
    init { require(strength in 0f..400f && area in 0f..100f && blur in 1f..100f) { "Glow settings are out of range" } }
}

/** CSP 5.1 Inflate: ink looks puffed up and lit. Shadow, shine and height 0–100, smoothness 0–10 px, light from [lightAngle]° (anticlockwise from the right). */
data class InflateParameters(val shadow: Float = 50f, val shine: Float = 50f, val height: Float = 30f, val smoothness: Float = 2f, val lightAngle: Float = 135f) {
    init {
        require(shadow in 0f..100f && shine in 0f..100f && height in 0f..100f && smoothness in 0f..10f && lightAngle.isFinite()) { "Inflate settings are out of range" }
    }
}

/** CSP 5.1 Drop shadow: falls [distance] px towards [angle]° (anticlockwise from the right), blurred by [blur] px. */
data class DropShadowParameters(val blur: Float = 8f, val angle: Float = -45f, val distance: Float = 12f, val opacity: Float = 60f, val colorArgb: Int = 0xFF000000.toInt()) {
    init { require(blur in 0f..100f && distance in 0f..500f && opacity in 0f..100f && angle.isFinite()) { "Drop shadow settings are out of range" } }
}

/** A digital glitch: horizontal bands slip up to [shift] px sideways and the colour channels split [split] px apart. */
data class GlitchParameters(val shift: Float = 40f, val bandHeight: Float = 24f, val density: Float = 40f, val split: Float = 6f, val seed: Int = 1) {
    init { require(shift in 0f..400f && bandHeight in 2f..400f && density in 0f..100f && split in 0f..100f) { "Glitch settings are out of range" } }
}

/**
 * Glow, Inflate, Drop shadow (CSP's Quick Effects) and Glitch. Each output tile reads a region of
 * the immutable source big enough for the effect's reach, so tiles join without seams; outside
 * the layer counts as transparent, and a selection weights the result.
 */
internal object RasterQuickEffects {
    fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface =
        when (effect) {
            is RasterImageFilter.Effect.Glow -> glow(source, effect.parameters, selection, cancelled)
            is RasterImageFilter.Effect.Inflate -> inflate(source, effect.parameters, selection, cancelled)
            is RasterImageFilter.Effect.DropShadow -> dropShadow(source, effect.parameters, selection, cancelled)
            is RasterImageFilter.Effect.Glitch -> glitch(source, effect.parameters, selection, cancelled)
            else -> error("Unsupported quick effect")
        }

    private fun glow(source: PixelTileSurfaceSnapshot, p: GlowParameters, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val radius = boxRadius(p.blur / 2f); val reach = radius * 3 + 1
        val threshold = 1f - p.area / 100f * .98f
        val gain = p.strength / 100f
        return RasterEffectFilter.render(source, selection, RasterEffectFilter.haloKeys(source, reach), cancelled) { left, top, w, h, out ->
            val rw = w + reach * 2; val rh = h + reach * 2
            val region = RasterEffectFilter.region(source, left - reach, top - reach, rw, rh)
            // Light leaves the pixels brighter than the threshold, weighted by how far above it they are.
            val light = FloatArray(rw * rh * 4)
            for (i in 0 until rw * rh) {
                val r = region.rgba[i * 4]; val g = region.rgba[i * 4 + 1]; val b = region.rgba[i * 4 + 2]; val a = region.rgba[i * 4 + 3]
                if (a <= 0f) continue
                val k = (((.2126f * r + .7152f * g + .0722f * b) - threshold) / max(1e-3f, 1f - threshold)).coerceIn(0f, 1f) * a
                light[i * 4] = r * k; light[i * 4 + 1] = g * k; light[i * 4 + 2] = b * k; light[i * 4 + 3] = k
            }
            blur(light, rw, rh, 4, radius)
            val c = FloatArray(4)
            for (y in 0 until h) for (x in 0 until w) {
                val j = ((y + reach) * rw + x + reach) * 4; val o = (y * w + x) * 4
                region.at(left + x, top + y, c)
                val la = (light[j + 3] * gain).coerceIn(0f, 1f)
                if (la <= 0f) { c.copyInto(out, o); continue }
                // The light is a layer of its own colour composited in Screen mode.
                val alpha = c[3] + la - c[3] * la
                for (ch in 0..2) {
                    val lc = (light[j + ch] * gain / la).coerceAtLeast(0f)
                    val screen = c[ch] + lc - c[ch] * lc
                    out[o + ch] = ((1f - la) * c[3] * c[ch] + (1f - c[3]) * la * lc + c[3] * la * screen) / alpha
                }
                out[o + 3] = alpha
            }
        }
    }

    private fun inflate(source: PixelTileSurfaceSnapshot, p: InflateParameters, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val dome = 2f + p.height / 100f * 38f
        val smooth = p.smoothness.roundToInt()
        val reach = ceil(dome).toInt() + smooth * 3 + 2
        val azimuth = p.lightAngle * PI.toFloat() / 180f
        val elevation = PI.toFloat() / 4f
        val lx = cos(elevation) * cos(azimuth); val ly = -cos(elevation) * sin(azimuth); val lz = sin(elevation)
        val hx = lx; val hy = ly; val hz = lz + 1f; val hl = sqrt(hx * hx + hy * hy + hz * hz)
        val exponent = 4f + p.shine * 1.2f; val sheen = .1f + .7f * p.shine / 100f
        return RasterEffectFilter.render(source, selection, RasterEffectFilter.haloKeys(source, 0), cancelled) { left, top, w, h, out ->
            val rw = w + reach * 2; val rh = h + reach * 2
            val region = RasterEffectFilter.region(source, left - reach, top - reach, rw, rh)
            val height = FloatArray(rw * rh) { if (region.rgba[it * 4 + 3] < .5f) 0f else 1e10f }
            EuclideanDistance.transform(height, rw, rh)
            for (i in height.indices) {
                // A rounded dome over the distance to the ink's edge: low heights flatten into an emboss.
                val d = (sqrt(height[i]) / dome).coerceAtMost(1f)
                height[i] = dome * sqrt(1f - (1f - d) * (1f - d)) * region.rgba[i * 4 + 3]
            }
            if (smooth > 0) blur(height, rw, rh, 1, smooth)
            val c = FloatArray(4)
            for (y in 0 until h) for (x in 0 until w) {
                val rx = x + reach; val ry = y + reach; val o = (y * w + x) * 4
                region.at(left + x, top + y, c)
                if (c[3] <= 0f) { c.copyInto(out, o); continue }
                val dx = (height[ry * rw + rx + 1] - height[ry * rw + rx - 1]) / 2f
                val dy = (height[(ry + 1) * rw + rx] - height[(ry - 1) * rw + rx]) / 2f
                val nl = sqrt(dx * dx + dy * dy + 1f)
                val nx = -dx / nl; val ny = -dy / nl; val nz = 1f / nl
                // Relative to a flat top, so flat areas keep their colour.
                val lit = (nx * lx + ny * ly + nz * lz).coerceAtLeast(0f) / lz
                val shade = (1f + (lit - 1f) * p.shadow / 100f * 1.2f).coerceAtLeast(0f)
                val highlight = sheen * ((nx * hx + ny * hy + nz * hz) / hl).coerceAtLeast(0f).pow(exponent)
                for (ch in 0..2) { val shaded = c[ch] * shade; out[o + ch] = shaded + (1f - shaded).coerceAtLeast(0f) * highlight }
                out[o + 3] = c[3]
            }
        }
    }

    private fun dropShadow(source: PixelTileSurfaceSnapshot, p: DropShadowParameters, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val angle = p.angle * PI.toFloat() / 180f
        val dx = cos(angle) * p.distance; val dy = -sin(angle) * p.distance
        val radius = if (p.blur < .5f) 0 else boxRadius(p.blur / 2f); val reach = radius * 3 + 2
        val shadow = floatArrayOf((p.colorArgb ushr 16 and 255) / 255f, (p.colorArgb ushr 8 and 255) / 255f, (p.colorArgb and 255) / 255f)
        val strength = p.opacity / 100f * (p.colorArgb ushr 24 and 255) / 255f
        return RasterEffectFilter.render(source, selection, shadowKeys(source, dx, dy, reach), cancelled) { left, top, w, h, out ->
            // The shadow at a pixel is the blurred alpha of the source one offset back along the light.
            val sx = floor(left - dx).toInt() - reach; val sy = floor(top - dy).toInt() - reach
            val rw = w + reach * 2 + 2; val rh = h + reach * 2 + 2
            val region = RasterEffectFilter.region(source, sx, sy, rw, rh)
            val alpha = FloatArray(rw * rh) { region.rgba[it * 4 + 3] }
            if (radius > 0) blur(alpha, rw, rh, 1, radius)
            fun sample(x: Float, y: Float): Float {
                val fx = x - sx; val fy = y - sy
                val x0 = floor(fx).toInt().coerceIn(0, rw - 2); val y0 = floor(fy).toInt().coerceIn(0, rh - 2)
                val tx = (fx - x0).coerceIn(0f, 1f); val ty = (fy - y0).coerceIn(0f, 1f)
                val top0 = alpha[y0 * rw + x0] * (1 - tx) + alpha[y0 * rw + x0 + 1] * tx
                val bottom = alpha[(y0 + 1) * rw + x0] * (1 - tx) + alpha[(y0 + 1) * rw + x0 + 1] * tx
                return top0 * (1 - ty) + bottom * ty
            }
            val c = FloatArray(4)
            val original = RasterEffectFilter.region(source, left, top, w, h)
            for (y in 0 until h) for (x in 0 until w) {
                val o = (y * w + x) * 4
                original.at(left + x, top + y, c)
                val sa = (sample(left + x - dx, top + y - dy) * strength).coerceIn(0f, 1f)
                // The layer stays on top of its shadow.
                val a = c[3] + sa * (1f - c[3])
                if (a <= 0f) continue
                for (ch in 0..2) out[o + ch] = (c[ch] * c[3] + shadow[ch] * sa * (1f - c[3])) / a
                out[o + 3] = a
            }
        }
    }

    private fun glitch(source: PixelTileSurfaceSnapshot, p: GlitchParameters, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val reach = ceil(p.shift + p.split).toInt() + 1
        val offset = RasterEffectFilter.hash(0, 0, p.seed) * p.bandHeight
        val split = p.split.roundToInt()
        return RasterEffectFilter.render(source, selection, RasterEffectFilter.haloKeys(source, reach), cancelled) { left, top, w, h, out ->
            val region = RasterEffectFilter.region(source, left - reach, top, w + reach * 2, h)
            val red = FloatArray(4); val green = FloatArray(4); val blue = FloatArray(4)
            for (y in 0 until h) {
                val band = floor((top + y + offset) / p.bandHeight).toInt()
                val shifted = RasterEffectFilter.hash(band, 1, p.seed) < p.density / 100f
                val shift = if (shifted) ((RasterEffectFilter.hash(band, 2, p.seed) * 2f - 1f) * p.shift).roundToInt() else 0
                for (x in 0 until w) {
                    val px = left + x - shift; val py = top + y; val o = (y * w + x) * 4
                    // Red slips right and blue left of the band.
                    region.at(px - split, py, red); region.at(px, py, green); region.at(px + split, py, blue)
                    val a = max(red[3], max(green[3], blue[3]))
                    if (a <= 0f) continue
                    out[o] = red[0] * red[3] / a; out[o + 1] = green[1] * green[3] / a; out[o + 2] = blue[2] * blue[3] / a; out[o + 3] = a
                }
            }
        }
    }

    /** Tiles that hold ink or receive its shadow. */
    private fun shadowKeys(source: PixelTileSurfaceSnapshot, dx: Float, dy: Float, reach: Int): Set<PixelTileKey> {
        val tile = source.tileSize; val keys = linkedSetOf<PixelTileKey>()
        val lastColumn = (source.logicalWidth - 1) / tile; val lastRow = (source.logicalHeight - 1) / tile
        source.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
            keys += info.key
            val left = info.key.column * tile + dx - reach; val top = info.key.row * tile + dy - reach
            val right = (info.key.column + 1) * tile + dx + reach; val bottom = (info.key.row + 1) * tile + dy + reach
            for (row in max(0, floor(top / tile).toInt())..min(lastRow, floor(bottom / tile).toInt()))
                for (column in max(0, floor(left / tile).toInt())..min(lastColumn, floor(right / tile).toInt())) keys += PixelTileKey(column, row)
        }
        return keys
    }

    /** Box radius whose three passes approximate a Gaussian of [sigma]. */
    private fun boxRadius(sigma: Float): Int = max(1, ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt())

    /** Three box blurs of radius [radius] over a [channels]-interleaved plane, zero outside it. */
    private fun blur(plane: FloatArray, width: Int, height: Int, channels: Int, radius: Int) {
        val scratch = FloatArray(plane.size)
        repeat(3) {
            pass(plane, scratch, width, height, channels, radius, horizontal = true)
            pass(scratch, plane, width, height, channels, radius, horizontal = false)
        }
    }

    private fun pass(from: FloatArray, to: FloatArray, width: Int, height: Int, channels: Int, radius: Int, horizontal: Boolean) {
        val extent = if (horizontal) width else height; val lines = if (horizontal) height else width
        val step = (if (horizontal) 1 else width) * channels; val scale = 1f / (radius * 2 + 1)
        val sums = FloatArray(channels)
        for (line in 0 until lines) {
            val start = (if (horizontal) line * width else line) * channels
            sums.fill(0f)
            for (k in 0..min(radius, extent - 1)) for (c in 0 until channels) sums[c] += from[start + k * step + c]
            for (position in 0 until extent) {
                val at = start + position * step
                for (c in 0 until channels) to[at + c] = sums[c] * scale
                val leaving = position - radius; val entering = position + radius + 1
                if (leaving >= 0) for (c in 0 until channels) sums[c] -= from[start + leaving * step + c]
                if (entering < extent) for (c in 0 until channels) sums[c] += from[start + entering * step + c]
            }
        }
    }
}
