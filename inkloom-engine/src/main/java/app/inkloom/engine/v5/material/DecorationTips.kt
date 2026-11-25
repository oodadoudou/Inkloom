package app.inkloom.engine.v5.material

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * First-party decoration and nature tips, drawn by code (no imported images). Ribbon tips (lace,
 * scallop, hair, bark) are tall: a ribbon runs a tip's height along the stroke, so their patterns
 * repeat exactly over the tip's height and join seamlessly. Stamp tips (chain, stitch, pearl) lie
 * along x, which follows the stroke when the brush follows its direction.
 */
object DecorationTips {
    /** Lace: a band with eyelet holes and a scalloped outer edge, two scallops per tip. */
    fun lace(size: Int): BrushTipMask = tall(size) { x, y, w, h ->
        val period = h / 2f
        val phase = (y % period) / period * 2f - 1f
        val scallop = .52f * w + .44f * w * sqrt((1f - phase * phase).coerceAtLeast(0f))
        val body = cover(scallop - x)
        val eyelet = circle(x, y % period, .27f * w, period / 2f, .1f * w)
        val scallopHole = circle(x, y % period, .74f * w, period / 2f, .07f * w)
        body * (1f - eyelet) * (1f - scallopHole)
    }

    /** Scallop border: a solid band with half-round scallops, three per tip. */
    fun scallop(size: Int): BrushTipMask = tall(size) { x, y, w, h ->
        val period = h / 3f
        val phase = (y % period) / period * 2f - 1f
        cover(.45f * w + .5f * w * sqrt((1f - phase * phase).coerceAtLeast(0f)) - x)
    }

    /** Hair: nine fine strands of varied weight running the whole tip. */
    fun hair(size: Int, seed: Int): BrushTipMask = tall(size) { x, _, w, _ ->
        var value = 0f
        for (strand in 0 until 9) {
            val centre = (strand + .5f) / 9f * w + (noise(strand, 1, seed) - .5f) * w / 12f
            val weight = .45f + .55f * noise(strand, 2, seed)
            value = maxOf(value, weight * cover(.9f - abs(x - centre)))
        }
        value
    }

    /** Bark: fibrous vertical furrows whose width breathes along the tip, repeating over its height. */
    fun bark(size: Int, seed: Int): BrushTipMask = tall(size) { x, y, w, h ->
        val along = 2f * PI.toFloat() * y / h
        val ridges = abs(sin(x / w * 7f * PI.toFloat() + noise(1, 3, seed) * 6f + .6f * sin(along + noise(2, 3, seed) * 6f)))
        val grain = .75f + .25f * sin(along * 3f + x * .9f)
        val edge = cover(minOf(x, w - x) - 1f)
        edge * (.25f + .75f * ridges * ridges) * grain
    }

    /** Chain link: an oval ring lying along x. */
    fun chain(size: Int): BrushTipMask = square(size) { nx, ny ->
        val ring = sqrt((nx / .9f) * (nx / .9f) + (ny / .55f) * (ny / .55f))
        cover((.12f - abs(ring - .82f)) * size / 2f)
    }

    /** Stitch: one rounded dash along x, with the gap left by the brush's spacing. */
    fun stitch(size: Int): BrushTipMask = square(size) { nx, ny ->
        val along = (abs(nx) - .42f).coerceAtLeast(0f)
        cover((.13f - sqrt(along * along + ny * ny)) * size / 2f)
    }

    /** Pearl: a bead, thinner where light catches it, so it reads as round even in one colour. */
    fun pearl(size: Int): BrushTipMask = square(size) { nx, ny ->
        val distance = sqrt(nx * nx + ny * ny)
        val shine = sqrt((nx + .32f) * (nx + .32f) + (ny + .32f) * (ny + .32f))
        cover((.92f - distance) * size / 2f) * (.55f + .45f * distance).coerceAtMost(1f) * (.45f + .55f * (shine / .3f).coerceAtMost(1f))
    }

