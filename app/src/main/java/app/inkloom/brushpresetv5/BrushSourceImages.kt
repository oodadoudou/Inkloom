package app.inkloom.brushpresetv5

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** ARGB pixels of a brush source image, row by row. */
class SourcePixels(val width: Int, val height: Int, val argb: IntArray) {
    init { require(width > 0 && height > 0 && argb.size == width * height) { "Image size does not match its pixels" } }
}

/** A built-in original source: a tileable paper texture or a brush tip shape, generated, never sampled from others' artwork. */
enum class BuiltInBrushSource(val grain: Boolean, val english: String, val chinese: String) {
    PAPER(true, "Paper fibre", "纸张纤维"),
    CANVAS(true, "Canvas weave", "画布纹理"),
    WATERCOLOR(true, "Watercolour paper", "水彩纸"),
    FINE_GRAIN(true, "Fine grain", "细颗粒"),
    HALFTONE(true, "Halftone dots", "网点"),
    HATCHING(true, "Hatching", "排线"),
    CHALK(false, "Chalk", "粉笔"),
    SPLATTER(false, "Splatter", "飞溅"),
    BRISTLES(false, "Bristles", "鬃毛"),
    CHARCOAL(false, "Charcoal", "炭笔"),
}

/**
 * Brush source image edits that need no Android types: trimming to the drawn content, squaring a
 * tip so it rotates evenly, and generating the built-in sources. Paper textures wrap at their edges,
 * so they repeat without seams.
 */
object BrushSourceImages {
    const val GRAIN_SIZE = 512
    const val TIP_SIZE = 256

