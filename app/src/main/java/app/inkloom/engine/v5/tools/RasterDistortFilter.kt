package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** CSP Filter > Blur > Blur / Strong blur / Smoothing: fixed kernels without parameters. */
enum class FixedBlurKind { BLUR, STRONG_BLUR, SMOOTH }

/** CSP Filter > Distort. Each kind reads only the [DistortParameters] its dialog shows. */
enum class DistortKind { PINCH, CURVED_SURFACE, WAVE, TWIRL, PANORAMA, GEOMETRIC, POLAR, ZIGZAG, FISHEYE }

/** Shapes for [DistortKind.WAVE] and modes for [DistortKind.POLAR] and [DistortKind.CURVED_SURFACE]. */
object DistortModes {
    const val WAVE_SINE = 0; const val WAVE_TRIANGLE = 1; const val WAVE_SQUARE = 2
    const val POLAR_FROM_RECTANGULAR = 0; const val POLAR_TO_RECTANGULAR = 1; const val POLAR_SPHERE = 2
    const val SURFACE_CYLINDER = 0; const val SURFACE_SPHERE = 1
}

/**
 * Distortion settings in CSP's units: strengths and shapes in −100..100 percent, angles in
 * degrees, lengths in pixels. [radius] 0 means the whole selection (or layer); otherwise it is a
 * fraction of half the longer layer side.
 */
data class DistortParameters(
    val strength: Float = 50f,
    val centerX: Float = .5f,
    val centerY: Float = .5f,
    val radius: Float = 0f,
    val shape: Float = 0f,
    val angle: Float = 0f,
    val tension: Float = 0f,
    val count: Int = 3,
    val wavelengthMin: Float = 40f,
    val wavelengthMax: Float = 120f,
    val amplitudeMin: Float = 5f,
    val amplitudeMax: Float = 20f,
    val horizontalRatio: Float = 100f,
    val verticalRatio: Float = 100f,
    val mode: Int = 0,
    val wrap: Boolean = false,
    val scale: Float = 100f,
    val seed: Int = 1,
) {
    init {
        require(strength in -100f..100f && shape in -100f..100f && tension in 0f..100f) { "Distortion strength is outside -100..100" }
        require(centerX in 0f..1f && centerY in 0f..1f && radius in 0f..1f) { "Distortion centre or radius is outside the layer" }
        require(angle.isFinite() && angle in -360f..360f) { "Distortion angle is outside -360..360°" }
        require(count in 1..20) { "Number of waves must be between 1 and 20" }
        require(wavelengthMin in 2f..2000f && wavelengthMax in wavelengthMin..2000f) { "Wavelengths must be 2..2000 px, minimum first" }
        require(amplitudeMin in 0f..1000f && amplitudeMax in amplitudeMin..1000f) { "Amplitudes must be 0..1000 px, minimum first" }
        require(horizontalRatio in 0f..100f && verticalRatio in 0f..100f) { "Wave ratios must be 0..100%" }
        require(mode in 0..2 && scale in 10f..400f) { "Unknown distortion mode or scale" }
    }
}

/**
 * Fixed blurs, Perlin noise and the Distort family. Every output pixel is an inverse-mapped,
 * premultiplied bilinear sample of one immutable source generation, so results do not depend on
 * tile size, keep F16 range, and a selection limits where the result is written.
 */
internal object RasterDistortFilter {
    private enum class Edge { TRANSPARENT, WRAP, CLAMP }
    private val TRANSPARENT = LinearRgba(0f, 0f, 0f, 0f)
    /** sRGB grey to linear light; 4096 steps are finer than either 8-bit output or F16 noise needs. */
    private val SRGB_TO_LINEAR = FloatArray(4096) { index ->
        val grey = index / 4095f
        if (grey <= .04045f) grey / 12.92f else ((grey + .055f) / 1.055f).pow(2.4f)
    }

