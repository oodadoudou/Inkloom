package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.document.LayerAdjustment

/** Children are ordered bottom-to-top. Group effects are applied once to the composite children. */
sealed interface PixelProjectionNode {
    data class Paint(val input: PixelLayerProjectionInput) : PixelProjectionNode
    data class Adjustment(
        val id: String,
        val adjustment: LayerAdjustment,
        val opacity: Float = 1f,
        val blendMode: PixelBlendMode = PixelBlendMode.NORMAL,
        val mask: MaskTileSurfaceSnapshot? = null,
        val visible: Boolean = true,
        /** Limits the correction to the clipping base below and the clipped layers already above it. */
        val clipToBaseBelow: Boolean = false,
    ) : PixelProjectionNode
    data class Group(
        val id: String,
        val children: List<PixelProjectionNode>,
        val opacity: Float = 1f,
        val blendMode: PixelBlendMode = PixelBlendMode.NORMAL,
        val mask: MaskTileSurfaceSnapshot? = null,
        val clipToBaseBelow: Boolean = false,
        val visible: Boolean = true,
        val passThrough: Boolean = false,
    ) : PixelProjectionNode
}

/** Isolated group caches retain sparse per-tile dependencies, including live gesture deltas. */
class PixelGroupProjectionCache(
    private val width: Int,
    private val height: Int,
    private val tileSize: Int = PixelTileSurface.DEFAULT_TILE_SIZE,
    private val format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888,
    private val surfaceId: String = "group-projection-${java.util.UUID.randomUUID()}",
) {
    private val caches = mutableMapOf("root" to PixelLayerProjectionCache(width, height, tileSize, format, surfaceId))
    private val contributions = mutableMapOf<String, PixelBackdropInterpolationCache>()
    private val adjustments = mutableMapOf<String, PixelAdjustmentProjectionCache>()
    val emptySnapshot = PixelTileSurface(width, height, tileSize, "$surfaceId/empty", pixelFormat = format).snapshot()
    val snapshot get() = caches.getValue("root").snapshot

    fun update(nodes: List<PixelProjectionNode>): PixelLayerProjectionUpdate {
        val groupIds = hashSetOf<String>()
        val usedCaches = hashSetOf<String>()
        val usedContributions = hashSetOf<String>()
        val usedAdjustments = hashSetOf<String>()
        var recomposed = 0
        var reused = 0
        fun count(update: PixelLayerProjectionUpdate): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot {
            recomposed += update.recomposedTileCount
            reused += update.reusedTileCount
            return update.snapshot
        }
        fun compose(key: String, inputs: List<PixelLayerProjectionInput>): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot {
            usedCaches += key
            val cache = caches.getOrPut(key) { PixelLayerProjectionCache(width, height, tileSize, format, "$surfaceId/$key") }
            return count(cache.update(inputs))
        }
        fun project(children: List<PixelProjectionNode>, scope: String,
            initialBackdrop: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot? = null): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot {
            val inputs = mutableListOf<PixelLayerProjectionInput>()
            // Base for clipped inputs; it survives an adjustment folding the prefix into one backdrop.
            var clippingBase: PixelLayerProjectionInput? = null
            fun add(input: PixelLayerProjectionInput) {
                inputs += input
                if (!input.clipToBaseBelow && input.isClippingBase) clippingBase = input
            }
            initialBackdrop?.let { inputs += PixelLayerProjectionInput("$scope/backdrop", it, isClippingBase = false) }
            for (node in children) when (node) {
                is PixelProjectionNode.Paint -> add(node.input)
                is PixelProjectionNode.Adjustment -> {
                    val base = clippingBase.takeIf { node.clipToBaseBelow }
                    // A clipped correction without a visible base has nothing to correct.
                    if (!node.visible || (node.clipToBaseBelow && base?.visible != true)) continue
                    val before = compose("before-adjustment/${node.id}", inputs)
                    val coverage = base?.let {
                        compose("adjustment-clip/${node.id}", listOf(it.copy(layerId = "${node.id}/clip-base",
                            blendMode = PixelBlendMode.NORMAL, colorReplacement = null, clipToBaseBelow = false, clippingBaseOnly = false)))
                    }
                    usedAdjustments += node.id
                    val cache = adjustments.getOrPut(node.id) {
                        PixelAdjustmentProjectionCache(width, height, tileSize, format, "$surfaceId/adjustment/${node.id}")
                    }
                    val result = count(cache.update(before, node.adjustment, node.opacity.coerceIn(0f, 1f), node.mask,
                        node.blendMode, coverage))
                    inputs.clear()
                    inputs += PixelLayerProjectionInput(node.id, result, isClippingBase = false)
                    if (base != null) inputs += base.copy(clippingBaseOnly = true) else clippingBase = null
                }
                is PixelProjectionNode.Group -> {
                    require(groupIds.add(node.id)) { "Duplicate or cyclic projection group ${node.id}" }
                    when {
                        !node.visible -> add(PixelLayerProjectionInput(node.id, emptySnapshot, visible = false))
                        node.passThrough -> {
                            require(!node.clipToBaseBelow) { "Through groups cannot be clipped" }
                            val before = compose("before/${node.id}", inputs)
                            val after = project(node.children, "group/${node.id}", before)
                            usedContributions += node.id
                            val contribution = contributions.getOrPut(node.id) {
                                PixelBackdropInterpolationCache(width, height, tileSize, format, "$surfaceId/contribution/${node.id}")
                            }
                            val result = count(contribution.update(before, after, node.opacity, node.mask))
                            // The result already contains the previous backdrop; replace the prefix.
                            inputs.clear()
                            inputs += PixelLayerProjectionInput(node.id, result, isClippingBase = false)
                            clippingBase = null
                        }
                        else -> add(PixelLayerProjectionInput(node.id, project(node.children, "group/${node.id}"),
                            opacity = node.opacity, blendMode = node.blendMode, mask = node.mask, clipToBaseBelow = node.clipToBaseBelow))
                    }
                }
            }
            return compose(scope, inputs)
        }
        val result = project(nodes, "root")
        caches.keys.retainAll(usedCaches)
        contributions.keys.retainAll(usedContributions)
        adjustments.keys.retainAll(usedAdjustments)
        return PixelLayerProjectionUpdate(result, recomposed, reused)
    }
}
