package app.inkloom.document

import app.inkloom.engine.v5.composite.ExtendedSrgb
import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders a fill, gradient or tone layer's pixels for a page of [width] × [height] pixels at
 * [dpi]. Colours are straight sRGB; F16 pages receive the same colours in linear light.
 */
internal object GeneratedLayerRenderer {
    fun render(generator: LayerGenerator, width: Int, height: Int, dpi: Float, format: PixelTileFormat): PixelTileSurface {
        require(width in 1..8192 && height in 1..8192) { "Generated layers support up to 8192 pixels per side" }
        val colour: (Float, Float) -> Int = when (generator) {
            is LayerGenerator.Fill -> { _, _ -> generator.colorArgb.toInt() }
            is LayerGenerator.Gradient -> gradient(generator, width, height)
            is LayerGenerator.Tone -> tone(generator, dpi)
        }
        val surface = PixelTileSurface(width, height, pixelFormat = format)
        val tile = surface.tileSize
        surface.beginWrite("generated layer").use { write ->
            for (row in 0 until (height + tile - 1) / tile) for (column in 0 until (width + tile - 1) / tile) {
                val key = PixelTileKey(column, row)
                val w = min(tile, width - column * tile); val h = min(tile, height - row * tile)
                if (format.isHighPrecision) write.editTileF16(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) for (x in 0 until w) {
                        val argb = colour(column * tile + x + .5f, row * tile + y + .5f)
                        val alpha = (argb ushr 24 and 255) / 255f
                        fun linear(shift: Int) = ExtendedSrgb.decode((argb ushr shift and 255) / 255f) * alpha
                        pixels[y * stride + x] = if (alpha == 0f) 0L else PackedRgbaF16.packPremultiplied(linear(16), linear(8), linear(0), alpha)
                    }
                } else write.editTile(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) for (x in 0 until w) pixels[y * stride + x] = premultiply(colour(column * tile + x + .5f, row * tile + y + .5f))
                }
            }
            write.commit().close()
        }
        return surface
    }

    private fun premultiply(argb: Int): Int {
        val a = argb ushr 24 and 255
        if (a == 255) return argb
        if (a == 0) return 0
        fun channel(shift: Int) = ((argb ushr shift and 255) * a + 127) / 255
        return (a shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Position along the gradient for each of Inkloom's six gradient shapes. */
    private fun gradient(generator: LayerGenerator.Gradient, width: Int, height: Int): (Float, Float) -> Int {
        val definition = generator.definition.normalized()
        val stops = GradientColors.resolved(definition)
        val sx = generator.startX * width; val sy = generator.startY * height
        val dx = generator.endX * width - sx; val dy = generator.endY * height - sy
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / length; val uy = dy / length
        val baseAngle = atan2(dy, dx)
        return { x, y ->
            val px = x - sx; val py = y - sy
            val along = (px * ux + py * uy) / length
            val across = (-px * uy + py * ux) / length
            val position = when (definition.kind) {
                GradientKind.LINEAR -> along
                GradientKind.REFLECTED -> abs(along)
                GradientKind.RADIAL -> hypot(px, py) / length
                GradientKind.ELLIPSE -> hypot(along, across / definition.ellipseRatio)
                GradientKind.ANGULAR -> ((atan2(py, px) - baseAngle) / (2 * PI.toFloat())).let { it - floor(it) }
                GradientKind.DIAMOND -> abs(along) + abs(across)
            }
            GradientColors.sample(stops, position, definition.edgeMode)
        }
    }

    /**
     * An amplitude-modulated halftone: a screen of [LayerGenerator.Tone.linesPerInch] cells at the
     * page DPI, rotated by the screen angle, each cell covered to the requested density by its dot.
     * Coverage is computed analytically with a one-pixel soft edge, so dots stay round when small.
     */
    private fun tone(generator: LayerGenerator.Tone, dpi: Float): (Float, Float) -> Int {
        val pitch = (dpi.coerceIn(1f, 2400f) / generator.linesPerInch.coerceIn(5f, 300f)).coerceAtLeast(2f)
        val density = generator.density.coerceIn(0f, 1f)
        val radians = generator.angleDegrees * PI.toFloat() / 180f
        val c = cos(radians); val s = sin(radians)
        val color = generator.colorArgb.toInt() and 0xFFFFFF
        val baseAlpha = (generator.colorArgb ushr 24 and 255).toInt()
        // Size of the dot (in cell units) that covers [density] of its cell.
        val size = when (generator.dot) {
            ToneDotShape.CIRCLE -> sqrt(density / PI.toFloat()) * 2f
            ToneDotShape.SQUARE -> sqrt(density)
            // An L1 dot of radius a covers 2a² of its cell, or 1 − 2(1 − a)² once neighbours merge.
            ToneDotShape.DIAMOND -> sqrt(2f) * if (density <= .5f) sqrt(density / 2f) else 1f - sqrt((1f - density) / 2f)
            ToneDotShape.LINE -> density
            ToneDotShape.CROSS -> 1f - sqrt(1f - density)
        }
        val soft = 1f / pitch
        return { x, y ->
            val u = (x * c + y * s) / pitch; val v = (-x * s + y * c) / pitch
            val fu = u - floor(u) - .5f; val fv = v - floor(v) - .5f
            // Signed distance to the nearest dot edge, in cell units (negative inside). Large
            // round and diamond dots reach into neighbouring cells and merge, as on a real screen.
            fun edge(du: Float, dv: Float) = when (generator.dot) {
                ToneDotShape.CIRCLE -> hypot(du, dv) - size / 2f
                ToneDotShape.SQUARE -> max(abs(du), abs(dv)) - size / 2f
                ToneDotShape.DIAMOND -> (abs(du) + abs(dv)) / sqrt(2f) - size / 2f
                ToneDotShape.LINE -> abs(dv) - size / 2f
                ToneDotShape.CROSS -> min(abs(du), abs(dv)) - size / 2f
            }
            var distance = edge(fu, fv)
            if (generator.dot == ToneDotShape.CIRCLE || generator.dot == ToneDotShape.DIAMOND)
                for (nu in -1..1) for (nv in -1..1) if (nu != 0 || nv != 0) distance = min(distance, edge(fu - nu, fv - nv))
            val coverage = when {
                density >= 1f -> 1f
                density <= 0f -> 0f
                else -> (.5f - distance / soft).coerceIn(0f, 1f)
            }
            val alpha = (coverage * baseAlpha).roundToInt()
            if (alpha == 0) 0 else (alpha shl 24) or color
        }
    }
}
