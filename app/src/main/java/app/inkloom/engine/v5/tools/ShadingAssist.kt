package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CSP's Shading Assist settings. The light comes from [angleDegrees] (0 = from the right, 90 = from
 * below, screen coordinates) at [elevation] (0 = grazing, 1 = straight on). [amount] widens the
 * shadow, [softness] blends its edge; cel shading gives a hard edge. [reverse] lights the shapes
 * from behind, leaving shadow in the middle and light on the rims.
 */
data class ShadingSettings(
    val angleDegrees: Float = 225f,
    val elevation: Float = .45f,
    val amount: Float = .5f,
    val softness: Float = .25f,
    val cel: Boolean = false,
    val reverse: Boolean = false,
    val shadowColorArgb: Int = 0xFF7A6A9A.toInt(),
    val opacity: Float = .6f,
    val linesAreBoundaries: Boolean = true,
) {
    init {
        require(elevation in 0f..1f && amount in 0f..1f && softness in 0f..1f && opacity in 0f..1f) { "Shading settings are out of range" }
    }
}

/** CSP's lighting presets, as starting points for the settings. */
enum class ShadingPreset(val settings: ShadingSettings) {
    STANDARD(ShadingSettings()),
    MORNING(ShadingSettings(angleDegrees = 315f, elevation = .35f, amount = .4f, shadowColorArgb = 0xFF8A86B8.toInt(), opacity = .45f)),
    EVENING(ShadingSettings(angleDegrees = 190f, elevation = .2f, amount = .6f, shadowColorArgb = 0xFFA0605A.toInt(), opacity = .55f)),
    NIGHT(ShadingSettings(angleDegrees = 250f, elevation = .5f, amount = .75f, shadowColorArgb = 0xFF2F3A66.toInt(), opacity = .7f)),
    SIMPLE_LIGHT(ShadingSettings(cel = true, amount = .45f, opacity = .5f)),
    FAINT_BACKLIGHT(ShadingSettings(reverse = true, amount = .45f, opacity = .35f)),
    BACKLIGHT(ShadingSettings(reverse = true, amount = .65f, softness = .2f, opacity = .6f)),
    STAGE_LIGHT(ShadingSettings(angleDegrees = 270f, elevation = .8f, amount = .4f, shadowColorArgb = 0xFF5A4A7A.toInt(), opacity = .65f)),
    COLOR_LIGHT(ShadingSettings(shadowColorArgb = 0xFF3F6F9F.toInt(), opacity = .5f)),
    HIGH_CONTRAST(ShadingSettings(cel = true, amount = .6f, shadowColorArgb = 0xFF3A3050.toInt(), opacity = .8f)),
}

/**
 * Shadows for flat-coloured artwork without AI: each flat colour region (split by line art) is
 * treated as a dome whose height grows with the distance from its edge, and lit by one light.
 * Work happens on a grid of at most [GRID] pixels per side; the shadow field is then sampled
 * smoothly at full size and clipped to the colours, so edges stay as sharp as the artwork.
 */
object ShadingAssist {
    private const val GRID = 768