    /** Which tiles hold any ink, so samples of empty paper cost no pixel reads or allocations. */
    private class Occupancy(source: PixelTileSurfaceSnapshot) {
        private val tile = source.tileSize
        private val columns = (source.logicalWidth + tile - 1) / tile
        private val rows = (source.logicalHeight + tile - 1) / tile
        private val inked = BooleanArray(columns * rows).also { cells ->
            source.tileInfo.forEach { if (it.nonTransparentPixelCount > 0) cells[it.key.row * columns + it.key.column] = true }
        }
        fun at(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0) return false
            val column = x / tile; val row = y / tile
            return column < columns && row < rows && inked[row * columns + column]
        }
        fun any(left: Float, top: Float, right: Float, bottom: Float): Boolean {
            val c0 = max(0, floor(left / tile).toInt()); val c1 = min(columns - 1, floor(right / tile).toInt())
            val r0 = max(0, floor(top / tile).toInt()); val r1 = min(rows - 1, floor(bottom / tile).toInt())
            for (row in r0..r1) for (column in c0..c1) if (inked[row * columns + column]) return true
            return false
        }
    }

    /**
     * Whether a destination tile can only receive empty paper. The mapping is sampled on a grid,
     * and its bounding box is widened by the largest step between neighbouring samples, so a
     * seam or a sharp fold makes the box large rather than skipping a tile that has ink.
     */
    private fun receivesNothing(key: PixelTileKey, tile: Int, width: Int, height: Int, occupancy: Occupancy,
                                map: (Float, Float) -> Pair<Float, Float>?, wraps: Boolean): Boolean {
        if (occupancy.at(key.column * tile, key.row * tile)) return false
        val grid = 8; val w = min(tile, width - key.column * tile); val h = min(tile, height - key.row * tile)
        val xs = FloatArray((grid + 1) * (grid + 1)); val ys = FloatArray(xs.size); val valid = BooleanArray(xs.size)
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        for (j in 0..grid) for (i in 0..grid) {
            val index = j * (grid + 1) + i
            val point = map(key.column * tile + (w - 1) * i / grid.toFloat(), key.row * tile + (h - 1) * j / grid.toFloat()) ?: continue
            if (!point.first.isFinite() || !point.second.isFinite()) return false
            xs[index] = point.first; ys[index] = point.second; valid[index] = true
            left = min(left, point.first); right = max(right, point.first); top = min(top, point.second); bottom = max(bottom, point.second)
        }
        if (left == Float.MAX_VALUE) return true
        var step = 0f
        for (j in 0..grid) for (i in 0..grid) {
            val index = j * (grid + 1) + i
            if (!valid[index]) continue
            if (i < grid && valid[index + 1]) step = max(step, hypot(xs[index + 1] - xs[index], ys[index + 1] - ys[index]))
            if (j < grid && valid[index + grid + 1]) step = max(step, hypot(xs[index + grid + 1] - xs[index], ys[index + grid + 1] - ys[index]))
        }
        val margin = step + 2f
        left -= margin; top -= margin; right += margin; bottom += margin
        if (wraps && (left < 0 || top < 0 || right >= width || bottom >= height)) return false
        return !occupancy.any(left.coerceIn(0f, width - 1f), top.coerceIn(0f, height - 1f), right.coerceIn(0f, width - 1f), bottom.coerceIn(0f, height - 1f))
    }

    fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect, selection: MaskTileSurfaceSnapshot?,
              cancelled: () -> Boolean): PixelTileSurface {
        require(selection == null || (selection.logicalWidth > 0 && selection.logicalHeight > 0)) { "Selection mask has invalid dimensions" }
        val width = source.logicalWidth; val height = source.logicalHeight; val tile = source.tileSize
        val output = PixelTileSurface(width, height, tile, pixelFormat = source.pixelFormat)
        val occupancy = Occupancy(source)
        val keys = when (effect) {
            is RasterImageFilter.Effect.FixedBlur -> haloKeys(source, if (effect.kind == FixedBlurKind.STRONG_BLUR) 2 else 1)
            else -> allKeys(width, height, tile)
        }
        val mapping = (effect as? RasterImageFilter.Effect.Distort)?.let { mapping(source, it, selection) }
        val filter: (Int, Int) -> LinearRgba = when (effect) {
            is RasterImageFilter.Effect.FixedBlur -> fixedBlur(source, effect.kind)
            is RasterImageFilter.Effect.PerlinNoise -> perlin(effect)
            is RasterImageFilter.Effect.Distort -> requireNotNull(mapping).let { (map, edge) ->
                { x, y -> map(x.toFloat(), y.toFloat())?.let { (sx, sy) -> sample(source, occupancy, sx, sy, edge) } ?: TRANSPARENT }
            }
            else -> error("Unsupported effect for distortion pipeline")
        }
        output.beginWrite("distort filter").use { write ->
            val packed = LongArray(tile * tile)
            keys.forEach { key ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                if (mapping != null && receivesNothing(key, tile, width, height, occupancy, mapping.first, mapping.second == Edge.WRAP)) return@forEach
                val w = min(tile, width - key.column * tile); val h = min(tile, height - key.row * tile)
                for (y in 0 until h) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                    for (x in 0 until w) {
                        val px = key.column * tile + x; val py = key.row * tile + y
                        val old = if (occupancy.at(px, py)) source.linearPixelAt(px, py) else TRANSPARENT
                        val weight = selection?.alphaAt((px.toLong() * selection.logicalWidth / width).toInt(),
                            (py.toLong() * selection.logicalHeight / height).toInt())?.div(255f) ?: 1f
                        val new = if (weight == 0f) old else filter(px, py)
                        if (old === TRANSPARENT && new === TRANSPARENT) { packed[y * tile + x] = 0L; continue }
                        fun mix(f: (LinearRgba) -> Float) = f(old) + (f(new) - f(old)) * weight
                        val alpha = mix { it.alpha }.coerceIn(0f, 1f)
                        fun channel(f: (LinearRgba) -> Float) = mix(f).let { if (source.pixelFormat.isHighPrecision) it else it.coerceIn(0f, alpha) }
                        packed[y * tile + x] = PackedRgbaF16.packPremultiplied(channel { it.red }, channel { it.green }, channel { it.blue }, alpha)
                    }
                }
                if (source.pixelFormat.isHighPrecision) write.editTileF16(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) packed.copyInto(pixels, y * stride, y * tile, y * tile + w)
                } else write.editTile(key, PixelRect(0, 0, w, h)) { pixels, stride ->
                    for (y in 0 until h) for (x in 0 until w) pixels[y * stride + x] = PackedRgbaF16.toPremultipliedArgb8888(packed[y * tile + x])
                }
            }
            write.commit().close()
        }
        return output
    }

    private fun allKeys(width: Int, height: Int, tile: Int): List<PixelTileKey> =
        (0 until (height + tile - 1) / tile).flatMap { row -> (0 until (width + tile - 1) / tile).map { PixelTileKey(it, row) } }

    private fun haloKeys(source: PixelTileSurfaceSnapshot, halo: Int): Set<PixelTileKey> {
        val tile = source.tileSize; val keys = linkedSetOf<PixelTileKey>()
        val lastColumn = (source.logicalWidth - 1) / tile; val lastRow = (source.logicalHeight - 1) / tile
        source.tileInfo.forEach { info ->
            for (row in max(0, (info.key.row * tile - halo) / tile)..min(lastRow, ((info.key.row + 1) * tile + halo - 1) / tile))
                for (column in max(0, (info.key.column * tile - halo) / tile)..min(lastColumn, ((info.key.column + 1) * tile + halo - 1) / tile))
                    keys += PixelTileKey(column, row)
        }
        return keys
    }

    private fun fixedBlur(source: PixelTileSurfaceSnapshot, kind: FixedBlurKind): (Int, Int) -> LinearRgba {
        val radius = if (kind == FixedBlurKind.STRONG_BLUR) 2 else 1
        val weights = when (kind) {
            FixedBlurKind.SMOOTH -> floatArrayOf(1f, 2f, 1f, 2f, 4f, 2f, 1f, 2f, 1f).map { it / 16f }
            else -> List((radius * 2 + 1) * (radius * 2 + 1)) { 1f / ((radius * 2 + 1) * (radius * 2 + 1)) }
        }
        return { x, y ->
            var r = 0f; var g = 0f; var b = 0f; var a = 0f; var index = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val p = source.linearPixelAt(x + dx, y + dy); val k = weights[index++]
                r += p.red * k; g += p.green * k; b += p.blue * k; a += p.alpha * k
            }
            LinearRgba(r, g, b, a)
        }
    }

    private fun perlin(effect: RasterImageFilter.Effect.PerlinNoise): (Int, Int) -> LinearRgba {
        val permutation = IntArray(256) { it }.also { values ->
            val random = java.util.Random(effect.seed.toLong())
            for (i in 255 downTo 1) { val j = random.nextInt(i + 1); val t = values[i]; values[i] = values[j]; values[j] = t }
        }
        fun hash(x: Int, y: Int) = permutation[(permutation[x and 255] + y) and 255]
        fun fade(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)
        fun gradient(h: Int, x: Float, y: Float) = when (h and 7) {
            0 -> x + y; 1 -> -x + y; 2 -> x - y; 3 -> -x - y; 4 -> x; 5 -> -x; 6 -> y; else -> -y
        }
        fun noise(x: Float, y: Float): Float {
            val xi = floor(x).toInt(); val yi = floor(y).toInt(); val xf = x - xi; val yf = y - yi
            val u = fade(xf); val v = fade(yf)
            val top = gradient(hash(xi, yi), xf, yf) + u * (gradient(hash(xi + 1, yi), xf - 1, yf) - gradient(hash(xi, yi), xf, yf))
            val bottom = gradient(hash(xi, yi + 1), xf, yf - 1) + u * (gradient(hash(xi + 1, yi + 1), xf - 1, yf - 1) - gradient(hash(xi, yi + 1), xf, yf - 1))
            return top + v * (bottom - top)
        }
        return { x, y ->
            var value = 0f; var amplitude = 1f; var total = 0f; var frequency = 1f / effect.scale
            repeat(effect.octaves) {
                value += noise(x * frequency, y * frequency) * amplitude
                total += amplitude; amplitude *= effect.attenuation; frequency *= 2f
            }
            val grey = (.5f + value / total * effect.amplitude).coerceIn(0f, 1f)
            val linear = SRGB_TO_LINEAR[(grey * (SRGB_TO_LINEAR.size - 1)).roundToInt()]
            LinearRgba(linear, linear, linear, 1f)
        }
    }

    /** The inverse map from a destination pixel to its source point (null: transparent), and how edges sample. */
    private fun mapping(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect.Distort,
                        selection: MaskTileSurfaceSnapshot?): Pair<(Float, Float) -> Pair<Float, Float>?, Edge> {
        val p = effect.parameters
        val width = source.logicalWidth.toFloat(); val height = source.logicalHeight.toFloat()
        val cx = p.centerX * (width - 1); val cy = p.centerY * (height - 1)
        val area = selection?.let(::bounds)?.let { b -> RectF(b.left * width / selection.logicalWidth, b.top * height / selection.logicalHeight,
            b.right * width / selection.logicalWidth, b.bottom * height / selection.logicalHeight) } ?: RectF(0f, 0f, width, height)
        val (rx, ry) = if (p.radius == 0f) max(cx - area.left, area.right - cx).coerceAtLeast(1f) to max(cy - area.top, area.bottom - cy).coerceAtLeast(1f)
        else (p.radius * max(width, height) / 2f).coerceAtLeast(1f).let { radius ->
            radius * (1f - max(0f, p.shape) / 100f * .9f) to radius * (1f - max(0f, -p.shape) / 100f * .9f)
        }
        val waves = waves(p)
        val edge = if (effect.kind == DistortKind.WAVE) (if (p.wrap) Edge.WRAP else Edge.CLAMP) else Edge.TRANSPARENT
        val map: (Float, Float) -> Pair<Float, Float>? = when (effect.kind) {
            DistortKind.PINCH -> radial(cx, cy, rx, ry) { r -> r.pow(1f + p.strength / 100f * .9f) }
            DistortKind.FISHEYE -> { x, y ->
                val u = (x - cx) / rx; val v = (y - cy) / ry; val r = hypot(u, v)
                if (r >= 1f) null else {
                    val phi = max(p.strength, 0f) / 100f * 1.45f
                    val mapped = if (phi < 1e-3f) r else tan(r * phi) / tan(phi)
                    if (r < 1e-6f) cx to cy else cx + u / r * mapped * rx to cy + v / r * mapped * ry
                }
            }
            DistortKind.CURVED_SURFACE -> { x, y ->
                val amount = p.strength / 100f
                fun curve(t: Float): Float {
                    val inside = t.coerceIn(-1f, 1f)
                    val sphere = if (amount >= 0) asin(inside) / (PI.toFloat() / 2f) else sin(inside * PI.toFloat() / 2f)
                    return inside + (sphere - inside) * abs(amount)
                }
                val u = (x - cx) / rx; val v = (y - cy) / ry
                if (p.mode == DistortModes.SURFACE_CYLINDER) { if (abs(u) >= 1f) x to y else cx + curve(u) * rx to y }
                else { val r = hypot(u, v); if (r >= 1f || r < 1e-6f) x to y else (curve(r) / r).let { cx + u * it * rx to cy + v * it * ry } }
            }
            DistortKind.TWIRL -> { x, y ->
                val u = (x - cx) / rx; val v = (y - cy) / ry; val r = hypot(u, v)
                if (r >= 1f) x to y else {
                    val falloff = (1f - r).pow(1f + p.tension / 25f)
                    val turn = -p.strength * 3.6f * falloff * PI.toFloat() / 180f
                    val dx = x - cx; val dy = y - cy
                    cx + dx * cos(turn) - dy * sin(turn) to cy + dx * sin(turn) + dy * cos(turn)
                }
            }
            DistortKind.GEOMETRIC -> { x, y ->
                val reach = hypot(width, height) / 2f; val dx = x - cx; val dy = y - cy; val r = hypot(dx, dy) / reach
                val k = p.strength / 100f
                val factor = 1f / (1f + k * r * r) / (p.scale / 100f)
                cx + dx * factor to cy + dy * factor
            }
            DistortKind.PANORAMA -> { x, y ->
                val focal = width / 2f; val angle = p.angle * PI.toFloat() / 180f
                val dx = x - cx; val dy = y - cy
                val ux = dx * cos(angle) + dy * sin(angle); val uy = -dx * sin(angle) + dy * cos(angle)
                val theta = ux / focal
                if (abs(theta) >= 1.5f) null else {
                    val sx = focal * tan(theta); val sy = uy / cos(theta)
                    cx + sx * cos(angle) - sy * sin(angle) to cy + sx * sin(angle) + sy * cos(angle)
                }
            }
            DistortKind.POLAR -> polar(p.mode, width, height)
            DistortKind.WAVE -> { x, y ->
                var dx = 0f; var dy = 0f
                waves.forEach { wave ->
                    dx += wave.amplitude * shape(p.mode, y / wave.wavelength + wave.phase)
                    dy += wave.amplitude * shape(p.mode, x / wave.wavelength + wave.phase)
                }
                x + dx * p.horizontalRatio / 100f to y + dy * p.verticalRatio / 100f
            }
            DistortKind.ZIGZAG -> { x, y ->
                val angle = p.angle * PI.toFloat() / 180f; val directionX = cos(angle); val directionY = sin(angle)
                val extent = abs(width * directionX) + abs(height * directionY)
                val t = ((x - cx) * directionX + (y - cy) * directionY) / extent * p.count
                val offset = p.amplitudeMax * shape(DistortModes.WAVE_TRIANGLE, t)
                x - directionY * offset to y + directionX * offset
            }
        }
        return map to edge
    }

    private fun radial(cx: Float, cy: Float, rx: Float, ry: Float, curve: (Float) -> Float): (Float, Float) -> Pair<Float, Float>? = { x, y ->
        val u = (x - cx) / rx; val v = (y - cy) / ry; val r = hypot(u, v)
        if (r >= 1f || r < 1e-6f) x to y else (curve(r) / r).let { cx + u * it * rx to cy + v * it * ry }
    }

    private fun polar(mode: Int, width: Float, height: Float): (Float, Float) -> Pair<Float, Float>? {
        val cx = width / 2f; val cy = height / 2f; val reach = hypot(cx, cy)
        return when (mode) {
            DistortModes.POLAR_FROM_RECTANGULAR -> { x, y ->
                val angle = atan2(x - cx, -(y - cy)).let { if (it < 0) it + 2 * PI.toFloat() else it }
                angle / (2 * PI.toFloat()) * width to hypot(x - cx, y - cy) / reach * height
            }
            DistortModes.POLAR_TO_RECTANGULAR -> { x, y ->
                val angle = x / width * 2 * PI.toFloat(); val radius = y / height * reach
                cx + radius * sin(angle) to cy - radius * cos(angle)
            }
            else -> { x, y ->
                val radius = min(cx, cy); val u = (x - cx) / radius; val v = (y - cy) / radius; val r = hypot(u, v)
                if (r >= 1f) null else if (r < 1e-6f) cx to cy else (asin(r) / (PI.toFloat() / 2f) / r).let { cx + u * it * radius to cy + v * it * radius }
            }
        }
    }

    private data class Wave(val wavelength: Float, val amplitude: Float, val phase: Float)

    /** CSP's Regenerate picks each wave's length and height between the minimum and maximum. */
    private fun waves(p: DistortParameters): List<Wave> {
        val random = java.util.Random(p.seed.toLong())
        return List(p.count) {
            Wave(p.wavelengthMin + (p.wavelengthMax - p.wavelengthMin) * random.nextFloat(),
                (p.amplitudeMin + (p.amplitudeMax - p.amplitudeMin) * random.nextFloat()) / p.count, random.nextFloat())
        }
    }

    /** One period per unit: sine, triangle and square waves between −1 and 1. */
    private fun shape(mode: Int, t: Float): Float {
        val phase = t - floor(t)
        return when (mode) {
            DistortModes.WAVE_TRIANGLE -> if (phase < .5f) phase * 4f - 1f else 3f - phase * 4f
            DistortModes.WAVE_SQUARE -> if (phase < .5f) 1f else -1f
            else -> sin(phase * 2f * PI.toFloat())
        }
    }

    private fun sample(source: PixelTileSurfaceSnapshot, occupancy: Occupancy, x: Float, y: Float, edge: Edge): LinearRgba {
        val width = source.logicalWidth; val height = source.logicalHeight
        if (!x.isFinite() || !y.isFinite()) return TRANSPARENT
        fun placeX(px: Int) = when (edge) { Edge.TRANSPARENT -> px; Edge.WRAP -> px.mod(width); Edge.CLAMP -> px.coerceIn(0, width - 1) }
        fun placeY(py: Int) = when (edge) { Edge.TRANSPARENT -> py; Edge.WRAP -> py.mod(height); Edge.CLAMP -> py.coerceIn(0, height - 1) }
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val tx = x - ix; val ty = y - iy
        val x0 = placeX(ix); val x1 = placeX(ix + 1); val y0 = placeY(iy); val y1 = placeY(iy + 1)
        if (!occupancy.at(x0, y0) && !occupancy.at(x1, y0) && !occupancy.at(x0, y1) && !occupancy.at(x1, y1)) return TRANSPARENT
        fun pixel(px: Int, py: Int): LinearRgba = source.linearPixelAt(placeX(px), placeY(py))
        val a = pixel(ix, iy); val b = pixel(ix + 1, iy); val c = pixel(ix, iy + 1); val d = pixel(ix + 1, iy + 1)
        fun mix(f: (LinearRgba) -> Float) = f(a) * (1 - tx) * (1 - ty) + f(b) * tx * (1 - ty) + f(c) * (1 - tx) * ty + f(d) * tx * ty
        return LinearRgba(mix { it.red }, mix { it.green }, mix { it.blue }, mix { it.alpha })
    }

    private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Bounds of the selected pixels; a mask selected by default covers the whole layer. */
    private fun bounds(mask: MaskTileSurfaceSnapshot): RectF? {
        if (mask.defaultAlpha > 0) return null
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
        mask.tileInfo.filter { it.nonZeroAlphaCount > 0 }.forEach { info ->
            val alpha = mask.copyTileAlpha(info.key) ?: return@forEach
            for (y in 0 until mask.tileSize) for (x in 0 until mask.tileSize) if (alpha[y * mask.tileSize + x].toInt() != 0) {
                val px = info.key.column * mask.tileSize + x; val py = info.key.row * mask.tileSize + y
                if (px >= mask.logicalWidth || py >= mask.logicalHeight) continue
                left = min(left, px); top = min(top, py); right = max(right, px + 1); bottom = max(bottom, py + 1)
            }
        }
        return if (right < 0) null else RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
}
