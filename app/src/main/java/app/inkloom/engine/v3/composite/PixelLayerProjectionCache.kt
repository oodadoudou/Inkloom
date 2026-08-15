package app.inkloom.engine.v3.composite

import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileFormat
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import java.util.UUID

data class PixelLayerProjectionInput(
    val layerId: String,
    val pixels: PixelTileSurfaceSnapshot,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: PixelBlendMode = PixelBlendMode.NORMAL,
    val mask: MaskTileSurfaceSnapshot? = null,
    val clipToBaseBelow: Boolean = false,
)

data class PixelLayerProjectionUpdate(
    val snapshot: PixelTileSurfaceSnapshot,
    val recomposedTileCount: Int,
    val reusedTileCount: Int,
)

/**
 * Incremental document projection keyed by immutable per-tile layer dependencies.
 *
 * Changing one layer tile recomposites only that projection tile from bottom to top. Unchanged
 * projection generations remain intact, so the Android RenderNode cache also remains valid.
 */
class PixelLayerProjectionCache(
    logicalWidth: Int,
    logicalHeight: Int,
    tileSize: Int = PixelTileSurface.DEFAULT_TILE_SIZE,
    pixelFormat: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888,
    surfaceId: String = "projection-${UUID.randomUUID()}",
) {
    private val projection = PixelTileSurface(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        tileSize = tileSize,
        surfaceId = surfaceId,
        pixelFormat = pixelFormat,
    )
    private var signatures: Map<PixelTileKey, TileSignature> = emptyMap()
    private var rgba8BatchPixels = IntArray(0)
    private var f16BatchPixels = LongArray(0)

    val snapshot: PixelTileSurfaceSnapshot get() = projection.snapshot()

    fun update(layers: List<PixelLayerProjectionInput>): PixelLayerProjectionUpdate {
        layers.forEach(::requireCompatible)
        val prepared = prepare(layers)
        val nextSignatures = buildSignatures(prepared)
        val dirtyKeys = (signatures.keys + nextSignatures.keys).filterTo(linkedSetOf()) { key ->
            signatures[key] != nextSignatures[key]
        }
        if (dirtyKeys.isEmpty()) {
            return PixelLayerProjectionUpdate(
                snapshot = projection.snapshot(),
                recomposedTileCount = 0,
                reusedTileCount = nextSignatures.size,
            )
        }

        val write = projection.beginWrite("incremental layer projection")
        val scratch = FloatArray(4)
        try {
            dirtyKeys.forEach { key ->
                val bounds = tileBounds(key)
                val dirtyLocal = PixelRect(0, 0, bounds.width, bounds.height)
                when (projection.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> write.editTile(key, dirtyLocal) { output, stride ->
                        output.fill(0)
                        compositeRgba8Layers(prepared, key, output, stride, bounds, scratch)
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 ->
                        write.editTileF16(key, dirtyLocal) { output, stride ->
                            output.fill(0L)
                            compositeF16Layers(prepared, key, output, stride, bounds, scratch)
                        }
                }
            }
            write.commit().close()
            signatures = nextSignatures
        } catch (failure: Throwable) {
            write.cancel()
            throw failure
        }
        return PixelLayerProjectionUpdate(
            snapshot = projection.snapshot(),
            recomposedTileCount = dirtyKeys.size,
            reusedTileCount = (nextSignatures.keys - dirtyKeys).size,
        )
    }

    private fun compositeRgba8Layers(
        layers: List<PreparedLayer>,
        key: PixelTileKey,
        output: IntArray,
        stride: Int,
        bounds: PixelRect,
        scratch: FloatArray,
    ) {
        val contributors = layers.filter { it.contributes(key) }
        if (contributors.isEmpty()) return
        var index = 0
        while (index < contributors.size) {
            val layer = contributors[index]
            if (layer.isNativeBatchCompatible()) {
                var end = index + 1
                while (end < contributors.size && contributors[end].isNativeBatchCompatible()) end++
                if (compositeRgba8Batch(contributors.subList(index, end), key, output, stride, bounds)) {
                    index = end
                    continue
                }
            }
            layer.input.pixels.readRgba8Tile(key) { source ->
                PixelTileCompositor.compositeRgba8Tile(
                    source,
                    output,
                    stride,
                    bounds,
                    bounds,
                    layer.input.blendMode,
                    layer.opacity,
                    layer.coverage,
                    scratch,
                )
            }
            index++
        }
    }

    private fun compositeRgba8Batch(
        contributors: List<PreparedLayer>,
        key: PixelTileKey,
        output: IntArray,
        stride: Int,
        bounds: PixelRect,
    ): Boolean {
        if (!NativeCompositeOps.isAvailable) return false
        val layerStride = output.size
        val required = Math.multiplyExact(layerStride, contributors.size)
        if (rgba8BatchPixels.size < required) rgba8BatchPixels = IntArray(required)
        var copiedLayers = 0
        contributors.forEachIndexed { layerIndex, layer ->
            layer.input.pixels.readRgba8Tile(key) {
                it.copyInto(rgba8BatchPixels, layerIndex * layerStride)
                copiedLayers++
            }
        }
        val nativeComplete = copiedLayers == contributors.size && NativeCompositeOps.compositeRgba8Layers(
            rgba8BatchPixels,
            layerStride,
            contributors.size,
            output,
            stride,
            0,
            0,
            bounds.width,
            bounds.height,
            IntArray(contributors.size) { contributors[it].input.blendMode.ordinal },
            FloatArray(contributors.size) { contributors[it].opacity },
        )
        check(nativeComplete) { "Native RGBA8 layer compositor rejected valid tile batch" }
        return true
    }

    private fun compositeF16Layers(
        layers: List<PreparedLayer>,
        key: PixelTileKey,
        output: LongArray,
        stride: Int,
        bounds: PixelRect,
        scratch: FloatArray,
    ) {
        val contributors = layers.filter { it.contributes(key) }
        if (contributors.isEmpty()) return
        var index = 0
        while (index < contributors.size) {
            val layer = contributors[index]
            if (layer.isNativeBatchCompatible()) {
                var end = index + 1
                while (end < contributors.size && contributors[end].isNativeBatchCompatible()) end++
                if (compositeF16Batch(contributors.subList(index, end), key, output, stride, bounds)) {
                    index = end
                    continue
                }
            }
            layer.input.pixels.readF16Tile(key) { source ->
                PixelTileCompositor.compositeF16Tile(
                    source,
                    output,
                    stride,
                    bounds,
                    bounds,
                    layer.input.blendMode,
                    layer.opacity,
                    layer.coverage,
                    scratch,
                )
            }
            index++
        }
    }

    private fun compositeF16Batch(
        contributors: List<PreparedLayer>,
        key: PixelTileKey,
        output: LongArray,
        stride: Int,
        bounds: PixelRect,
    ): Boolean {
        if (!NativeCompositeOps.isAvailable) return false
        val layerStride = output.size
        val required = Math.multiplyExact(layerStride, contributors.size)
        if (f16BatchPixels.size < required) f16BatchPixels = LongArray(required)
        var copiedLayers = 0
        contributors.forEachIndexed { layerIndex, layer ->
            layer.input.pixels.readF16Tile(key) {
                it.copyInto(f16BatchPixels, layerIndex * layerStride)
                copiedLayers++
            }
        }
        val nativeComplete = copiedLayers == contributors.size && NativeCompositeOps.compositeF16Layers(
            f16BatchPixels,
            layerStride,
            contributors.size,
            output,
            stride,
            0,
            0,
            bounds.width,
            bounds.height,
            IntArray(contributors.size) { contributors[it].input.blendMode.ordinal },
            FloatArray(contributors.size) { contributors[it].opacity },
        )
        check(nativeComplete) { "Native F16 layer compositor rejected valid tile batch" }
        return true
    }

    private fun prepare(layers: List<PixelLayerProjectionInput>): List<PreparedLayer> {
        var currentBase: PixelLayerProjectionInput? = null
        return layers.map { input ->
            val opacity = if (input.opacity.isFinite()) input.opacity.coerceIn(0f, 1f) else 0f
            val base = if (input.clipToBaseBelow) currentBase else null
            if (!input.clipToBaseBelow) currentBase = input
            val coverage = when {
                !input.visible || opacity <= 0f -> ZERO_COVERAGE
                input.clipToBaseBelow && (base == null || !base.visible) -> ZERO_COVERAGE
                input.mask == null && base == null -> null
                else -> CompositeCoverage { x, y ->
                    val maskAlpha = input.mask?.let { alphaAt(it, x, y, projection.logicalWidth, projection.logicalHeight) } ?: 1f
                    val baseAlpha = base?.let {
                        val baseOpacity = if (it.opacity.isFinite()) it.opacity.coerceIn(0f, 1f) else 0f
                        val baseMask = it.mask?.let { mask -> alphaAt(mask, x, y, projection.logicalWidth, projection.logicalHeight) } ?: 1f
                        alphaAt(it.pixels, x, y) * baseMask * baseOpacity
                    } ?: 1f
                    maskAlpha * baseAlpha
                }
            }
            PreparedLayer(
                input = input,
                opacity = if (input.visible) opacity else 0f,
                sourceGenerations = input.pixels.tileInfo.associate { it.key to it.generation },
                maskDependencies = maskDependencies(input.mask, input.pixels.tileInfo.mapTo(linkedSetOf()) { it.key }),
                clippingBase = base,
                clippingBaseGenerations = base?.pixels?.tileInfo?.associate { it.key to it.generation }.orEmpty(),
                clippingBaseMaskDependencies = if (base == null) emptyMap() else
                    maskDependencies(base.mask, input.pixels.tileInfo.mapTo(linkedSetOf()) { it.key }),
                coverage = coverage,
            )
        }
    }

    private fun buildSignatures(layers: List<PreparedLayer>): Map<PixelTileKey, TileSignature> {
        val keys = layers.asSequence()
            .filter { it.opacity > 0f }
            .flatMap { it.sourceGenerations.keys.asSequence() }
            .toCollection(linkedSetOf())
        return keys.associateWith { key ->
            TileSignature(layers.mapNotNull { layer -> layer.dependency(key) })
        }
    }

    private fun requireCompatible(input: PixelLayerProjectionInput) {
        val pixels = input.pixels
        require(pixels.logicalWidth == projection.logicalWidth &&
            pixels.logicalHeight == projection.logicalHeight &&
            pixels.tileSize == projection.tileSize &&
            pixels.pixelFormat == projection.pixelFormat
        ) { "Layer ${input.layerId} is incompatible with the projection surface" }
        input.mask?.let { mask ->
            require(mask.logicalWidth > 0 && mask.logicalHeight > 0) {
                "Layer ${input.layerId} mask geometry is invalid"
            }
        }
    }

    private fun maskDependencies(
        mask: MaskTileSurfaceSnapshot?,
        sourceKeys: Set<PixelTileKey>,
    ): Map<PixelTileKey, Long> {
        if (mask == null) return emptyMap()
        val generations = mask.tileInfo.associate { it.key to it.generation }
        return sourceKeys.associateWith { sourceKey ->
            val bounds = tileBounds(sourceKey)
            val left = (bounds.left.toLong() * mask.logicalWidth / projection.logicalWidth).toInt()
            val top = (bounds.top.toLong() * mask.logicalHeight / projection.logicalHeight).toInt()
            val right = ((bounds.right.toLong() * mask.logicalWidth + projection.logicalWidth - 1) / projection.logicalWidth)
                .toInt().coerceAtMost(mask.logicalWidth)
            val bottom = ((bounds.bottom.toLong() * mask.logicalHeight + projection.logicalHeight - 1) / projection.logicalHeight)
                .toInt().coerceAtMost(mask.logicalHeight)
            var fingerprint = 31L + mask.defaultAlpha
            if (right > left && bottom > top) {
                for (row in top / mask.tileSize..(bottom - 1) / mask.tileSize) {
                    for (column in left / mask.tileSize..(right - 1) / mask.tileSize) {
                        val generation = generations[PixelTileKey(column, row)] ?: 0L
                        fingerprint = fingerprint * 1_000_003L + generation
                    }
                }
            }
            fingerprint
        }
    }

    private fun tileBounds(key: PixelTileKey): PixelRect {
        val left = key.column * projection.tileSize
        val top = key.row * projection.tileSize
        return PixelRect(
            left,
            top,
            minOf(left + projection.tileSize, projection.logicalWidth),
            minOf(top + projection.tileSize, projection.logicalHeight),
        )
    }

    private data class PreparedLayer(
        val input: PixelLayerProjectionInput,
        val opacity: Float,
        val sourceGenerations: Map<PixelTileKey, Long>,
        val maskDependencies: Map<PixelTileKey, Long>,
        val clippingBase: PixelLayerProjectionInput?,
        val clippingBaseGenerations: Map<PixelTileKey, Long>,
        val clippingBaseMaskDependencies: Map<PixelTileKey, Long>,
        val coverage: CompositeCoverage?,
    ) {
        fun contributes(key: PixelTileKey): Boolean = opacity > 0f && sourceGenerations[key] != null

        fun isNativeBatchCompatible(): Boolean = coverage == null &&
            input.blendMode.ordinal <= PixelBlendMode.EXCLUSION.ordinal

        fun dependency(key: PixelTileKey): LayerDependency? {
            val sourceGeneration = sourceGenerations[key] ?: return null
            return LayerDependency(
                layerId = input.layerId,
                surfaceId = input.pixels.surfaceId,
                sourceGeneration = sourceGeneration,
                visible = input.visible,
                opacityBits = opacity.toRawBits(),
                blendMode = input.blendMode,
                maskSurfaceId = input.mask?.surfaceId,
                maskGeneration = maskDependencies[key],
                maskDefaultAlpha = input.mask?.defaultAlpha,
                clipToBaseBelow = input.clipToBaseBelow,
                baseLayerId = clippingBase?.layerId,
                baseSurfaceId = clippingBase?.pixels?.surfaceId,
                baseGeneration = clippingBaseGenerations[key],
                baseVisible = clippingBase?.visible,
                baseOpacityBits = clippingBase?.opacity?.toRawBits(),
                baseMaskSurfaceId = clippingBase?.mask?.surfaceId,
                baseMaskGeneration = clippingBaseMaskDependencies[key],
                baseMaskDefaultAlpha = clippingBase?.mask?.defaultAlpha,
            )
        }
    }

    private data class TileSignature(val layers: List<LayerDependency>)

    private data class LayerDependency(
        val layerId: String,
        val surfaceId: String,
        val sourceGeneration: Long,
        val visible: Boolean,
        val opacityBits: Int,
        val blendMode: PixelBlendMode,
        val maskSurfaceId: String?,
        val maskGeneration: Long?,
        val maskDefaultAlpha: Int?,
        val clipToBaseBelow: Boolean,
        val baseLayerId: String?,
        val baseSurfaceId: String?,
        val baseGeneration: Long?,
        val baseVisible: Boolean?,
        val baseOpacityBits: Int?,
        val baseMaskSurfaceId: String?,
        val baseMaskGeneration: Long?,
        val baseMaskDefaultAlpha: Int?,
    )

    companion object {
        private val ZERO_COVERAGE = CompositeCoverage { _, _ -> 0f }

        private fun alphaAt(snapshot: PixelTileSurfaceSnapshot, x: Int, y: Int): Float =
            when (snapshot.pixelFormat) {
                PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> (snapshot.pixelAt(x, y) ushr 24 and 0xff) / 255f
                PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> snapshot.linearPixelAt(x, y).alpha
            }.coerceIn(0f, 1f)

        private fun alphaAt(
            snapshot: MaskTileSurfaceSnapshot,
            x: Int,
            y: Int,
            projectionWidth: Int,
            projectionHeight: Int,
        ): Float {
            val maskX = (((x + .5f) * snapshot.logicalWidth / projectionWidth) - .5f)
                .toInt().coerceIn(0, snapshot.logicalWidth - 1)
            val maskY = (((y + .5f) * snapshot.logicalHeight / projectionHeight) - .5f)
                .toInt().coerceIn(0, snapshot.logicalHeight - 1)
            return snapshot.alphaAt(maskX, maskY) / 255f
        }
    }
}
