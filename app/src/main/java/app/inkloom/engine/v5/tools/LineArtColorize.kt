package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Colorize settings. Line art (alpha ≥ [lineThreshold]) divides the page into regions; gaps up to
 * [gapClosing] pixels wide are closed. With a hint image each region takes the hint colour it
 * contains most, or the first in [priority] when [analyzeColors] is off and several are present.
 * [fillUnhinted] gives regions without hints the nearest hint colour; [fillUnderLines] colours the
 * line pixels too, so no gap shows between colour and line; [blur] softens colour edges.
 */
data class ColorizeSettings(
    val lineThreshold: Float = .5f,
    val gapClosing: Int = 2,
    val analyzeColors: Boolean = true,
    val priority: List<Int> = emptyList(),
    val fillUnhinted: Boolean = false,
    val fillUnderLines: Boolean = true,
    val blur: Int = 0,
    /** Colorize all: colours for regions without a hint image, in order of region size. */
    val palette: List<Int> = emptyList(),
) {
    init { require(lineThreshold in .01f..1f && gapClosing in 0..20 && blur in 0..30) { "Colorize settings are out of range" } }
}

/**
 * Line-art colourising without AI: a flood fill of the regions the line art encloses, coloured from
 * a hint layer (or a palette), as a flat colour layer to sit under the lines.
 */
