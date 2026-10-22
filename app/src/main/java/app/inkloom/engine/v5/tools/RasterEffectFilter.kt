package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.composite.ExtendedSrgb
import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** CSP Filter > Effect > Artistic: what to process. */
enum class ArtisticProcess { COLOR_AND_LINES, COLOR_ONLY, LINES_ONLY }

/** CSP's Artistic settings; lengths in pixels, the rest in 0..100 percent. */
data class ArtisticParameters(
    val process: ArtisticProcess = ArtisticProcess.COLOR_AND_LINES,
    val lineWidth: Float = 2f,
    val lineSimplicity: Float = 40f,
    val lineDensity: Float = 50f,
    val lineOpacity: Float = 100f,
    val lineAntiAliasing: Float = 50f,
    val colorBlending: Float = 50f,
    val colorBlur: Float = 20f,
    val colors: Int = 8,
) {
    init {
        require(lineWidth in .5f..10f && colors in 2..64) { "Artistic line width or number of colors is out of range" }
        require(listOf(lineSimplicity, lineDensity, lineOpacity, lineAntiAliasing, colorBlending, colorBlur).all { it in 0f..100f }) {
            "Artistic settings must be between 0 and 100"
        }
    }
}

/** CSP Filter > Effect > Pencil drawing. */
data class PencilParameters(
    val outline: Boolean = true,
    val hatching: Boolean = true,
    val size: Float = 2f,
    val roughness: Float = 40f,
    val angle: Float = 45f,
    val grayscale: Boolean = true,
    val seed: Int = 1,
) {
    init { require(size in 1f..10f && roughness in 0f..100f && angle.isFinite()) { "Pencil settings are out of range" } }
}

/** CSP Filter > Effect > Film looks. */
enum class FilmLook { VINTAGE, MODERN, WARM }

data class FilmParameters(
    val look: FilmLook = FilmLook.VINTAGE,
    val intensity: Float = 70f,
    val aberration: Float = 30f,
    val noise: Float = 30f,
    val seed: Int = 1,
) {
    init { require(intensity in 0f..100f && aberration in 0f..100f && noise in 0f..100f) { "Film settings must be between 0 and 100" } }
}

/**
 * The Effect filters that need a neighbourhood: each output tile is computed from a padded,
 * immutable copy of its surroundings in display-encoded colour, so tiles join seamlessly, F16
 * keeps its range where an effect allows it, and a selection weights the result.
 */
internal object RasterEffectFilter {
    private class Region(val left: Int, val top: Int, val width: Int, val height: Int, val rgba: FloatArray) {
        /** Unpremultiplied display-encoded colour and alpha at absolute coordinates, edges clamped. */
        fun at(x: Int, y: Int, out: FloatArray) {
            val cx = (x - left).coerceIn(0, width - 1); val cy = (y - top).coerceIn(0, height - 1)
            rgba.copyInto(out, 0, (cy * width + cx) * 4, (cy * width + cx) * 4 + 4)
        }
        fun luminance(x: Int, y: Int): Float {
            val cx = (x - left).coerceIn(0, width - 1); val cy = (y - top).coerceIn(0, height - 1); val i = (cy * width + cx) * 4
            // Transparent paper reads as white, as when the layer is shown on paper.
            val a = rgba[i + 3]
            return (.2126f * rgba[i] + .7152f * rgba[i + 1] + .0722f * rgba[i + 2]) * a + (1f - a)
        }
    }

    fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect, selection: MaskTileSurfaceSnapshot?,
              cancelled: () -> Boolean): PixelTileSurface {
        val width = source.logicalWidth; val height = source.logicalHeight; val tile = source.tileSize
        val halo = when (effect) {
            is RasterImageFilter.Effect.RemoveJpegNoise -> 2
            is RasterImageFilter.Effect.Crystallize -> 0
            is RasterImageFilter.Effect.NormalMap -> 1
            is RasterImageFilter.Effect.Artistic -> (effect.parameters.colorBlur / 20f).roundToInt() + effect.parameters.lineWidth.roundToInt() + 6
            is RasterImageFilter.Effect.PencilDrawing -> 2
            is RasterImageFilter.Effect.Film -> ceil(effect.parameters.aberration / 10f).toInt() + 1
            else -> error("Unsupported effect")
        }
        // Normal maps and film looks colour the whole layer; the others only touch existing ink.
        val keys = when (effect) {
            is RasterImageFilter.Effect.NormalMap, is RasterImageFilter.Effect.Film -> allKeys(width, height, tile)
            is RasterImageFilter.Effect.Crystallize -> haloKeys(source, max(4, effect.cellSize.roundToInt() * 2))
            else -> haloKeys(source, halo)
        }
        val output = PixelTileSurface(width, height, tile, pixelFormat = source.pixelFormat)
        val high = source.pixelFormat.isHighPrecision
        output.beginWrite("effect filter").use { write ->
            val packed = LongArray(tile * tile)
            val argb = IntArray(tile * tile)
            keys.forEach { key ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                val left = key.column * tile; val top = key.row * tile
                val w = min(tile, width - left); val h = min(tile, height - top)
                val reach = if (effect is RasterImageFilter.Effect.Crystallize) effect.cellSize.roundToInt() * 2 + 2 else halo
                val region = region(source, left - reach, top - reach, w + reach * 2, h + reach * 2)
                val result = FloatArray(w * h * 4)
                when (effect) {
                    is RasterImageFilter.Effect.RemoveJpegNoise -> removeJpegNoise(region, left, top, w, h, result)
                    is RasterImageFilter.Effect.Crystallize -> crystallize(source, effect, left, top, w, h, result)
                    is RasterImageFilter.Effect.NormalMap -> normalMap(region, effect, left, top, w, h, result)
                    is RasterImageFilter.Effect.Artistic -> artistic(region, effect.parameters, left, top, w, h, result)
                    is RasterImageFilter.Effect.PencilDrawing -> pencil(region, effect.parameters, left, top, w, h, result)
                    is RasterImageFilter.Effect.Film -> film(region, effect.parameters, width, height, left, top, w, h, result)
                    else -> error("Unsupported effect")
                }
                val old = FloatArray(4)
                for (y in 0 until h) for (x in 0 until w) {
                    val px = left + x; val py = top + y; val i = (y * w + x) * 4
                    region.at(px, py, old)
                    val weight = selection?.alphaAt((px.toLong() * selection.logicalWidth / width).toInt(),
                        (py.toLong() * selection.logicalHeight / height).toInt())?.div(255f) ?: 1f
                    fun mix(c: Int) = old[c] + (result[i + c] - old[c]) * weight
                    val alpha = mix(3).coerceIn(0f, 1f)
                    if (high) {
                        fun linear(c: Int) = ExtendedSrgb.decode(mix(c)) * alpha
                        packed[y * tile + x] = if (alpha <= 0f) 0L else PackedRgbaF16.packPremultiplied(linear(0), linear(1), linear(2), alpha)
                    } else {
                        val a8 = (alpha * 255f).roundToInt()
                        fun channel(c: Int) = (mix(c).coerceIn(0f, 1f) * a8).roundToInt().coerceIn(0, a8)
                        argb[y * tile + x] = if (a8 == 0) 0 else (a8 shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                    }
                }
                if (high) write.editTileF16(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) packed.copyInto(pixels, y * stride, y * tile, y * tile + w)
                } else write.editTile(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) argb.copyInto(pixels, y * stride, y * tile, y * tile + w)
                }
            }
            write.commit().close()
        }
        return output
    }

    /** Unpremultiplied, display-encoded copy of a rectangle; outside the layer is transparent. */
    private fun region(source: PixelTileSurfaceSnapshot, left: Int, top: Int, width: Int, height: Int): Region {
        val rgba = FloatArray(width * height * 4)
        if (!source.pixelFormat.isHighPrecision) {
            // 8-bit pixels are already display-encoded: one bulk read, no linear round trip.
            val inside = PixelRect(left, top, left + width, top + height).intersect(PixelRect(0, 0, source.logicalWidth, source.logicalHeight))
            if (inside.isEmpty) return Region(left, top, width, height, rgba)
            val pixels = source.copyPremultipliedPixels(inside)
            for (y in 0 until inside.height) for (x in 0 until inside.width) {
                val color = pixels[y * inside.width + x]; val alpha = color ushr 24 and 255
                if (alpha == 0) continue
                val i = ((inside.top - top + y) * width + inside.left - left + x) * 4; val scale = 1f / alpha
                rgba[i] = (color ushr 16 and 255) * scale; rgba[i + 1] = (color ushr 8 and 255) * scale
                rgba[i + 2] = (color and 255) * scale; rgba[i + 3] = alpha / 255f
            }
            return Region(left, top, width, height, rgba)
        }
        for (y in 0 until height) for (x in 0 until width) {
            val p = source.linearPixelAt(left + x, top + y); val i = (y * width + x) * 4
            if (p.alpha <= 0f) continue
            rgba[i] = ExtendedSrgb.encode(p.red / p.alpha); rgba[i + 1] = ExtendedSrgb.encode(p.green / p.alpha)
            rgba[i + 2] = ExtendedSrgb.encode(p.blue / p.alpha); rgba[i + 3] = p.alpha
        }
        return Region(left, top, width, height, rgba)
    }

    private fun allKeys(width: Int, height: Int, tile: Int): List<PixelTileKey> =
        (0 until (height + tile - 1) / tile).flatMap { row -> (0 until (width + tile - 1) / tile).map { PixelTileKey(it, row) } }

    private fun haloKeys(source: PixelTileSurfaceSnapshot, halo: Int): Set<PixelTileKey> {
        val tile = source.tileSize; val keys = linkedSetOf<PixelTileKey>()
        val lastColumn = (source.logicalWidth - 1) / tile; val lastRow = (source.logicalHeight - 1) / tile
        source.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
            for (row in max(0, (info.key.row * tile - halo) / tile)..min(lastRow, ((info.key.row + 1) * tile + halo - 1) / tile))
                for (column in max(0, (info.key.column * tile - halo) / tile)..min(lastColumn, ((info.key.column + 1) * tile + halo - 1) / tile))
                    keys += PixelTileKey(column, row)
        }
        return keys
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var n = x * 73856093 xor (y * 19349663) xor (seed * 83492791)
        n = (n xor (n ushr 13)) * 1274126177
        return ((n ushr 1) and 0xFFFFFF) / 16777215f
    }

    /**
     * JPEG artefacts sit on the 8 × 8 block grid. Colours are smoothed only with neighbours of a
     * similar colour (an edge-preserving average), and more strongly across block boundaries, so
     * ringing and blocking soften while drawn edges stay sharp.
     */
    private fun removeJpegNoise(region: Region, left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val center = FloatArray(4); val other = FloatArray(4)
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x; val py = top + y
            region.at(px, py, center)
            val boundary = px % 8 == 0 || px % 8 == 7 || py % 8 == 0 || py % 8 == 7
            val radius = if (boundary) 2 else 1
            val tolerance = if (boundary) .14f else .08f
            var r = 0f; var g = 0f; var b = 0f; var a = 0f; var total = 0f
            for (dy in -radius..radius) for (dx in -radius..radius) {
                region.at(px + dx, py + dy, other)
                val difference = max(abs(other[0] - center[0]), max(abs(other[1] - center[1]), abs(other[2] - center[2])))
                val k = exp(-(difference * difference) / (2f * tolerance * tolerance)) * if (dx == 0 && dy == 0) 2f else 1f
                r += other[0] * k * other[3]; g += other[1] * k * other[3]; b += other[2] * k * other[3]; a += other[3] * k; total += k
            }
            val i = (y * w + x) * 4
            if (a <= 0f) { center.copyInto(out, i); continue }
            out[i] = r / a; out[i + 1] = g / a; out[i + 2] = b / a; out[i + 3] = center[3]
        }
    }

    /** Voronoi cells around jittered grid points; each cell takes the colour under its point. */
    private fun crystallize(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect.Crystallize,
                            left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val width = source.logicalWidth; val height = source.logicalHeight
        val columns = if (effect.tiling) max(1, (width / effect.cellSize).roundToInt()) else Int.MAX_VALUE
        val rows = if (effect.tiling) max(1, (height / effect.cellSize).roundToInt()) else Int.MAX_VALUE
        val cellX = if (effect.tiling) width.toFloat() / columns else effect.cellSize
        val cellY = if (effect.tiling) height.toFloat() / rows else effect.cellSize
        val jitter = effect.randomness / 100f
        val colour = FloatArray(4)
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x + .5f; val py = top + y + .5f
            val cx = floor(px / cellX).toInt(); val cy = floor(py / cellY).toInt()
            var best = Float.MAX_VALUE; var bx = 0f; var by = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val wx = if (effect.tiling) (cx + dx).mod(columns) else cx + dx; val wy = if (effect.tiling) (cy + dy).mod(rows) else cy + dy
                val sx = (cx + dx + .5f + (hash(wx, wy, effect.seed) - .5f) * jitter) * cellX
                val sy = (cy + dy + .5f + (hash(wy, wx, effect.seed + 7) - .5f) * jitter) * cellY
                val d = (sx - px) * (sx - px) + (sy - py) * (sy - py)
                if (d < best) { best = d; bx = sx; by = sy }
            }
            val sampleX = if (effect.tiling) floor(bx).toInt().mod(width) else floor(bx).toInt().coerceIn(0, width - 1)
            val sampleY = if (effect.tiling) floor(by).toInt().mod(height) else floor(by).toInt().coerceIn(0, height - 1)
            if (!source.pixelFormat.isHighPrecision) {
                val color = source.pixelAt(sampleX, sampleY); val alpha = color ushr 24 and 255
                if (alpha == 0) colour.fill(0f) else {
                    colour[0] = (color ushr 16 and 255) / alpha.toFloat(); colour[1] = (color ushr 8 and 255) / alpha.toFloat()
                    colour[2] = (color and 255) / alpha.toFloat(); colour[3] = alpha / 255f
                }
                colour.copyInto(out, (y * w + x) * 4); continue
            }
            val p = source.linearPixelAt(sampleX, sampleY)
            if (p.alpha > 0f) {
                colour[0] = ExtendedSrgb.encode(p.red / p.alpha); colour[1] = ExtendedSrgb.encode(p.green / p.alpha)
                colour[2] = ExtendedSrgb.encode(p.blue / p.alpha); colour[3] = p.alpha
            } else colour.fill(0f)
            colour.copyInto(out, (y * w + x) * 4)
        }
    }

    /** Luminance is the height; Sobel slopes give the surface normal, encoded as RGB 0..1. */
    private fun normalMap(region: Region, effect: RasterImageFilter.Effect.NormalMap, left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val strength = effect.strength / 10f
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x; val py = top + y
            fun l(dx: Int, dy: Int) = 1f - region.luminance(px + dx, py + dy)
            val sx = (l(1, -1) + 2 * l(1, 0) + l(1, 1)) - (l(-1, -1) + 2 * l(-1, 0) + l(-1, 1))
            val sy = (l(-1, 1) + 2 * l(0, 1) + l(1, 1)) - (l(-1, -1) + 2 * l(0, -1) + l(1, -1))
            var nx = -sx * strength; var ny = (if (effect.yUp) sy else -sy) * strength; var nz = 1f
            val length = sqrt(nx * nx + ny * ny + nz * nz); nx /= length; ny /= length; nz /= length
            val i = (y * w + x) * 4
            out[i] = nx * .5f + .5f; out[i + 1] = ny * .5f + .5f; out[i + 2] = nz * .5f + .5f; out[i + 3] = 1f
        }
    }

    private fun artistic(region: Region, p: ArtisticParameters, left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val blur = (p.colorBlur / 20f).roundToInt()
        val tolerance = .04f + p.colorBlending / 100f * .3f
        val levels = max(2, p.colors.toFloat().pow(1f / 3f).roundToInt() + 1)
        val threshold = .5f - p.lineDensity / 100f * .45f
        val smoothing = (p.lineSimplicity / 34f).roundToInt()
        val softness = .02f + p.lineAntiAliasing / 100f * .25f
        val reach = max(0, p.lineWidth.roundToInt() - 1)
        val lines = if (p.process == ArtisticProcess.COLOR_ONLY) null else lineMap(region, smoothing, reach)
        val center = FloatArray(4); val other = FloatArray(4)
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x; val py = top + y; val i = (y * w + x) * 4
            region.at(px, py, center)
            var r = center[0]; var g = center[1]; var b = center[2]; var a = center[3]
            if (p.process != ArtisticProcess.LINES_ONLY && a > 0f) {
                var sr = 0f; var sg = 0f; var sb = 0f; var total = 0f
                for (dy in -blur..blur) for (dx in -blur..blur) {
                    region.at(px + dx, py + dy, other)
                    if (other[3] <= 0f) continue
                    val difference = max(abs(other[0] - center[0]), max(abs(other[1] - center[1]), abs(other[2] - center[2])))
                    val k = exp(-(difference * difference) / (2f * tolerance * tolerance)) * other[3]
                    sr += other[0] * k; sg += other[1] * k; sb += other[2] * k; total += k
                }
                if (total > 0f) { r = sr / total; g = sg / total; b = sb / total }
                fun quantize(v: Float) = (v.coerceIn(0f, 1f) * (levels - 1)).roundToInt().toFloat() / (levels - 1)
                r = quantize(r); g = quantize(g); b = quantize(b)
            }
            if (lines != null) {
                val strongest = lines[(py - region.top) * region.width + (px - region.left)]
                val line = (((strongest - threshold) / softness) + .5f).coerceIn(0f, 1f) * p.lineOpacity / 100f
                if (p.process == ArtisticProcess.LINES_ONLY) { r = 0f; g = 0f; b = 0f; a = line }
                else { r *= 1f - line; g *= 1f - line; b *= 1f - line; a = max(a, line) }
            }
            out[i] = r; out[i + 1] = g; out[i + 2] = b; out[i + 3] = a
        }
    }

    /**
     * Edge strength over the padded region: luminance smoothed by [smoothing] (simpler lines
     * join more), Sobel magnitude, then widened by a separable maximum of [reach] pixels.
     */
    private fun lineMap(region: Region, smoothing: Int, reach: Int): FloatArray {
        val w = region.width; val h = region.height
        var lum = FloatArray(w * h) { region.luminance(region.left + it % w, region.top + it / w) }
        if (smoothing > 0) {
            val horizontal = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) { var sum = 0f; for (d in -smoothing..smoothing) sum += lum[y * w + (x + d).coerceIn(0, w - 1)]; horizontal[y * w + x] = sum / (smoothing * 2 + 1) }
            lum = FloatArray(w * h) { i -> val x = i % w; val y = i / w; var sum = 0f; for (d in -smoothing..smoothing) sum += horizontal[(y + d).coerceIn(0, h - 1) * w + x]; sum / (smoothing * 2 + 1) }
        }
        fun l(x: Int, y: Int) = lum[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        var edge = FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            val sx = (l(x + 1, y - 1) + 2 * l(x + 1, y) + l(x + 1, y + 1)) - (l(x - 1, y - 1) + 2 * l(x - 1, y) + l(x - 1, y + 1))
            val sy = (l(x - 1, y + 1) + 2 * l(x, y + 1) + l(x + 1, y + 1)) - (l(x - 1, y - 1) + 2 * l(x, y - 1) + l(x + 1, y - 1))
            sqrt(sx * sx + sy * sy) / 4f
        }
        if (reach > 0) {
            val horizontal = FloatArray(w * h) { i -> val x = i % w; val y = i / w; var m = 0f; for (d in -reach..reach) m = max(m, edge[y * w + (x + d).coerceIn(0, w - 1)]); m }
            edge = FloatArray(w * h) { i -> val x = i % w; val y = i / w; var m = 0f; for (d in -reach..reach) m = max(m, horizontal[(y + d).coerceIn(0, h - 1) * w + x]); m }
        }
        return edge
    }

    /** Graphite on white paper: hatching follows tone in one to three directions, outlines follow edges. */
    private fun pencil(region: Region, p: PencilParameters, left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val spacing = p.size * 2.5f + 1.5f
        val center = FloatArray(4)
        fun stroke(px: Int, py: Int, degrees: Float, darkness: Float, layer: Int): Float {
            val a = degrees * PI.toFloat() / 180f
            val u = -px * sin(a) + py * cos(a); val v = px * cos(a) + py * sin(a)
            val line = floor(u / spacing).toInt()
            val phase = abs(u / spacing - line - .5f) * 2f
            // Roughness breaks strokes into dashes of varying length and pressure.
            val dash = hash(line, floor(v / (8f + 40f * (1f - p.roughness / 100f))).toInt(), p.seed + layer)
            val pressure = 1f - p.roughness / 100f * .6f * dash
            val width = (p.size / spacing).coerceIn(.15f, .9f)
            return if (phase < width && darkness > layer * .28f) pressure * (1f - phase / width) * .85f else 0f
        }
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x; val py = top + y; val i = (y * w + x) * 4
            region.at(px, py, center)
            val darkness = 1f - region.luminance(px, py)
            var graphite = 0f
            if (p.hatching) for (layer in 0..2) graphite = max(graphite, stroke(px, py, p.angle + layer * 45f, darkness, layer) * darkness.coerceIn(.2f, 1f))
            if (p.outline) {
                fun l(dx: Int, dy: Int) = region.luminance(px + dx, py + dy)
                val sx = (l(1, -1) + 2 * l(1, 0) + l(1, 1)) - (l(-1, -1) + 2 * l(-1, 0) + l(-1, 1))
                val sy = (l(-1, 1) + 2 * l(0, 1) + l(1, 1)) - (l(-1, -1) + 2 * l(0, -1) + l(1, -1))
                graphite = max(graphite, (sqrt(sx * sx + sy * sy) / 2f).coerceIn(0f, .95f))
            }
            val tint = if (p.grayscale) floatArrayOf(.18f, .18f, .2f) else floatArrayOf(center[0] * .7f, center[1] * .7f, center[2] * .7f)
            out[i] = 1f + (tint[0] - 1f) * graphite; out[i + 1] = 1f + (tint[1] - 1f) * graphite; out[i + 2] = 1f + (tint[2] - 1f) * graphite
            out[i + 3] = center[3]
        }
    }

    /** Film looks: tone curve and grade per look, lateral colour fringing and seeded grain, with a soft vignette. */
    private fun film(region: Region, p: FilmParameters, width: Int, height: Int, left: Int, top: Int, w: Int, h: Int, out: FloatArray) {
        val amount = p.intensity / 100f
        val shift = (p.aberration / 10f).roundToInt()
        val grain = p.noise / 100f * .18f
        val center = FloatArray(4); val red = FloatArray(4); val blue = FloatArray(4)
        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x; val py = top + y; val i = (y * w + x) * 4
            region.at(px, py, center)
            if (center[3] <= 0f) { out[i + 3] = 0f; continue }
            region.at(px + shift, py, red); region.at(px - shift, py, blue)
            var r = if (red[3] > 0f) red[0] else center[0]; var g = center[1]; var b = if (blue[3] > 0f) blue[2] else center[2]
            val (lift, warmth, fade) = when (p.look) {
                FilmLook.VINTAGE -> Triple(.08f, .06f, .12f)
                FilmLook.MODERN -> Triple(.04f, -.03f, .05f)
                FilmLook.WARM -> Triple(.06f, .14f, .08f)
            }
            fun tone(v: Float) = lift + (1f - lift - fade) * (v * v * (3f - 2f * v))
            var tr = tone(r.coerceIn(0f, 1f)) + warmth; var tg = tone(g.coerceIn(0f, 1f)) + warmth * .4f; var tb = tone(b.coerceIn(0f, 1f)) - warmth
            if (p.look == FilmLook.WARM) { val l = .3f * tr + .59f * tg + .11f * tb; tr = l + .12f; tg = l + .04f; tb = l - .08f }
            val dx = (px + .5f) / width - .5f; val dy = (py + .5f) / height - .5f
            val vignette = 1f - (dx * dx + dy * dy) * 1.2f * amount
            val n = if (p.look == FilmLook.WARM) 0f else (hash(px, py, p.seed) - .5f) * grain
            r = r + ((tr * vignette + n) - r) * amount; g = g + ((tg * vignette + n) - g) * amount; b = b + ((tb * vignette + n) - b) * amount
            out[i] = r.coerceIn(0f, 1f); out[i + 1] = g.coerceIn(0f, 1f); out[i + 2] = b.coerceIn(0f, 1f); out[i + 3] = center[3]
        }
    }
}
