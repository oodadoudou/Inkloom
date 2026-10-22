package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.composite.ExtendedSrgb
import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** CSP Layer > Convert layer: expression colour. */
enum class LayerExpression { COLOR, GRAY, MONOCHROME }

/**
 * Settings of a layer conversion, applied in this order: a lower layer [resolution] (fraction of
 * the page resolution), the [expression] colour, then optionally a halftone of the shades with a
 * screen pitch of [tonePitch] pixels. Monochrome keeps black ink; light pixels become white, or
 * transparent with [whiteTransparent].
 */
data class LayerConversion(
    val expression: LayerExpression = LayerExpression.COLOR,
    val threshold: Float = .5f,
    val whiteTransparent: Boolean = true,
    val resolution: Float = 1f,
    val tonePitch: Float? = null,
    val toneAngle: Float = 45f,
) {
    init {
        require(threshold in 0f..1f && resolution in .02f..1f && (tonePitch == null || tonePitch in 2f..200f)) { "Layer conversion settings are out of range" }
    }
}

internal object RasterLayerConversion {
    fun apply(source: PixelTileSurfaceSnapshot, conversion: LayerConversion, selection: MaskTileSurfaceSnapshot?, cancelled: () -> Boolean): PixelTileSurface {
        val width = source.logicalWidth; val height = source.logicalHeight; val tile = source.tileSize
        val block = max(1, (1f / conversion.resolution).roundToInt())
        val output = PixelTileSurface(width, height, tile, pixelFormat = source.pixelFormat)
        val high = source.pixelFormat.isHighPrecision
        // Conversions only change existing ink; lowering resolution can spread it within one block.
        val keys = linkedSetOf<PixelTileKey>()
        val lastColumn = (width - 1) / tile; val lastRow = (height - 1) / tile
        source.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
            for (row in max(0, (info.key.row * tile - block) / tile)..min(lastRow, ((info.key.row + 1) * tile + block) / tile))
                for (column in max(0, (info.key.column * tile - block) / tile)..min(lastColumn, ((info.key.column + 1) * tile + block) / tile))
                    keys += PixelTileKey(column, row)
        }
        val radians = conversion.toneAngle * PI.toFloat() / 180f
        val c = cos(radians); val s = sin(radians)
        output.beginWrite("convert layer").use { write ->
            keys.forEach { key ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Image filter preview superseded")
                val left = key.column * tile; val top = key.row * tile
                val w = min(tile, width - left); val h = min(tile, height - top)
                val blocks = HashMap<Long, FloatArray>()
                fun averaged(x: Int, y: Int): FloatArray {
                    if (block == 1) return displayed(source, x, y)
                    val bx = x / block * block; val by = y / block * block
                    return blocks.getOrPut(bx.toLong() shl 32 or by.toLong()) {
                        var r = 0f; var g = 0f; var b = 0f; var a = 0f; var n = 0
                        for (yy in by until min(height, by + block)) for (xx in bx until min(width, bx + block)) {
                            val p = displayed(source, xx, yy); r += p[0] * p[3]; g += p[1] * p[3]; b += p[2] * p[3]; a += p[3]; n++
                        }
                        if (a <= 0f) floatArrayOf(0f, 0f, 0f, 0f) else floatArrayOf(r / a, g / a, b / a, a / n)
                    }
                }
                val packed = LongArray(tile * tile); val argb = IntArray(tile * tile)
                for (y in 0 until h) for (x in 0 until w) {
                    val px = left + x; val py = top + y
                    val old = displayed(source, px, py)
                    var (r, g, b, a) = averaged(px, py).let { listOf(it[0], it[1], it[2], it[3]) }
                    if (conversion.expression != LayerExpression.COLOR || conversion.tonePitch != null) {
                        val lum = (.2126f * r + .7152f * g + .0722f * b).coerceIn(0f, 1f)
                        r = lum; g = lum; b = lum
                    }
                    conversion.tonePitch?.let { pitch ->
                        // Shades become round dots whose area is their darkness, on a rotated screen.
                        val u = ((px + .5f) * c + (py + .5f) * s) / pitch; val v = (-(px + .5f) * s + (py + .5f) * c) / pitch
                        val fu = u - floor(u) - .5f; val fv = v - floor(v) - .5f
                        val darkness = 1f - r
                        val radius = sqrt(darkness / PI.toFloat())
                        var distance = hypot(fu, fv) - radius
                        for (nu in -1..1) for (nv in -1..1) if (nu != 0 || nv != 0) distance = min(distance, hypot(fu - nu, fv - nv) - radius)
                        val ink = (.5f - distance * pitch).coerceIn(0f, 1f)
                        r = 0f; g = 0f; b = 0f; a *= ink
                    }
                    if (conversion.expression == LayerExpression.MONOCHROME && conversion.tonePitch == null) {
                        // One bit per pixel: mostly transparent stays transparent, ink is solid black or white.
                        val dark = r < conversion.threshold
                        val level = if (dark) 0f else 1f
                        r = level; g = level; b = level
                        a = if (a < .5f || (!dark && conversion.whiteTransparent)) 0f else 1f
                    }
                    val weight = selection?.alphaAt((px.toLong() * selection.logicalWidth / width).toInt(),
                        (py.toLong() * selection.logicalHeight / height).toInt())?.div(255f) ?: 1f
                    fun mix(new: Float, previous: Float) = previous + (new - previous) * weight
                    val alpha = mix(a, old[3]).coerceIn(0f, 1f)
                    val cr = mix(r, old[0]); val cg = mix(g, old[1]); val cb = mix(b, old[2])
                    if (high) packed[y * tile + x] = if (alpha <= 0f) 0L else
                        PackedRgbaF16.packPremultiplied(ExtendedSrgb.decode(cr) * alpha, ExtendedSrgb.decode(cg) * alpha, ExtendedSrgb.decode(cb) * alpha, alpha)
                    else {
                        val a8 = (alpha * 255f).roundToInt()
                        fun channel(v: Float) = (v.coerceIn(0f, 1f) * a8).roundToInt().coerceIn(0, a8)
                        argb[y * tile + x] = if (a8 == 0) 0 else (a8 shl 24) or (channel(cr) shl 16) or (channel(cg) shl 8) or channel(cb)
                    }
                }
                if (high) write.editTileF16(key, PixelRect(0, 0, w, h)) { pixels, stride -> for (y in 0 until h) packed.copyInto(pixels, y * stride, y * tile, y * tile + w) }
                else write.editTile(key, PixelRect(0, 0, w, h)) { pixels, stride -> for (y in 0 until h) argb.copyInto(pixels, y * stride, y * tile, y * tile + w) }
            }
            write.commit().close()
        }
        return output
    }

    /** Unpremultiplied display-encoded colour and alpha. */
    private fun displayed(source: PixelTileSurfaceSnapshot, x: Int, y: Int): FloatArray {
        if (!source.pixelFormat.isHighPrecision) {
            val color = source.pixelAt(x, y); val alpha = color ushr 24 and 255
            if (alpha == 0) return floatArrayOf(0f, 0f, 0f, 0f)
            return floatArrayOf((color ushr 16 and 255) / alpha.toFloat(), (color ushr 8 and 255) / alpha.toFloat(), (color and 255) / alpha.toFloat(), alpha / 255f)
        }
        val p = source.linearPixelAt(x, y)
        if (p.alpha <= 0f) return floatArrayOf(0f, 0f, 0f, 0f)
        return floatArrayOf(ExtendedSrgb.encode(p.red / p.alpha), ExtendedSrgb.encode(p.green / p.alpha), ExtendedSrgb.encode(p.blue / p.alpha), p.alpha)
    }
}