object LineArtColorize {
    /** The hint layer's colours, most area first (at most [limit]), as opaque ARGB. */
    fun hintColors(hint: PixelTileSurfaceSnapshot, limit: Int = 10): List<Int> {
        val counts = HashMap<Int, Int>()
        for (y in 0 until hint.logicalHeight step 2) for (x in 0 until hint.logicalWidth step 2) {
            val c = straight(hint, x, y) ?: continue
            counts[quantise(c)] = (counts[quantise(c)] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** Premultiplied colour pixels at the size of [lines]. */
    fun render(lines: PixelTileSurfaceSnapshot, hint: PixelTileSurfaceSnapshot?, settings: ColorizeSettings, cancelled: () -> Boolean = { false }): IntArray {
        val w = lines.logicalWidth; val h = lines.logicalHeight; val n = w * h
        fun check() { if (cancelled()) throw java.util.concurrent.CancellationException("Colorize preview superseded") }
        // Line pixels, then grown to close small gaps between strokes.
        val line = BooleanArray(n) { i -> alpha(lines, i % w, i / w) >= settings.lineThreshold }
        val barrier = if (settings.gapClosing > 0) dilate(line, w, h, settings.gapClosing) else line
        check()
        val label = IntArray(n) { -1 }
        val sizes = ArrayList<Int>(); val touchesEdge = ArrayList<Boolean>()
        val queue = IntArray(n)
        for (start in 0 until n) {
            if (barrier[start] || label[start] >= 0) continue
            val id = sizes.size
            var head = 0; var tail = 0; var edge = false
            queue[tail++] = start; label[start] = id
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) edge = true
                if (x > 0 && !barrier[i - 1] && label[i - 1] < 0) { label[i - 1] = id; queue[tail++] = i - 1 }
                if (x < w - 1 && !barrier[i + 1] && label[i + 1] < 0) { label[i + 1] = id; queue[tail++] = i + 1 }
                if (y > 0 && !barrier[i - w] && label[i - w] < 0) { label[i - w] = id; queue[tail++] = i - w }
                if (y < h - 1 && !barrier[i + w] && label[i + w] < 0) { label[i + w] = id; queue[tail++] = i + w }
            }
            sizes += tail; touchesEdge += edge
        }
        check()
        val colour = IntArray(sizes.size) // 0 = none
        if (hint != null) {
            val votes = Array(sizes.size) { HashMap<Int, Int>() }
            for (i in 0 until n) {
                val l = label[i]; if (l < 0) continue
                val c = straight(hint, (i % w) * hint.logicalWidth / w, (i / w) * hint.logicalHeight / h) ?: continue
                val q = quantise(c); votes[l][q] = (votes[l][q] ?: 0) + 1
            }
            val order = settings.priority.map(::quantise)
            for (r in sizes.indices) {
                val v = votes[r]; if (v.isEmpty()) continue
                colour[r] = if (!settings.analyzeColors && order.isNotEmpty()) order.firstOrNull { it in v } ?: v.maxBy { it.value }.key
                    else if (order.isNotEmpty()) v.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { order.indexOf(it.key).let { p -> if (p < 0) Int.MAX_VALUE else p } }).first().key
                    else v.maxBy { it.value }.key
            }
        } else if (settings.palette.isNotEmpty()) {
            // Colorize all: the background (the largest region on the page edge) stays clear.
            val background = sizes.indices.filter { touchesEdge[it] }.maxByOrNull { sizes[it] }
            sizes.indices.filter { it != background && sizes[it] >= 16 }.sortedByDescending { sizes[it] }.forEachIndexed { rank, r ->
                colour[r] = settings.palette[rank % settings.palette.size] or 0xFF000000.toInt()
            }
        }
        check()
        val pixel = IntArray(n) { i -> label[i].let { if (it < 0) 0 else colour[it] } }
        // Regions without hints, and the line pixels, take the nearest colour found.
        if (settings.fillUnhinted || settings.fillUnderLines) {
            val open = BooleanArray(n) { i -> pixel[i] == 0 && (if (label[i] < 0) settings.fillUnderLines else settings.fillUnhinted) }
            var head = 0; var tail = 0
            for (i in 0 until n) if (pixel[i] != 0) queue[tail++] = i
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                fun spread(j: Int) { if (open[j] && pixel[j] == 0) { pixel[j] = pixel[i]; queue[tail++] = j } }
                if (x > 0) spread(i - 1); if (x < w - 1) spread(i + 1); if (y > 0) spread(i - w); if (y < h - 1) spread(i + w)
            }
            if (!settings.fillUnderLines) for (i in 0 until n) if (label[i] < 0) pixel[i] = 0
        }
        check()
        return if (settings.blur > 0) blur(pixel, w, h, settings.blur) else pixel
    }

    /** A harmony palette around [base]: neighbouring and opposite hues with varied lightness, for Colorize all. */
    fun palette(base: Int, count: Int = 8): List<Int> {
        val r = (base ushr 16 and 255) / 255f; val g = (base ushr 8 and 255) / 255f; val b = (base and 255) / 255f
        val maxC = maxOf(r, g, b); val minC = minOf(r, g, b); val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return List(count) { i ->
            val h = ((hue + listOf(0f, 30f, -30f, 180f, 150f, 210f, 60f, -60f)[i % 8]) % 360f + 360f) % 360f
            hsv(h, (.35f + .1f * (i % 3)).coerceIn(0f, 1f), (.95f - .08f * (i % 4)).coerceIn(0f, 1f))
        }
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s; val x = c * (1f - abs((h / 60f) % 2f - 1f)); val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun quantise(argb: Int): Int {
        fun q(v: Int) = ((v + 8) / 16 * 16).coerceAtMost(255)
        return (0xFF shl 24) or (q(argb ushr 16 and 255) shl 16) or (q(argb ushr 8 and 255) shl 8) or q(argb and 255)
    }

    private fun straight(surface: PixelTileSurfaceSnapshot, x: Int, y: Int): Int? {
        val cx = x.coerceIn(0, surface.logicalWidth - 1); val cy = y.coerceIn(0, surface.logicalHeight - 1)
        if (surface.pixelFormat.isHighPrecision) {
            val p = surface.linearPixelAt(cx, cy)
            if (p.alpha < .5f) return null
            fun c(v: Float) = (app.inkloom.engine.v5.composite.ExtendedSrgb.encode((v / p.alpha).coerceIn(0f, 1f)) * 255f).roundToInt()
            return (0xFF shl 24) or (c(p.red) shl 16) or (c(p.green) shl 8) or c(p.blue)
        }
        val v = surface.pixelAt(cx, cy); val a = v ushr 24
        if (a < 128) return null
        fun c(shift: Int) = ((v ushr shift and 255) * 255 / a).coerceAtMost(255)
        return (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private fun alpha(surface: PixelTileSurfaceSnapshot, x: Int, y: Int): Float =
        if (surface.pixelFormat.isHighPrecision) surface.linearPixelAt(x, y).alpha else (surface.pixelAt(x, y) ushr 24) / 255f

    private fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val horizontal = BooleanArray(mask.size)
        for (y in 0 until h) { var last = -radius - 1
            for (x in 0 until w) { if (mask[y * w + x]) last = x; if (x - last <= radius) horizontal[y * w + x] = true }
            last = w + radius + 1
            for (x in w - 1 downTo 0) { if (mask[y * w + x]) last = x; if (last - x <= radius) horizontal[y * w + x] = true } }
        val out = BooleanArray(mask.size)
        for (x in 0 until w) { var last = -radius - 1
            for (y in 0 until h) { if (horizontal[y * w + x]) last = y; if (y - last <= radius) out[y * w + x] = true }
            last = h + radius + 1
            for (y in h - 1 downTo 0) { if (horizontal[y * w + x]) last = y; if (last - y <= radius) out[y * w + x] = true } }
        return out
    }

    /** Box blur of premultiplied ARGB, horizontally then vertically. */
    private fun blur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        fun pass(src: IntArray, horizontal: Boolean): IntArray {
            val dst = IntArray(src.size); val lines = if (horizontal) h else w; val length = if (horizontal) w else h
            val sums = IntArray(4)
            for (line in 0 until lines) {
                fun index(k: Int) = if (horizontal) line * w + k else k * w + line
                sums.fill(0)
                for (k in -radius..radius) { val v = src[index(k.coerceIn(0, length - 1))]; for (c in 0..3) sums[c] += v ushr (c * 8) and 255 }
                val span = 2 * radius + 1
                for (k in 0 until length) {
                    dst[index(k)] = (sums[3] / span shl 24) or (sums[2] / span shl 16) or (sums[1] / span shl 8) or (sums[0] / span)
                    val out = src[index((k - radius).coerceIn(0, length - 1))]; val inn = src[index((k + radius + 1).coerceIn(0, length - 1))]
                    for (c in 0..3) sums[c] += (inn ushr (c * 8) and 255) - (out ushr (c * 8) and 255)
                }
            }
            return dst
        }
        return pass(pass(pixels, true), false)
    }
}
