package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Placement is relative to the selection's half-coverage contour, in destination pixel units. */
enum class SelectionStrokePlacement { INSIDE, CENTER, OUTSIDE }

/**
 * Bounded signed-distance coverage. Each output tile owns a complete source halo, so disconnected
 * islands, holes and tile boundaries use the same contour. No full-page bitmap or distance field
 * is allocated, including for an implicit full-page/inverted selection.
 */
object SelectionStrokeMask {
    fun create(
        selection: MaskTileSurfaceSnapshot,
        width: Int,
        height: Int,
        strokeWidth: Float,
        placement: SelectionStrokePlacement,
        tileSize: Int = MaskTileSurface.DEFAULT_TILE_SIZE,
        cancelled: () -> Boolean = { false },
    ): MaskTileSurface {
        require(width > 0 && height > 0)
        require(strokeWidth.isFinite() && strokeWidth > 0f && strokeWidth <= 256f)
        val output = MaskTileSurface(width, height, tileSize)
        if (selection.isEmpty) return output
        val reach = if (placement == SelectionStrokePlacement.CENTER) strokeWidth / 2f else strokeWidth
        val halo = ceil(reach + 2f).toInt()
        val logical = PixelRect(0, 0, width, height)
        val keys = linkedSetOf<PixelTileKey>()
        fun addRegion(region: PixelRect) {
            val clipped = region.intersect(logical)
            if (clipped.isEmpty) return
            for (row in clipped.top / tileSize..(clipped.bottom - 1) / tileSize)
                for (column in clipped.left / tileSize..(clipped.right - 1) / tileSize)
                    keys.add(PixelTileKey(column, row))
        }
        // Explicit mask tiles may describe selected islands or holes in an inverted selection.
        for (info in selection.tileInfo) {
            val l = floor(info.key.column * selection.tileSize.toDouble() * width / selection.logicalWidth).toInt()
            val t = floor(info.key.row * selection.tileSize.toDouble() * height / selection.logicalHeight).toInt()
            val r = ceil(minOf((info.key.column + 1) * selection.tileSize, selection.logicalWidth).toDouble() * width / selection.logicalWidth).toInt()
            val b = ceil(minOf((info.key.row + 1) * selection.tileSize, selection.logicalHeight).toDouble() * height / selection.logicalHeight).toInt()
            addRegion(PixelRect(l - halo, t - halo, r + halo, b + halo))
        }
        if (selection.defaultAlpha >= 128) {
            addRegion(PixelRect(0, 0, width, minOf(height, halo)))
            addRegion(PixelRect(0, maxOf(0, height - halo), width, height))
            addRegion(PixelRect(0, 0, minOf(width, halo), height))
            addRegion(PixelRect(maxOf(0, width - halo), 0, width, height))
        }
        fun alpha(x: Int, y: Int): Float {
            if (x !in 0 until width || y !in 0 until height) return 0f
            val sx = (x + .5f) * selection.logicalWidth / width - .5f
            val sy = (y + .5f) * selection.logicalHeight / height - .5f
            val ix = floor(sx).toInt(); val iy = floor(sy).toInt()
            val fx = sx - ix; val fy = sy - iy
            fun sample(dx: Int, dy: Int) = selection.alphaAt((ix + dx).coerceIn(0, selection.logicalWidth - 1),
                (iy + dy).coerceIn(0, selection.logicalHeight - 1)) / 255f
            val top = sample(0, 0) * (1 - fx) + sample(1, 0) * fx
            val bottom = sample(0, 1) * (1 - fx) + sample(1, 1) * fx
            return top * (1 - fy) + bottom * fy
        }
        output.beginWrite("selection stroke coverage").use { write ->
            for (key in keys) {
                if (cancelled()) throw CancellationException("Selection stroke cancelled")
                val left = key.column * tileSize; val top = key.row * tileSize
                val tw = minOf(tileSize, width - left); val th = minOf(tileSize, height - top)
                val rw = tw + 2 * halo; val rh = th + 2 * halo
                val samples = FloatArray(rw * rh)
                for (y in 0 until rh) {
                    if (cancelled()) throw CancellationException("Selection stroke cancelled")
                    for (x in 0 until rw) samples[y * rw + x] = alpha(left + x - halo, top + y - halo)
                }
                val toInside = distances(samples, rw, rh, true)
                val toOutside = distances(samples, rw, rh, false)
                val coverage = ByteArray(tw * th)
                var nonzero = false
                val low = if (placement == SelectionStrokePlacement.INSIDE) 0f else -reach
                val high = if (placement == SelectionStrokePlacement.OUTSIDE) 0f else reach
                for (y in 0 until th) for (x in 0 until tw) {
                    val index = (y + halo) * rw + x + halo
                    val a = samples[index]
                    val inside = a >= .5f
                    // Fractional source coverage shifts the local contour without changing width.
                    val signed = if (inside) sqrt(toOutside[index]) - .5f + a - 1f
                        else .5f - sqrt(toInside[index]) + a
                    val value = (minOf(signed - low + .5f, high - signed + .5f).coerceIn(0f, 1f) * 255).roundToInt()
                    coverage[y * tw + x] = value.toByte()
                    nonzero = nonzero || value > 0
                }
                if (nonzero) write.composeAlpha(PixelRect(left, top, left + tw, top + th), coverage, mode = MaskComposeMode.REPLACE)
            }
            write.commit().close()
        }
        return output
    }

    /** Separable squared Euclidean distance transform; scratch is bounded by one tile plus halo. */
    private fun distances(alpha: FloatArray, width: Int, height: Int, targetInside: Boolean): FloatArray {
        val data = FloatArray(alpha.size) { if ((alpha[it] >= .5f) == targetInside) 0f else 1e9f }
        val size = maxOf(width, height)
        val input = FloatArray(size); val result = FloatArray(size)
        val vertices = IntArray(size); val breaks = DoubleArray(size + 1)
        fun line(n: Int) {
            var k = 0
            vertices[0] = 0; breaks[0] = Double.NEGATIVE_INFINITY; breaks[1] = Double.POSITIVE_INFINITY
            for (q in 1 until n) {
                var v = vertices[k]
                var crossing = (input[q].toDouble() + q.toDouble() * q - input[v] - v.toDouble() * v) / (2.0 * (q - v))
                while (crossing <= breaks[k]) {
                    k--; v = vertices[k]
                    crossing = (input[q].toDouble() + q.toDouble() * q - input[v] - v.toDouble() * v) / (2.0 * (q - v))
                }
                k++; vertices[k] = q; breaks[k] = crossing; breaks[k + 1] = Double.POSITIVE_INFINITY
            }
            k = 0
            for (q in 0 until n) {
                while (breaks[k + 1] < q) k++
                val d = q - vertices[k]
                result[q] = d.toFloat() * d + input[vertices[k]]
            }
        }
        for (y in 0 until height) {
            data.copyInto(input, 0, y * width, (y + 1) * width); line(width)
            result.copyInto(data, y * width, 0, width)
        }
        for (x in 0 until width) {
            for (y in 0 until height) input[y] = data[y * width + x]
            line(height)
            for (y in 0 until height) data[y * width + x] = result[y]
        }
        return data
    }
}
