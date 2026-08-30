package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord

/** Pixel coverage used by layer masks, clipping bases, and selections. */
fun interface CompositeCoverage {
    fun coverageAt(x: Int, y: Int): Float
}

/**
 * Composites immutable source generations into one sparse destination transaction.
 *
 * Only occupied source tiles are visited. The destination remains unpublished until the returned
 * record is committed, and undo swaps exactly the touched tile generations.
 */
object PixelTileCompositor {
    fun composite(
        source: PixelTileSurfaceSnapshot,
        destination: PixelTileSurface,
        mode: PixelBlendMode = PixelBlendMode.NORMAL,
        opacity: Float = 1f,
        coverage: CompositeCoverage? = null,
        region: PixelRect = PixelRect(0, 0, source.logicalWidth, source.logicalHeight),
        label: String = "composite ${mode.name.lowercase()}",
    ): PixelTileTransactionRecord {
        require(source.logicalWidth == destination.logicalWidth &&
            source.logicalHeight == destination.logicalHeight
        ) { "Source and destination geometry must match" }
        require(source.tileSize == destination.tileSize) { "Source and destination tile sizes must match" }
        require(source.pixelFormat == destination.pixelFormat) { "Source and destination pixel formats must match" }
        val bounds = PixelRect(0, 0, source.logicalWidth, source.logicalHeight)
        val clippedRegion = region.intersect(bounds)
        require(clippedRegion == region) { "Composite region must be inside the logical surface" }
        val layerOpacity = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 0f
        val write = destination.beginWrite(label)
        if (clippedRegion.isEmpty || layerOpacity <= 0f) return write.commit()
        val scratch = FloatArray(4)
        try {
            source.tileInfo.forEach { info ->
                val tileBounds = tileBounds(source, info.key)
                val overlap = tileBounds.intersect(clippedRegion)
                if (overlap.isEmpty) return@forEach
                val dirtyLocal = PixelRect(
                    overlap.left - tileBounds.left,
                    overlap.top - tileBounds.top,
                    overlap.right - tileBounds.left,
                    overlap.bottom - tileBounds.top,
                )
                when (source.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> {
                        checkNotNull(source.readRgba8Tile(info.key) { sourcePixels ->
                            write.editTile(info.key, dirtyLocal) { destinationPixels, stride ->
                                compositeRgba8Tile(
                                    sourcePixels,
                                    destinationPixels,
                                    stride,
                                    tileBounds,
                                    overlap,
                                    mode,
                                    layerOpacity,
                                    coverage,
                                    scratch,
                                )
                            }
                        })
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> {
                        checkNotNull(source.readF16Tile(info.key) { sourcePixels ->
                            write.editTileF16(info.key, dirtyLocal) { destinationPixels, stride ->
                                compositeF16Tile(
                                    sourcePixels,
                                    destinationPixels,
                                    stride,
                                    tileBounds,
                                    overlap,
                                    mode,
                                    layerOpacity,
                                    coverage,
                                    scratch,
                                )
                            }
                        })
                    }
                }
            }
            return write.commit()
        } catch (failure: Throwable) {
            write.cancel()
            throw failure
        }
    }

    internal fun compositeRgba8Tile(
        source: IntArray,
        destination: IntArray,
        stride: Int,
        tileBounds: PixelRect,
        overlap: PixelRect,
        mode: PixelBlendMode,
        opacity: Float,
        coverage: CompositeCoverage?,
        scratch: FloatArray,
    ) {
        val localLeft = overlap.left - tileBounds.left
        val localTop = overlap.top - tileBounds.top
        val localRight = overlap.right - tileBounds.left
        val localBottom = overlap.bottom - tileBounds.top
        if (coverage == null && NativeCompositeOps.compositeRgba8(
                source,
                destination,
                stride,
                localLeft,
                localTop,
                localRight,
                localBottom,
                mode,
                opacity,
            )
        ) return
        for (y in overlap.top until overlap.bottom) {
            var index = (y - tileBounds.top) * stride + overlap.left - tileBounds.left
            for (x in overlap.left until overlap.right) {
                val sourcePixel = source[index]
                val sourceAlpha = (sourcePixel ushr 24 and 0xff) / 255f
                if (sourceAlpha > 0f) {
                    val pixelOpacity = opacity * coverageValue(coverage, x, y)
                    if (pixelOpacity > 0f) {
                        val destinationPixel = destination[index]
                        PixelBlendMath.compositeInto(
                            sourceRedPremultiplied = (sourcePixel ushr 16 and 0xff) / 255f,
                            sourceGreenPremultiplied = (sourcePixel ushr 8 and 0xff) / 255f,
                            sourceBluePremultiplied = (sourcePixel and 0xff) / 255f,
                            sourceAlpha = sourceAlpha,
                            destinationRedPremultiplied = (destinationPixel ushr 16 and 0xff) / 255f,
                            destinationGreenPremultiplied = (destinationPixel ushr 8 and 0xff) / 255f,
                            destinationBluePremultiplied = (destinationPixel and 0xff) / 255f,
                            destinationAlpha = (destinationPixel ushr 24 and 0xff) / 255f,
                            mode = mode,
                            opacity = pixelOpacity,
                            output = scratch,
                        )
                        destination[index] = packRgba8(scratch)
                    }
                }
                index++
            }
        }
    }

    internal fun compositeF16Tile(
        source: LongArray,
        destination: LongArray,
        stride: Int,
        tileBounds: PixelRect,
        overlap: PixelRect,
        mode: PixelBlendMode,
        opacity: Float,
        coverage: CompositeCoverage?,
        scratch: FloatArray,
    ) {
        val localLeft = overlap.left - tileBounds.left
        val localTop = overlap.top - tileBounds.top
        val localRight = overlap.right - tileBounds.left
        val localBottom = overlap.bottom - tileBounds.top
        if (coverage == null && NativeCompositeOps.compositeF16(
                source,
                destination,
                stride,
                localLeft,
                localTop,
                localRight,
                localBottom,
                mode,
                opacity,
            )
        ) return
        for (y in overlap.top until overlap.bottom) {
            var index = (y - tileBounds.top) * stride + overlap.left - tileBounds.left
            for (x in overlap.left until overlap.right) {
                val sourcePixel = source[index]
                val sourceAlpha = PackedRgbaF16.alpha(sourcePixel)
                if (sourceAlpha > 0f) {
                    val pixelOpacity = opacity * coverageValue(coverage, x, y)
                    if (pixelOpacity > 0f) {
                        val destinationPixel = destination[index]
                        PixelBlendMath.compositeInto(
                            sourceRedPremultiplied = PackedRgbaF16.premultipliedRed(sourcePixel),
                            sourceGreenPremultiplied = PackedRgbaF16.premultipliedGreen(sourcePixel),
                            sourceBluePremultiplied = PackedRgbaF16.premultipliedBlue(sourcePixel),
                            sourceAlpha = sourceAlpha,
                            destinationRedPremultiplied = PackedRgbaF16.premultipliedRed(destinationPixel),
                            destinationGreenPremultiplied = PackedRgbaF16.premultipliedGreen(destinationPixel),
                            destinationBluePremultiplied = PackedRgbaF16.premultipliedBlue(destinationPixel),
                            destinationAlpha = PackedRgbaF16.alpha(destinationPixel),
                            mode = mode,
                            opacity = pixelOpacity,
                            output = scratch,
                        )
                        destination[index] = PackedRgbaF16.packPremultiplied(
                            scratch[0],
                            scratch[1],
                            scratch[2],
                            scratch[3],
                        )
                    }
                }
                index++
            }
        }
    }

    private fun coverageValue(coverage: CompositeCoverage?, x: Int, y: Int): Float {
        val value = coverage?.coverageAt(x, y) ?: return 1f
        return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    private fun packRgba8(channels: FloatArray): Int {
        val alpha = unitByte(channels[3])
        if (alpha == 0) return 0
        val alphaFloat = alpha / 255f
        return (alpha shl 24) or
            (unitByte(channels[0].coerceIn(0f, alphaFloat)) shl 16) or
            (unitByte(channels[1].coerceIn(0f, alphaFloat)) shl 8) or
            unitByte(channels[2].coerceIn(0f, alphaFloat))
    }

    private fun unitByte(value: Float): Int =
        ((if (value.isFinite()) value else 0f).coerceIn(0f, 1f) * 255f + .5f).toInt()

    private fun tileBounds(snapshot: PixelTileSurfaceSnapshot, key: PixelTileKey): PixelRect {
        val left = key.column * snapshot.tileSize
        val top = key.row * snapshot.tileSize
        return PixelRect(
            left,
            top,
            minOf(left + snapshot.tileSize, snapshot.logicalWidth),
            minOf(top + snapshot.tileSize, snapshot.logicalHeight),
        )
    }
}
