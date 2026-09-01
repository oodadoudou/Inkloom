package app.inkloom.ui

import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerHierarchy
import app.inkloom.document.PageCropTransform
import app.inkloom.document.RasterTileSurfaceRef
import app.inkloom.document.isPaintSurfaceLayer
import app.inkloom.engine.v5.surface.PixelTileSurface

/**
 * Page-scoped owner of the editable raster surfaces used by the production canvas.
 *
 * A document reference is a durable checkpoint, not a newer live surface. Saving a gesture can
 * finish after later gestures have already changed the in-memory tiles, so Compose recomposition
 * must not rebuild the canvas from every checkpoint it observes. [acceptPublished] associates the
 * checkpoint with the still-authoritative live surface. A genuinely different document source
 * (undo, clear, crop, rotation, layer replacement) is reloaded by [reconcile].
 */
internal class RasterLayerSurfaceRuntime(
    private val renderWidth: Int,
    private val loader: (InkPage, String, Int) -> PixelTileSurface,
) {
    init {
        require(renderWidth > 0) { "renderWidth must be positive" }
    }

    private val surfaces = linkedMapOf<String, PixelTileSurface>()
    private val sourceKeys = mutableMapOf<String, RasterSurfaceSourceKey>()
    private val locallyPublishedKeys = mutableMapOf<String, LinkedHashSet<RasterSurfaceSourceKey>>()

    /** Returns stable live surfaces while bringing external document revisions into the runtime. */
    @Synchronized
    fun reconcile(page: InkPage): Map<String, PixelTileSurface> {
        val visibleLayers = page.layers.filter { layer ->
            layer.isPaintSurfaceLayer() && LayerHierarchy.effectiveVisible(page.layers, layer)
        }
        val visibleIds = visibleLayers.mapTo(mutableSetOf(), InkLayer::id)
        (surfaces.keys - visibleIds).forEach { layerId ->
            surfaces.remove(layerId)
            sourceKeys.remove(layerId)
            locallyPublishedKeys.remove(layerId)
        }
        visibleLayers.forEach { layer ->
            val incoming = RasterSurfaceSourceKey.from(page, layer, renderWidth)
            val current = surfaces[layer.id]
            val currentSource = sourceKeys[layer.id]
            val published = locallyPublishedKeys[layer.id]
            when {
                current == null -> {
                    surfaces[layer.id] = loader(page, layer.id, renderWidth)
                    sourceKeys[layer.id] = incoming
                    locallyPublishedKeys.remove(layer.id)
                }
                incoming == currentSource || published?.contains(incoming) == true -> {
                    // The disk checkpoint may lag newer gestures already present in current.
                    sourceKeys[layer.id] = incoming
                    if (published?.remove(incoming) == true && published.isEmpty()) {
                        locallyPublishedKeys.remove(layer.id)
                    }
                }
                else -> {
                    surfaces[layer.id] = loader(page, layer.id, renderWidth)
                    sourceKeys[layer.id] = incoming
                    locallyPublishedKeys.remove(layer.id)
                }
            }
        }
        return surfaces.toMap()
    }

    /** Marks a successfully saved checkpoint without replacing the newer live tile generations. */
    @Synchronized
    fun acceptPublished(page: InkPage, layerId: String, reference: RasterTileSurfaceRef, rotationQuarterTurns: Int) {
        if (layerId !in surfaces) return
        val layer = page.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() } ?: return
        val key = RasterSurfaceSourceKey.from(
            page,
            layer.copy(
                rasterTileSurface = reference,
                rasterRotationQuarterTurns = rotationQuarterTurns.mod(4),
            ),
            renderWidth,
        )
        locallyPublishedKeys.getOrPut(layerId, ::linkedSetOf).add(key)
    }

    @Synchronized
    internal fun surfaceForTests(layerId: String): PixelTileSurface? = surfaces[layerId]
}

internal data class RasterSurfaceSourceKey(
    val layerId: String,
    val manifestPath: String?,
    val surfaceId: String?,
    val revision: Long?,
    val rotationQuarterTurns: Int,
    val cropLeftBits: Int,
    val cropTopBits: Int,
    val cropRightBits: Int,
    val cropBottomBits: Int,
    val renderWidth: Int,
) {
    companion object {
        fun from(page: InkPage, layer: InkLayer, renderWidth: Int): RasterSurfaceSourceKey {
            val crop = PageCropTransform.normalized(page.crop)
            val reference = layer.rasterTileSurface
            return RasterSurfaceSourceKey(
                layerId = layer.id,
                manifestPath = reference?.manifestAssetPath,
                surfaceId = reference?.surfaceId,
                revision = reference?.revision,
                rotationQuarterTurns = layer.rasterRotationQuarterTurns.mod(4),
                cropLeftBits = crop.left.toBits(),
                cropTopBits = crop.top.toBits(),
                cropRightBits = crop.right.toBits(),
                cropBottomBits = crop.bottom.toBits(),
                renderWidth = renderWidth,
            )
        }
    }
}
