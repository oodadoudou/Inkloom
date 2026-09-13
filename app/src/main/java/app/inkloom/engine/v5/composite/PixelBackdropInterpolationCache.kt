package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.*
import kotlin.math.roundToInt

/** A non-isolated group's contribution is applied once to its original backdrop, in premultiplied space. */
internal class PixelBackdropInterpolationCache(
    private val width: Int,
    private val height: Int,
    private val tileSize: Int,
    private val format: PixelTileFormat,
    surfaceId: String,
) {
    private val output = PixelTileSurface(width, height, tileSize, surfaceId, pixelFormat = format)
    private val zero8 = IntArray(tileSize * tileSize)
    private val zero16 = LongArray(tileSize * tileSize)
    private var state: List<Any?>? = null
    private var signatures = emptyMap<PixelTileKey, List<Any?>>()

    fun update(before: PixelTileSurfaceSnapshot, after: PixelTileSurfaceSnapshot, opacity: Float,
        mask: MaskTileSurfaceSnapshot?): PixelLayerProjectionUpdate {
        require(opacity.isFinite() && opacity in 0f..1f)
        for (surface in listOf(before, after)) require(surface.logicalWidth == width && surface.logicalHeight == height &&
            surface.tileSize == tileSize && surface.pixelFormat == format) { "Group backdrop geometry or format mismatch" }
        // The common full-strength Through mode already has the final immutable projection.
        // Reuse it directly instead of copying every changed tile through an identity lerp.
        if (opacity == 0f || (opacity == 1f && mask == null)) {
            val result = if (opacity == 0f) before else after
            return PixelLayerProjectionUpdate(result, 0, result.tileCount)
        }
        val nextState = listOf(before.surfaceId, before.revision, after.surfaceId, after.revision,
            opacity.toBits(), mask?.surfaceId, mask?.revision, mask?.defaultAlpha,
            mask?.logicalWidth, mask?.logicalHeight, mask?.tileSize)
        if (state == nextState) return PixelLayerProjectionUpdate(output.snapshot(), 0, signatures.size)
        val nextSignatures = (before.tileKeys + after.tileKeys).associateWith { key ->
            listOf(before.surfaceId, before.generationAt(key), after.surfaceId, after.generationAt(key), opacity.toBits(),
                mask?.surfaceId, mask?.defaultAlpha, mask?.logicalWidth, mask?.logicalHeight, mask?.tileSize,
                mask?.let { maskGenerations(it, bounds(key)) })
        }
        val dirty = (signatures.keys + nextSignatures.keys).filter { signatures[it] != nextSignatures[it] }
        if (dirty.isNotEmpty()) output.beginWrite("through group contribution").use { write ->
            for (key in dirty) {
                val area = bounds(key)
                val local = PixelRect(0, 0, area.width, area.height)
                fun weight(x: Int, y: Int): Float = opacity * (mask?.let {
                    it.alphaAt(maskX(it, x), maskY(it, y)) / 255f
                } ?: 1f)
                when (format) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> read8(before, key) { back -> read8(after, key) { front ->
                        write.editTile(key, local) { pixels, stride ->
                            for (y in 0 until area.height) for (x in 0 until area.width) {
                                val i = y * stride + x; val amount = weight(area.left + x, area.top + y)
                                val a = back[i]; val b = front[i]
                                fun channel(shift: Int): Int {
                                    val from = a ushr shift and 255; val to = b ushr shift and 255
                                    return (from + (to - from) * amount).roundToInt().coerceIn(0, 255)
                                }
                                pixels[i] = (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                            }
                        }
                    } }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> read16(before, key) { back -> read16(after, key) { front ->
                        write.editTileF16(key, local) { pixels, stride ->
                            for (y in 0 until area.height) for (x in 0 until area.width) {
                                val i = y * stride + x; val amount = weight(area.left + x, area.top + y)
                                val a = back[i]; val b = front[i]
                                fun mix(from: Float, to: Float) = from + (to - from) * amount
                                pixels[i] = PackedRgbaF16.packPremultiplied(
                                    mix(PackedRgbaF16.premultipliedRed(a), PackedRgbaF16.premultipliedRed(b)),
                                    mix(PackedRgbaF16.premultipliedGreen(a), PackedRgbaF16.premultipliedGreen(b)),
                                    mix(PackedRgbaF16.premultipliedBlue(a), PackedRgbaF16.premultipliedBlue(b)),
                                    mix(PackedRgbaF16.alpha(a), PackedRgbaF16.alpha(b)))
                            }
                        }
                    } }
                }
            }
            write.commit().close()
        }
        signatures = nextSignatures; state = nextState
        return PixelLayerProjectionUpdate(output.snapshot(), dirty.size, nextSignatures.keys.count { it !in dirty })
    }

    private fun read8(surface: PixelTileSurfaceSnapshot, key: PixelTileKey, block: (IntArray) -> Unit) {
        var present = false
        surface.readRgba8Tile(key) { present = true; block(it) }
        if (!present) block(zero8)
    }
    private fun read16(surface: PixelTileSurfaceSnapshot, key: PixelTileKey, block: (LongArray) -> Unit) {
        var present = false
        surface.readF16Tile(key) { present = true; block(it) }
        if (!present) block(zero16)
    }
    private fun bounds(key: PixelTileKey) = PixelRect(key.column * tileSize, key.row * tileSize,
        minOf(width, (key.column + 1) * tileSize), minOf(height, (key.row + 1) * tileSize))
    private fun maskX(mask: MaskTileSurfaceSnapshot, x: Int) = ((x + .5f) * mask.logicalWidth / width).toInt().coerceIn(0, mask.logicalWidth - 1)
    private fun maskY(mask: MaskTileSurfaceSnapshot, y: Int) = ((y + .5f) * mask.logicalHeight / height).toInt().coerceIn(0, mask.logicalHeight - 1)
    private fun maskGenerations(mask: MaskTileSurfaceSnapshot, area: PixelRect): List<Pair<PixelTileKey, Long?>> = buildList {
        for (y in maskY(mask, area.top) / mask.tileSize..maskY(mask, area.bottom - 1) / mask.tileSize)
            for (x in maskX(mask, area.left) / mask.tileSize..maskX(mask, area.right - 1) / mask.tileSize) {
                val key = PixelTileKey(x, y); add(key to mask.generationAt(key))
            }
    }
}