    /** Premultiplied shadow pixels at the size of [colours]. */
    fun render(colours: PixelTileSurfaceSnapshot, lines: PixelTileSurfaceSnapshot?, settings: ShadingSettings, cancelled: () -> Boolean = { false }): IntArray {
        val width = colours.logicalWidth; val height = colours.logicalHeight
        val step = max(1f, max(width, height) / GRID.toFloat())
        val gw = max(1, (width / step).roundToInt()); val gh = max(1, (height / step).roundToInt())
        val field = shadowField(colours, lines, settings, gw, gh, cancelled)
        val out = IntArray(width * height)
        val color = settings.shadowColorArgb
        for (y in 0 until height) {
            if (y % 64 == 0 && cancelled()) throw java.util.concurrent.CancellationException("Shading preview superseded")
            for (x in 0 until width) {
                val coverage = alpha(colours, x, y) * (if (settings.linesAreBoundaries && lines != null) 1f - alpha(lines, x, y) else 1f)
                if (coverage <= 0f) continue
                var shade = sample(field, gw, gh, (x + .5f) / step - .5f, (y + .5f) / step - .5f)
                if (settings.cel) shade = if (shade >= .5f) 1f else 0f
                val a = (shade * coverage * settings.opacity * (color ushr 24 and 255)).roundToInt().coerceIn(0, 255)
                if (a == 0) continue
                fun channel(shift: Int) = ((color ushr shift and 255) * a + 127) / 255
                out[y * width + x] = (a shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
        return out
    }

    /** Shadow amount 0..1 on the grid, 0 outside the colours. */
    internal fun shadowField(colours: PixelTileSurfaceSnapshot, lines: PixelTileSurfaceSnapshot?, settings: ShadingSettings,
                             gw: Int, gh: Int, cancelled: () -> Boolean): FloatArray {
        val sx = colours.logicalWidth / gw.toFloat(); val sy = colours.logicalHeight / gh.toFloat()
        // Flat colours, quantised so anti-aliased edges do not split regions; line art is no region.
        val key = IntArray(gw * gh) { i ->
            val x = ((i % gw + .5f) * sx).toInt().coerceIn(0, colours.logicalWidth - 1)
            val y = ((i / gw + .5f) * sy).toInt().coerceIn(0, colours.logicalHeight - 1)
            val line = settings.linesAreBoundaries && lines != null && alpha(lines, x, y) > .5f
            if (line || alpha(colours, x, y) < .5f) -1 else quantised(colours, x, y)
        }
        if (cancelled()) throw java.util.concurrent.CancellationException("Shading preview superseded")
        val label = IntArray(gw * gh) { -1 }
        var regions = 0
        val queue = IntArray(gw * gh)
        for (start in key.indices) {
            if (key[start] < 0 || label[start] >= 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = regions
            while (head < tail) {
                val i = queue[head++]; val x = i % gw; val y = i / gw
                fun visit(j: Int) { if (label[j] < 0 && key[j] == key[start]) { label[j] = regions; queue[tail++] = j } }
                if (x > 0) visit(i - 1); if (x < gw - 1) visit(i + 1); if (y > 0) visit(i - gw); if (y < gh - 1) visit(i + gw)
            }
            regions++
        }
        // Distance from each region's edge: edges are cells with a neighbour from elsewhere.
        val inf = 1e12f
        val distance = FloatArray(gw * gh) { i ->
            if (label[i] < 0) 0f else {
                val x = i % gw; val y = i / gw; val l = label[i]
                val edge = x == 0 || y == 0 || x == gw - 1 || y == gh - 1 || label[i - 1] != l || label[i + 1] != l || label[i - gw] != l || label[i + gw] != l
                if (edge) 0f else inf
            }
        }
        EuclideanDistance.transform(distance, gw, gh)
        val maxima = FloatArray(regions)
        for (i in distance.indices) if (label[i] >= 0) { distance[i] = sqrt(distance[i]); maxima[label[i]] = max(maxima[label[i]], distance[i]) }
        if (cancelled()) throw java.util.concurrent.CancellationException("Shading preview superseded")
        // A dome over each region: a circular profile rising from the edge to the region's middle.
        val height = FloatArray(gw * gh) { i ->
            if (label[i] < 0) 0f else { val m = maxima[label[i]].coerceAtLeast(1f); val t = (distance[i] / m).coerceIn(0f, 1f); m * sqrt(1f - (1f - t) * (1f - t)) }
        }
        val radians = settings.angleDegrees * PI.toFloat() / 180f
        val elevation = settings.elevation * PI.toFloat() / 2f
        var lx = cos(radians) * cos(elevation); var ly = sin(radians) * cos(elevation); val lz = sin(elevation)
        if (settings.reverse) { lx = -lx; ly = -ly }
        // The amount moves the shadow line from the far side towards the lit side.
        val threshold = -.25f + settings.amount * 1.1f
        val soft = (settings.softness * .6f).coerceAtLeast(.02f)
        return FloatArray(gw * gh) { i ->
            val l = label[i]
            if (l < 0) return@FloatArray 0f
            val x = i % gw; val y = i / gw
            fun h(j: Int) = if (label[j] == l) height[j] else 0f
            val dx = ((if (x < gw - 1) h(i + 1) else height[i]) - (if (x > 0) h(i - 1) else height[i])) / 2f
            val dy = ((if (y < gh - 1) h(i + gw) else height[i]) - (if (y > 0) h(i - gw) else height[i])) / 2f
            val length = sqrt(dx * dx + dy * dy + 1f)
            val lambert = (-dx * lx - dy * ly + lz) / length
            val light = if (settings.reverse) 1f - lambert else lambert
            ((threshold - light) / soft + .5f).coerceIn(0f, 1f)
        }
    }

    private fun quantised(surface: PixelTileSurfaceSnapshot, x: Int, y: Int): Int {
        val p = surface.linearPixelAt(x, y)
        if (p.alpha <= 0f) return 0
        fun q(v: Float) = (min(1f, v / p.alpha) * 15f).roundToInt()
        return (q(p.red) shl 8) or (q(p.green) shl 4) or q(p.blue)
    }

    private fun alpha(surface: PixelTileSurfaceSnapshot, x: Int, y: Int): Float =
        if (surface.pixelFormat.isHighPrecision) surface.linearPixelAt(x.coerceIn(0, surface.logicalWidth - 1), y.coerceIn(0, surface.logicalHeight - 1)).alpha
        else (surface.pixelAt(x.coerceIn(0, surface.logicalWidth - 1), y.coerceIn(0, surface.logicalHeight - 1)) ushr 24) / 255f

    private fun sample(field: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, w - 1); val y0 = y.toInt().coerceIn(0, h - 1)
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val fx = (x - x0).coerceIn(0f, 1f); val fy = (y - y0).coerceIn(0f, 1f)
        val top = field[y0 * w + x0] * (1 - fx) + field[y0 * w + x1] * fx
        val bottom = field[y1 * w + x0] * (1 - fx) + field[y1 * w + x1] * fx
        return top * (1 - fy) + bottom * fy
    }
}

/** Squared Euclidean distance transform (Felzenszwalb–Huttenlocher), in place: 0 = seed. */
internal object EuclideanDistance {
    fun transform(values: FloatArray, width: Int, height: Int) {
        val size = max(width, height)
        val f = FloatArray(size); val d = FloatArray(size); val v = IntArray(size); val z = FloatArray(size + 1)
        for (x in 0 until width) {
            for (y in 0 until height) f[y] = values[y * width + x]
            line(f, height, d, v, z)
            for (y in 0 until height) values[y * width + x] = d[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) f[x] = values[y * width + x]
            line(f, width, d, v, z)
            for (x in 0 until width) values[y * width + x] = d[x]
        }
    }

    private fun line(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
        var k = 0
        v[0] = 0; z[0] = -Float.MAX_VALUE; z[1] = Float.MAX_VALUE
        for (q in 1 until n) {
            var s: Float
            while (true) {
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2f * q - 2f * v[k])
                if (s <= z[k] && k > 0) k-- else break
            }
            if (s <= z[k]) { v[0] = q; z[0] = -Float.MAX_VALUE; z[1] = Float.MAX_VALUE; k = 0; continue }
            k++; v[k] = q; z[k] = s; z[k + 1] = Float.MAX_VALUE
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val dq = q - v[k]
            d[q] = dq * dq + f[v[k]]
        }
    }
}