    /** The area that differs from the background: opaque content of a transparent image, or what stands out from the border colour. */
    fun contentBounds(image: SourcePixels): IntArray? {
        val transparent = image.argb.any { it ushr 24 != 255 }
        val background = if (transparent) 0 else borderLuminance(image)
        var left = image.width; var top = image.height; var right = -1; var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image.argb[y * image.width + x]
            val content = if (transparent) pixel ushr 24 > 8 else abs(luminance(pixel) - background) > 24
            if (content) { left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y) }
        }
        return if (right < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
    }

    /** Crops to [contentBounds] with a small margin; fails when there is nothing drawn. */
    fun trimmed(image: SourcePixels, margin: Int = 2): SourcePixels {
        val bounds = requireNotNull(contentBounds(image)) { "The image is empty" }
        val left = max(0, bounds[0] - margin); val top = max(0, bounds[1] - margin)
        val right = min(image.width, bounds[2] + margin); val bottom = min(image.height, bounds[3] + margin)
        val w = right - left; val h = bottom - top
        return SourcePixels(w, h, IntArray(w * h) { i -> image.argb[(top + i / w) * image.width + left + i % w] })
    }

    /** Centres the image on a square of its longer side, filled with transparency or the border colour; nothing is cut off. */
    fun squared(image: SourcePixels): SourcePixels {
        val side = max(image.width, image.height)
        if (image.width == image.height) return image
        val transparent = image.argb.any { it ushr 24 != 255 }
        val fill = if (transparent) 0 else borderLuminance(image).let { (255 shl 24) or (it shl 16) or (it shl 8) or it }
        val dx = (side - image.width) / 2; val dy = (side - image.height) / 2
        val out = IntArray(side * side) { fill }
        for (y in 0 until image.height) image.argb.copyInto(out, (y + dy) * side + dx, y * image.width, (y + 1) * image.width)
        return SourcePixels(side, side, out)
    }

    fun generate(source: BuiltInBrushSource): SourcePixels = when (source) {
        // Fibres: noise four times finer across than along; every lattice divides the tile, so it still wraps.
        BuiltInBrushSource.PAPER -> gray(GRAIN_SIZE) { x, y -> .55f + .35f * fbm(x, y, 11, 8, 5) + .15f * fbm(x * 4f, y, 23, 4, 3) }
        BuiltInBrushSource.CANVAS -> gray(GRAIN_SIZE) { x, y -> canvas(x, y) }
        BuiltInBrushSource.WATERCOLOR -> gray(GRAIN_SIZE) { x, y -> .35f + .45f * cells(x, y, 12, 31) + .2f * fbm(x, y, 37, 16, 4) }
        BuiltInBrushSource.FINE_GRAIN -> gray(GRAIN_SIZE) { x, y -> .5f + .5f * fbm(x, y, 41, 128, 2) }
        BuiltInBrushSource.HALFTONE -> gray(GRAIN_SIZE) { x, y -> halftone(x, y) }
        BuiltInBrushSource.HATCHING -> gray(GRAIN_SIZE) { x, y -> hatching(x, y) }
        BuiltInBrushSource.CHALK -> tip { x, y, r -> (smooth(1f - r, .08f) * (.35f + .9f * fbm(x * 2f, y * 2f, 53, 16, 4)) - .25f).coerceIn(0f, 1f) * 1.4f }
        BuiltInBrushSource.SPLATTER -> tip { x, y, _ -> splatter(x, y) }
        BuiltInBrushSource.BRISTLES -> tip { x, y, _ -> bristles(x, y) }
        BuiltInBrushSource.CHARCOAL -> tip { x, y, _ -> charcoal(x, y) }
    }

    // ----- generators: coordinates are in [0, size); grain noise wraps every GRAIN_SIZE pixels -----

    private fun gray(size: Int, value: (Float, Float) -> Float) = SourcePixels(size, size, IntArray(size * size) { i ->
        val v = (value((i % size).toFloat(), (i / size).toFloat()).coerceIn(0f, 1f) * 255f).roundToInt()
        (255 shl 24) or (v shl 16) or (v shl 8) or v
    })

    /** White ink whose alpha is the shape's coverage; [value] receives pixel coordinates and the distance from the centre (0–1). */
    private fun tip(value: (Float, Float, Float) -> Float) = SourcePixels(TIP_SIZE, TIP_SIZE, IntArray(TIP_SIZE * TIP_SIZE) { i ->
        val x = (i % TIP_SIZE).toFloat(); val y = (i / TIP_SIZE).toFloat()
        val r = hypot(x + .5f - TIP_SIZE / 2f, y + .5f - TIP_SIZE / 2f) / (TIP_SIZE / 2f)
        val a = (value(x, y, r).coerceIn(0f, 1f) * 255f).roundToInt()
        (a shl 24) or 0xFFFFFF
    })

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }

    private fun smooth(t: Float, edge: Float) = (t / edge).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** Value noise on a [cells]-wide lattice that wraps across GRAIN_SIZE pixels. */
    private fun noise(x: Float, y: Float, seed: Int, cells: Int): Float {
        val fx = x / GRAIN_SIZE * cells; val fy = y / GRAIN_SIZE * cells
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val tx = (fx - x0).let { it * it * (3f - 2f * it) }; val ty = (fy - y0).let { it * it * (3f - 2f * it) }
        fun at(ix: Int, iy: Int) = hash(Math.floorMod(ix, cells), Math.floorMod(iy, cells), seed)
        val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
        val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    /** Fractal noise in −0.5..0.5 whose octaves double a base lattice of [cells]. */
    private fun fbm(x: Float, y: Float, seed: Int, cells: Int, octaves: Int): Float {
        var total = 0f; var amplitude = .5f; var weight = 0f; var lattice = cells
        repeat(octaves) { octave ->
            total += amplitude * noise(x, y, seed + octave * 101, lattice)
            weight += amplitude; amplitude *= .5f; lattice *= 2
        }
        return total / weight - .5f
    }

    /** Distance to the nearest of [cells]² jittered points, wrapping; ridges where cells meet. */
    private fun cells(x: Float, y: Float, cells: Int, seed: Int): Float {
        val size = GRAIN_SIZE.toFloat() / cells
        val cx = floor(x / size).toInt(); val cy = floor(y / size).toInt()
        var nearest = Float.MAX_VALUE; var second = Float.MAX_VALUE
        for (oy in -1..1) for (ox in -1..1) {
            val ix = cx + ox; val iy = cy + oy
            val px = (ix + hash(Math.floorMod(ix, cells), Math.floorMod(iy, cells), seed)) * size
            val py = (iy + hash(Math.floorMod(ix, cells), Math.floorMod(iy, cells), seed + 7)) * size
            val d = hypot(x - px, y - py)
            if (d < nearest) { second = nearest; nearest = d } else if (d < second) second = d
        }
        return ((second - nearest) / size).coerceIn(0f, 1f)
    }

    private fun canvas(x: Float, y: Float): Float {
        val period = 8f
        val cx = floor(x / period).toInt(); val cy = floor(y / period).toInt()
        val horizontal = (cx + cy) % 2 == 0
        val across = (if (horizontal) y else x) / period * 2f * Math.PI.toFloat()
        val thread = .5f + .5f * cos(across)
        return .3f + .55f * thread + .25f * fbm(x, y, 61, 32, 3)
    }

    private fun halftone(x: Float, y: Float): Float {
        val period = 16f
        val dx = (x % period) - period / 2f; val dy = (y % period) - period / 2f
        return 1f - smooth(5.5f - sqrt(dx * dx + dy * dy), 1.2f)
    }

    private fun hatching(x: Float, y: Float): Float {
        val period = 16f
        val d = ((x + y) % period) - period / 2f
        return .25f + .75f * (1f - exp(-(d * d) / 3f)) + .1f * fbm(x, y, 71, 32, 2)
    }

    private fun splatter(x: Float, y: Float): Float {
        var coverage = 0f
        for (i in 0 until 36) {
            val angle = hash(i, 0, 83) * 2f * Math.PI.toFloat(); val reach = sqrt(hash(i, 1, 83)) * TIP_SIZE * .42f
            val px = TIP_SIZE / 2f + cos(angle) * reach; val py = TIP_SIZE / 2f + sin(angle) * reach
            val radius = 3f + hash(i, 2, 83) * 14f * (1f - reach / (TIP_SIZE * .5f))
            coverage = max(coverage, smooth(radius - hypot(x - px, y - py), 1.5f))
        }
        return coverage
    }

    private fun bristles(x: Float, y: Float): Float {
        var coverage = 0f
        for (i in 0 until 22) {
            val px = TIP_SIZE * (.12f + .76f * (i + hash(i, 0, 89) * .6f) / 22f)
            val py = TIP_SIZE / 2f + (hash(i, 1, 89) - .5f) * TIP_SIZE * .18f
            val rx = 2.5f + hash(i, 2, 89) * 3.5f; val ry = rx * (2.2f + hash(i, 3, 89) * 1.6f)
            val d = sqrt(((x - px) / rx).let { it * it } + ((y - py) / ry).let { it * it })
            coverage = max(coverage, smooth(1f - d, .35f) * (.6f + .4f * hash(i, 4, 89)))
        }
        return coverage
    }

    private fun charcoal(x: Float, y: Float): Float {
        val nx = abs(x + .5f - TIP_SIZE / 2f) / (TIP_SIZE * .42f); val ny = abs(y + .5f - TIP_SIZE / 2f) / (TIP_SIZE * .2f)
        val edge = 1f - max(nx, ny) + .25f * fbm(x * 2f, y * 2f, 97, 16, 4)
        return smooth(edge, .12f) * (.55f + .9f * fbm(x * 3f, y * 3f, 101, 32, 3)).coerceIn(0f, 1f)
    }

    private fun luminance(pixel: Int) = ((pixel ushr 16 and 255) * 299 + (pixel ushr 8 and 255) * 587 + (pixel and 255) * 114) / 1000

    private fun borderLuminance(image: SourcePixels): Int {
        val samples = mutableListOf<Int>()
        for (x in 0 until image.width) { samples += luminance(image.argb[x]); samples += luminance(image.argb[(image.height - 1) * image.width + x]) }
        for (y in 0 until image.height) { samples += luminance(image.argb[y * image.width]); samples += luminance(image.argb[y * image.width + image.width - 1]) }
        return samples.sorted()[samples.size / 2]
    }
}