    /** Cloud puff: five soft overlapping circles, flatter at the bottom. */
    fun cloud(size: Int, seed: Int): BrushTipMask = square(size) { nx, ny ->
        var value = 0f
        for (lobe in 0 until 5) {
            val cx = (lobe - 2) * .3f + (noise(lobe, 4, seed) - .5f) * .12f
            val cy = -abs(lobe - 2) * -.1f - .08f + (noise(lobe, 5, seed) - .5f) * .1f
            val radius = .32f + .12f * noise(lobe, 6, seed) - abs(lobe - 2) * .04f
            val d = sqrt((nx - cx) * (nx - cx) + (ny - cy) * (ny - cy))
            value = maxOf(value, ((radius - d) / .14f).coerceIn(0f, 1f))
        }
        value * cover((.55f - ny) * size / 2f)
    }

    /** Leaf: a pointed oval standing up, with a pale midrib. */
    fun leaf(size: Int): BrushTipMask = square(size) { nx, ny ->
        val t = (ny + .9f) / 1.8f
        if (t !in 0f..1f) 0f else {
            val half = .42f * Math.pow(sin(PI * t), .8).toFloat()
            val blade = cover((half - abs(nx)) * size / 2f)
            val rib = 1f - .55f * cover((.03f - abs(nx)) * size / 2f)
            blade * rib
        }
    }

    /** Grass blade: a narrow curved spike rising from the bottom of the tip. */
    fun grass(size: Int, seed: Int): BrushTipMask = square(size) { nx, ny ->
        val t = (.95f - ny) / 1.9f
        if (t !in 0f..1f) 0f else {
            val bend = (noise(0, 7, seed) - .5f) * .7f * t * t
            val half = .1f * (1f - t)
            cover((half - abs(nx - bend)) * size / 2f + .5f)
        }
    }

    /** Denim twill: diagonal ribs with a slub in a soft square. */
    fun denim(size: Int, seed: Int): BrushTipMask = square(size) { nx, ny ->
        val twill = abs(sin((nx * 2.2f + ny) * 9f))
        val slub = .8f + .2f * sin(ny * 23f + noise(0, 8, seed) * 6f)
        val edge = ((.95f - maxOf(abs(nx), abs(ny))) / .12f).coerceIn(0f, 1f)
        edge * (.35f + .65f * twill) * slub
    }

    private fun circle(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Float =
        cover(radius - sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)))

    /** Signed distance in pixels to coverage, a one-pixel anti-aliased edge. */
    private fun cover(distance: Float): Float = (distance + .5f).coerceIn(0f, 1f)

    /** A tip half as wide as it is tall; [paint] gets pixel centres and the tip's width and height. */
    private inline fun tall(size: Int, paint: (Float, Float, Float, Float) -> Float): BrushTipMask {
        require(size in 16..1024)
        val width = size / 2; val height = size
        val values = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            values[y * width + x] = (255f * paint(x + .5f, y + .5f, width.toFloat(), height.toFloat()).coerceIn(0f, 1f) + .5f).toInt().toByte()
        }
        return BrushTipMask(width, height, values)
    }

    /** A square tip; [paint] gets coordinates in -1..1 with y pointing down. */
    private inline fun square(size: Int, paint: (Float, Float) -> Float): BrushTipMask {
        require(size in 16..1024)
        val values = ByteArray(size * size)
        val radius = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            values[y * size + x] = (255f * paint((x + .5f - radius) / radius, (y + .5f - radius) / radius).coerceIn(0f, 1f) + .5f).toInt().toByte()
        }
        return BrushTipMask(size, size, values)
    }

    private fun noise(x: Int, y: Int, seed: Int): Float {
        var value = x * 0x1f1f1f1f + y * 0x45d9f3b + seed
        value = value xor (value ushr 16)
        value *= 0x45d9f3b
        value = value xor (value ushr 16)
        return (value and 0xffff) / 65535f
    }
}
