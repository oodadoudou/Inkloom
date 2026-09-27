package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.document.LayerColorReplacement
import java.util.UUID

data class PixelLayerProjectionInput(
    val layerId: String,
    val pixels: PixelTileSurfaceSnapshot,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: PixelBlendMode = PixelBlendMode.NORMAL,
    val mask: MaskTileSurfaceSnapshot? = null,
    val clipToBaseBelow: Boolean = false,
    /** Gesture-local sparse contribution pinned to [pixels]. It participates in signatures and
     * composition without materializing a full-layer preview surface. */
    val activeDelta: PixelTileSurfaceSnapshot? = null,
    val activeDeltaBlend: PixelActiveDeltaBlend = PixelActiveDeltaBlend.SOURCE_OVER,
    /** Synthetic Through backdrops are visible, but are outside the child clipping scope. */
    val isClippingBase: Boolean = true,
    val colorReplacement: LayerColorReplacement? = null,
)

enum class PixelActiveDeltaBlend { SOURCE_OVER, DESTINATION_OUT, SOURCE_ATOP }

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
    private var lastInputState: List<ProjectionInputState>? = null
    private var rgba8BatchPixels = IntArray(0)
    private var f16BatchPixels = LongArray(0)
    private var batchCoverage = ByteArray(0)
    private var maskCoverageScratch = ByteArray(0)
    private var batchModes = IntArray(0)
    private var batchOpacities = FloatArray(0)
    private var activeRgba8Scratch = IntArray(0)
    private val rgba8Checkpoints = object : LinkedHashMap<ProjectionCheckpointKey, Rgba8Underlay>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProjectionCheckpointKey, Rgba8Underlay>?): Boolean =
            size > MAX_CACHED_UNDERLAY_CHECKPOINTS
    }

    val snapshot: PixelTileSurfaceSnapshot get() = projection.snapshot()

    fun update(layers: List<PixelLayerProjectionInput>): PixelLayerProjectionUpdate {
        val inputState = layers.map(ProjectionInputState::from)
        if (inputState == lastInputState) {
            return PixelLayerProjectionUpdate(
                snapshot = projection.snapshot(),
                recomposedTileCount = 0,
                reusedTileCount = signatures.size,
            )
        }
        layers.forEach(::requireCompatible)
        val prepared = prepare(layers)
        val nextSignatures = buildSignatures(prepared)
        val dirtyKeys = (signatures.keys + nextSignatures.keys).filterTo(linkedSetOf()) { key ->
            signatures[key] != nextSignatures[key]
        }
        if (dirtyKeys.isEmpty()) {
            lastInputState = inputState
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
                        compositeRgba8Layers(
                            prepared,
                            key,
                            output,
                            stride,
                            bounds,
                            scratch,
                            nextSignatures[key],
                        )
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
            lastInputState = inputState
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
        signature: TileSignature?,
    ) {
        val contributors = layers.filter { it.contributes(key) }
        if (contributors.isEmpty()) return
        if (contributors.size >= UNDERLAY_LAYER_THRESHOLD && signature != null) {
            val contributorSignature = TileSignature(contributors.mapNotNull { it.dependency(key) })
            val reusable = rgba8Checkpoints.entries.asSequence()
                .filter { (checkpoint, cached) ->
                    checkpoint.key == key && checkpoint.layerCount <= contributorSignature.layers.size &&
                        cached.pixels.size == output.size &&
                        cached.signature.layers == contributorSignature.layers.take(checkpoint.layerCount)
                }
                .maxByOrNull { it.key.layerCount }
            var composedLayers = reusable?.key?.layerCount ?: 0
            reusable?.value?.pixels?.copyInto(output)
            while (composedLayers < contributors.size) {
                val end = minOf(composedLayers + UNDERLAY_CHECKPOINT_INTERVAL, contributors.size)
                compositeRgba8Contributors(
                    contributors.subList(composedLayers, end),
                    key,
                    output,
                    stride,
                    bounds,
                    scratch,
                )
                composedLayers = end
                if (composedLayers < contributors.size) {
                    val checkpointKey = ProjectionCheckpointKey(key, composedLayers)
                    rgba8Checkpoints[checkpointKey] = Rgba8Underlay(
                        TileSignature(contributorSignature.layers.take(composedLayers)),
                        output.copyOf(),
                    )
                }
            }
            return
        }
        rgba8Checkpoints.keys.removeAll { it.key == key }
        compositeRgba8Contributors(contributors, key, output, stride, bounds, scratch)
    }

    private fun compositeRgba8Contributors(
        contributors: List<PreparedLayer>,
        key: PixelTileKey,
        output: IntArray,
        stride: Int,
        bounds: PixelRect,
        scratch: FloatArray,
    ) {
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
            layer.readRgba8Tile(key) { source ->
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
        val hasCoverage = contributors.any { it.coverage != null }
        if (hasCoverage && batchCoverage.size < required) batchCoverage = ByteArray(required)
        if (batchModes.size != contributors.size) batchModes = IntArray(contributors.size)
        if (batchOpacities.size != contributors.size) batchOpacities = FloatArray(contributors.size)
        var copiedLayers = 0
        contributors.forEachIndexed { layerIndex, layer ->
            layer.readRgba8Tile(key) {
                it.copyInto(rgba8BatchPixels, layerIndex * layerStride)
                copiedLayers++
            }
            if (hasCoverage) writeCoverage(layer, key, layerIndex * layerStride, stride, bounds)
            batchModes[layerIndex] = layer.input.blendMode.ordinal
            batchOpacities[layerIndex] = layer.opacity
        }
        val nativeComplete = copiedLayers == contributors.size && if (hasCoverage) {
            NativeCompositeOps.compositeRgba8LayersWithCoverage(
                rgba8BatchPixels, batchCoverage, layerStride, contributors.size, output, stride,
                0, 0, bounds.width, bounds.height, batchModes, batchOpacities,
            )
        } else NativeCompositeOps.compositeRgba8Layers(
            rgba8BatchPixels, layerStride, contributors.size, output, stride,
            0, 0, bounds.width, bounds.height, batchModes, batchOpacities,
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
            layer.readF16Tile(key) { source ->
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
        val hasCoverage = contributors.any { it.coverage != null }
        if (hasCoverage && batchCoverage.size < required) batchCoverage = ByteArray(required)
        if (batchModes.size != contributors.size) batchModes = IntArray(contributors.size)
        if (batchOpacities.size != contributors.size) batchOpacities = FloatArray(contributors.size)
        var copiedLayers = 0
        contributors.forEachIndexed { layerIndex, layer ->
            layer.readF16Tile(key) {
                it.copyInto(f16BatchPixels, layerIndex * layerStride)
                copiedLayers++
            }
            if (hasCoverage) writeCoverage(layer, key, layerIndex * layerStride, stride, bounds)
            batchModes[layerIndex] = layer.input.blendMode.ordinal
            batchOpacities[layerIndex] = layer.opacity
        }
        val nativeComplete = copiedLayers == contributors.size && if (hasCoverage) {
            NativeCompositeOps.compositeF16LayersWithCoverage(
                f16BatchPixels, batchCoverage, layerStride, contributors.size, output, stride,
                0, 0, bounds.width, bounds.height, batchModes, batchOpacities,
            )
        } else NativeCompositeOps.compositeF16Layers(
            f16BatchPixels, layerStride, contributors.size, output, stride,
            0, 0, bounds.width, bounds.height, batchModes, batchOpacities,
        )
        check(nativeComplete) { "Native F16 layer compositor rejected valid tile batch" }
        return true
    }

    private fun writeCoverage(
        layer: PreparedLayer,
        key: PixelTileKey,
        offset: Int,
        stride: Int,
        bounds: PixelRect,
    ) {
        val end = offset + stride * stride
        batchCoverage.fill(if (layer.zeroCoverage) 0 else 255.toByte(), offset, end)
        if (layer.zeroCoverage) return
        multiplyMaskCoverage(layer.input.mask, offset, stride, bounds)
        val base = layer.clippingBase ?: return
        val baseOpacityByte = ((if (base.opacity.isFinite()) base.opacity else 0f).coerceIn(0f, 1f) * 255f + .5f).toInt()
        val baseTilePresent = when (base.pixels.pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> readResolvedRgba8Tile(base, key) { pixels ->
                if (!NativeCompositeOps.multiplyCoverageByRgba8Alpha(
                        batchCoverage,
                        offset,
                        pixels,
                        stride,
                        bounds.width,
                        bounds.height,
                        baseOpacityByte,
                    )
                ) {
                    for (localY in 0 until bounds.height) {
                        var index = localY * stride
                        var coverageIndex = offset + index
                        repeat(bounds.width) {
                            val alpha = pixels[index++] ushr 24 and 0xff
                            batchCoverage[coverageIndex] = multiplyByte(batchCoverage[coverageIndex], alpha)
                            batchCoverage[coverageIndex] = multiplyByte(batchCoverage[coverageIndex], baseOpacityByte)
                            coverageIndex++
                        }
                    }
                }
                true
            } ?: false
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> base.pixels.readF16Tile(key) { pixels ->
                for (localY in 0 until bounds.height) {
                    var index = localY * stride
                    var coverageIndex = offset + index
                    repeat(bounds.width) {
                        val alpha = (app.inkloom.engine.v5.surface.PackedRgbaF16.alpha(pixels[index++]).coerceIn(0f, 1f) * 255f + .5f).toInt()
                        batchCoverage[coverageIndex] = multiplyByte(batchCoverage[coverageIndex], alpha)
                        batchCoverage[coverageIndex] = multiplyByte(batchCoverage[coverageIndex], baseOpacityByte)
                        coverageIndex++
                    }
                }
                true
            } ?: false
        }
        if (!baseTilePresent) batchCoverage.fill(0, offset, end)
        multiplyMaskCoverage(base.mask, offset, stride, bounds)
    }

    private fun multiplyMaskCoverage(
        mask: MaskTileSurfaceSnapshot?,
        offset: Int,
        stride: Int,
        bounds: PixelRect,
    ) {
        if (mask == null) return
        if (mask.tileCount == 0 && mask.defaultAlpha == 255) return
        if (mask.tileCount == 0 && mask.defaultAlpha == 0) {
            batchCoverage.fill(0, offset, offset + stride * stride)
            return
        }
        val required = stride * stride
        if (maskCoverageScratch.size < required) maskCoverageScratch = ByteArray(required)
        if (mask.logicalWidth == projection.logicalWidth && mask.logicalHeight == projection.logicalHeight) {
            mask.copyAlpha(bounds, maskCoverageScratch, 0, stride)
        } else {
            for (localY in 0 until bounds.height) {
                var index = localY * stride
                val y = bounds.top + localY
                repeat(bounds.width) { localX ->
                    val x = bounds.left + localX
                    val maskX = ((x + .5f) * mask.logicalWidth / projection.logicalWidth)
                        .toInt().coerceIn(0, mask.logicalWidth - 1)
                    val maskY = ((y + .5f) * mask.logicalHeight / projection.logicalHeight)
                        .toInt().coerceIn(0, mask.logicalHeight - 1)
                    maskCoverageScratch[index++] = mask.alphaAt(maskX, maskY).toByte()
                }
            }
        }
        for (localY in 0 until bounds.height) {
            var index = localY * stride
            var coverageIndex = offset + index
            repeat(bounds.width) {
                batchCoverage[coverageIndex] = multiplyByte(
                    batchCoverage[coverageIndex],
                    maskCoverageScratch[index++].toInt().and(0xff),
                )
                coverageIndex++
            }
        }
    }

    private fun multiplyByte(current: Byte, factor: Int): Byte =
        ((current.toInt().and(0xff) * factor.coerceIn(0, 255) + 127) / 255).toByte()

    private fun <T> readResolvedRgba8Tile(
        input: PixelLayerProjectionInput,
        key: PixelTileKey,
        block: (IntArray) -> T,
    ): T? {
        val delta = input.activeDelta
        if (delta?.generationAt(key) == null) return input.pixels.readRgba8Tile(key, block)
        val required = projection.tileSize * projection.tileSize
        if (activeRgba8Scratch.size < required) activeRgba8Scratch = IntArray(required)
        activeRgba8Scratch.fill(0)
        input.pixels.readRgba8Tile(key) { it.copyInto(activeRgba8Scratch) }
        delta.readRgba8Tile(key) { source ->
            source.indices.forEach { index ->
                activeRgba8Scratch[index] = activeDeltaPixel(
                    activeRgba8Scratch[index],
                    source[index],
                    input.activeDeltaBlend,
                )
            }
        }
        return block(activeRgba8Scratch)
    }

    private fun alphaAt(input: PixelLayerProjectionInput, x: Int, y: Int): Float {
        val destination = input.pixels.pixelAt(x, y)
        val source = input.activeDelta?.pixelAt(x, y) ?: 0
        val resolved = if (source == 0) destination else activeDeltaPixel(destination, source, input.activeDeltaBlend)
        return (resolved ushr 24 and 0xff) / 255f
    }

    private fun activeDeltaPixel(destination: Int, source: Int, blend: PixelActiveDeltaBlend): Int = when (blend) {
        PixelActiveDeltaBlend.SOURCE_OVER -> sourceOver(destination, source)
        PixelActiveDeltaBlend.DESTINATION_OUT -> destinationOut(destination, source ushr 24 and 0xff)
        PixelActiveDeltaBlend.SOURCE_ATOP -> sourceAtop(destination, source)
    }

    private fun sourceOver(destination: Int, source: Int): Int {
        val sourceAlpha = source ushr 24 and 0xff
        if (sourceAlpha == 0) return destination
        if (sourceAlpha == 255) return source
        val inverse = 255 - sourceAlpha
        val destinationAlpha = destination ushr 24 and 0xff
        return ((sourceAlpha + destinationAlpha * inverse / 255).coerceAtMost(255) shl 24) or
            ((((source ushr 16) and 0xff) + ((destination ushr 16) and 0xff) * inverse / 255).coerceAtMost(255) shl 16) or
            ((((source ushr 8) and 0xff) + ((destination ushr 8) and 0xff) * inverse / 255).coerceAtMost(255) shl 8) or
            (((source and 0xff) + (destination and 0xff) * inverse / 255).coerceAtMost(255))
    }

    private fun destinationOut(destination: Int, sourceAlpha: Int): Int {
        val keep = 255 - sourceAlpha.coerceIn(0, 255)
        return ((((destination ushr 24) and 0xff) * keep / 255) shl 24) or
            ((((destination ushr 16) and 0xff) * keep / 255) shl 16) or
            ((((destination ushr 8) and 0xff) * keep / 255) shl 8) or
            ((destination and 0xff) * keep / 255)
    }

    private fun sourceAtop(destination: Int, source: Int): Int {
        val destinationAlpha = destination ushr 24 and 0xff
        if (destinationAlpha == 0) return destination
        val sourceAlpha = source ushr 24 and 0xff
        if (sourceAlpha == 0) return destination
        val inverse = 255 - sourceAlpha
        return (destinationAlpha shl 24) or
            (((((source ushr 16) and 0xff) * destinationAlpha + ((destination ushr 16) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 16) or
            (((((source ushr 8) and 0xff) * destinationAlpha + ((destination ushr 8) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 8) or
            ((((source and 0xff) * destinationAlpha + (destination and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha))
    }

    private fun prepare(layers: List<PixelLayerProjectionInput>): List<PreparedLayer> {
        var currentBase: PixelLayerProjectionInput? = null
        return layers.map { input ->
            val opacity = if (input.opacity.isFinite()) input.opacity.coerceIn(0f, 1f) else 0f
            val base = if (input.clipToBaseBelow) currentBase else null
            if (!input.clipToBaseBelow && input.isClippingBase) currentBase = input
            val zeroCoverage = !input.visible || opacity <= 0f ||
                (input.clipToBaseBelow && (base == null || !base.visible))
            val coverage = when {
                !input.visible || opacity <= 0f -> ZERO_COVERAGE
                input.clipToBaseBelow && (base == null || !base.visible) -> ZERO_COVERAGE
                input.mask == null && base == null -> null
                else -> CompositeCoverage { x, y ->
                    val maskAlpha = input.mask?.let { alphaAt(it, x, y, projection.logicalWidth, projection.logicalHeight) } ?: 1f
                    val baseAlpha = base?.let {
                        val baseOpacity = if (it.opacity.isFinite()) it.opacity.coerceIn(0f, 1f) else 0f
                        val baseMask = it.mask?.let { mask -> alphaAt(mask, x, y, projection.logicalWidth, projection.logicalHeight) } ?: 1f
                        alphaAt(it, x, y) * baseMask * baseOpacity
                    } ?: 1f
                    maskAlpha * baseAlpha
                }
            }
            PreparedLayer(
                input = input,
                opacity = if (input.visible) opacity else 0f,
                clippingBase = base,
                coverage = coverage,
                zeroCoverage = zeroCoverage,
            )
        }
    }

    private fun buildSignatures(layers: List<PreparedLayer>): Map<PixelTileKey, TileSignature> {
        val keys = layers.asSequence()
            .filter { it.opacity > 0f }
            .flatMap { layer -> (layer.input.pixels.tileKeys + layer.input.activeDelta?.tileKeys.orEmpty()).asSequence() }
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
        input.activeDelta?.let { delta ->
            require(delta.logicalWidth == projection.logicalWidth && delta.logicalHeight == projection.logicalHeight &&
                delta.tileSize == projection.tileSize && delta.pixelFormat == projection.pixelFormat
            ) { "Layer ${input.layerId} active delta is incompatible with the projection surface" }
            require(delta.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) {
                "Realtime active deltas currently require premultiplied RGBA8"
            }
        }
    }

    private fun maskDependency(
        mask: MaskTileSurfaceSnapshot?,
        sourceKey: PixelTileKey,
    ): Long? {
        if (mask == null) return null
        return run {
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
                        val generation = mask.generationAt(PixelTileKey(column, row)) ?: 0L
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

    private inner class PreparedLayer(
        val input: PixelLayerProjectionInput,
        val opacity: Float,
        val clippingBase: PixelLayerProjectionInput?,
        val coverage: CompositeCoverage?,
        val zeroCoverage: Boolean,
    ) {
        fun contributes(key: PixelTileKey): Boolean = opacity > 0f &&
            (input.pixels.generationAt(key) != null || input.activeDelta?.generationAt(key) != null)

        val requiresCoverage: Boolean get() = zeroCoverage || input.mask != null || clippingBase != null

        fun isNativeBatchCompatible(): Boolean = input.activeDelta == null &&
            input.colorReplacement == null &&
            input.blendMode.ordinal <= PixelBlendMode.EXCLUSION.ordinal

        fun <T> readRgba8Tile(key: PixelTileKey, block: (IntArray) -> T): T? =
            readResolvedRgba8Tile(input, key) { source ->
                val replacement = input.colorReplacement
                if (replacement == null) block(source)
                else block(IntArray(source.size) { PixelLayerColor.replaceRgba8(source[it], replacement) })
            }

        fun <T> readF16Tile(key: PixelTileKey, block: (LongArray) -> T): T? =
            input.pixels.readF16Tile(key) { source ->
                val replacement = input.colorReplacement
                if (replacement == null) block(source)
                else block(LongArray(source.size) { PixelLayerColor.replaceF16(source[it], replacement) })
            }

        fun dependency(key: PixelTileKey): LayerDependency? {
            val sourceGeneration = input.pixels.generationAt(key)
            val deltaGeneration = input.activeDelta?.generationAt(key)
            if (sourceGeneration == null && deltaGeneration == null) return null
            return LayerDependency(
                layerId = input.layerId,
                surfaceId = input.pixels.surfaceId,
                sourceGeneration = sourceGeneration,
                activeDeltaSurfaceId = input.activeDelta?.surfaceId,
                activeDeltaGeneration = deltaGeneration,
                activeDeltaBlend = input.activeDeltaBlend,
                isClippingBase = input.isClippingBase,
                visible = input.visible,
                opacityBits = opacity.toRawBits(),
                blendMode = input.blendMode,
                colorReplacement = input.colorReplacement,
                maskSurfaceId = input.mask?.surfaceId,
                maskGeneration = maskDependency(input.mask, key),
                maskDefaultAlpha = input.mask?.defaultAlpha,
                clipToBaseBelow = input.clipToBaseBelow,
                baseLayerId = clippingBase?.layerId,
                baseSurfaceId = clippingBase?.pixels?.surfaceId,
                baseGeneration = clippingBase?.pixels?.generationAt(key),
                baseActiveDeltaSurfaceId = clippingBase?.activeDelta?.surfaceId,
                baseActiveDeltaGeneration = clippingBase?.activeDelta?.generationAt(key),
                baseActiveDeltaBlend = clippingBase?.activeDeltaBlend,
                baseVisible = clippingBase?.visible,
                baseOpacityBits = clippingBase?.opacity?.toRawBits(),
                baseMaskSurfaceId = clippingBase?.mask?.surfaceId,
                baseMaskGeneration = maskDependency(clippingBase?.mask, key),
                baseMaskDefaultAlpha = clippingBase?.mask?.defaultAlpha,
            )
        }
    }

    private data class TileSignature(val layers: List<LayerDependency>)

    private data class Rgba8Underlay(
        val signature: TileSignature,
        val pixels: IntArray,
    )

    private data class ProjectionCheckpointKey(val key: PixelTileKey, val layerCount: Int)

    private data class LayerDependency(
        val layerId: String,
        val surfaceId: String,
        val sourceGeneration: Long?,
        val activeDeltaSurfaceId: String?,
        val activeDeltaGeneration: Long?,
        val activeDeltaBlend: PixelActiveDeltaBlend,
        val isClippingBase: Boolean,
        val visible: Boolean,
        val opacityBits: Int,
        val blendMode: PixelBlendMode,
        val colorReplacement: LayerColorReplacement?,
        val maskSurfaceId: String?,
        val maskGeneration: Long?,
        val maskDefaultAlpha: Int?,
        val clipToBaseBelow: Boolean,
        val baseLayerId: String?,
        val baseSurfaceId: String?,
        val baseGeneration: Long?,
        val baseActiveDeltaSurfaceId: String?,
        val baseActiveDeltaGeneration: Long?,
        val baseActiveDeltaBlend: PixelActiveDeltaBlend?,
        val baseVisible: Boolean?,
        val baseOpacityBits: Int?,
        val baseMaskSurfaceId: String?,
        val baseMaskGeneration: Long?,
        val baseMaskDefaultAlpha: Int?,
    )

    /**
     * Complete layer-level dependency for the zero-dirty-frame fast path. Tile generation changes
     * advance their owning surface revision, while every non-pixel value that affects composition
     * is retained explicitly. Equality therefore cannot turn a real edit into a reused frame.
     */
    private data class ProjectionInputState(
        val layerId: String,
        val surfaceId: String,
        val surfaceRevision: Long,
        val visible: Boolean,
        val opacityBits: Int,
        val blendMode: PixelBlendMode,
        val colorReplacement: LayerColorReplacement?,
        val maskSurfaceId: String?,
        val maskRevision: Long?,
        val maskDefaultAlpha: Int?,
        val clipToBaseBelow: Boolean,
        val activeDeltaSurfaceId: String?,
        val activeDeltaRevision: Long?,
        val activeDeltaBlend: PixelActiveDeltaBlend,
    ) {
        companion object {
            fun from(input: PixelLayerProjectionInput): ProjectionInputState = ProjectionInputState(
                layerId = input.layerId,
                surfaceId = input.pixels.surfaceId,
                surfaceRevision = input.pixels.revision,
                visible = input.visible,
                opacityBits = input.opacity.toRawBits(),
                blendMode = input.blendMode,
                colorReplacement = input.colorReplacement,
                maskSurfaceId = input.mask?.surfaceId,
                maskRevision = input.mask?.revision,
                maskDefaultAlpha = input.mask?.defaultAlpha,
                clipToBaseBelow = input.clipToBaseBelow,
                activeDeltaSurfaceId = input.activeDelta?.surfaceId,
                activeDeltaRevision = input.activeDelta?.revision,
                activeDeltaBlend = input.activeDeltaBlend,
            )
        }
    }

    companion object {
        private const val UNDERLAY_LAYER_THRESHOLD = 8
        private const val UNDERLAY_CHECKPOINT_INTERVAL = 8
        private const val MAX_CACHED_UNDERLAY_CHECKPOINTS = 64
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
            val maskX = ((x + .5f) * snapshot.logicalWidth / projectionWidth)
                .toInt().coerceIn(0, snapshot.logicalWidth - 1)
            val maskY = ((y + .5f) * snapshot.logicalHeight / projectionHeight)
                .toInt().coerceIn(0, snapshot.logicalHeight - 1)
            return snapshot.alphaAt(maskX, maskY) / 255f
        }
    }
}
