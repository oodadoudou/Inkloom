package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface

/** Children are ordered bottom-to-top. Group effects are applied once to the composite children. */
sealed interface PixelProjectionNode {
    data class Paint(val input: PixelLayerProjectionInput) : PixelProjectionNode
    data class Group(
        val id: String,
        val children: List<PixelProjectionNode>,
        val opacity: Float = 1f,
        val blendMode: PixelBlendMode = PixelBlendMode.NORMAL,
        val mask: MaskTileSurfaceSnapshot? = null,
        val clipToBaseBelow: Boolean = false,
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
    private val root = PixelLayerProjectionCache(width, height, tileSize, format, surfaceId)
    private val groups = mutableMapOf<String, PixelLayerProjectionCache>()
    val snapshot get() = root.snapshot

    fun update(nodes: List<PixelProjectionNode>): PixelLayerProjectionUpdate {
        val used = hashSetOf<String>()
        var recomposed = 0
        var reused = 0
        fun inputs(children: List<PixelProjectionNode>): List<PixelLayerProjectionInput> = children.map { node ->
            when (node) {
                is PixelProjectionNode.Paint -> node.input
                is PixelProjectionNode.Group -> {
                    require(used.add(node.id)) { "Duplicate or cyclic projection group ${node.id}" }
                    val cache = groups.getOrPut(node.id) {
                        PixelLayerProjectionCache(width, height, tileSize, format, "$surfaceId/group/${node.id}")
                    }
                    val update = cache.update(inputs(node.children))
                    recomposed += update.recomposedTileCount
                    reused += update.reusedTileCount
                    PixelLayerProjectionInput(node.id, update.snapshot, opacity = node.opacity,
                        blendMode = node.blendMode, mask = node.mask, clipToBaseBelow = node.clipToBaseBelow)
                }
            }
        }
        val update = root.update(inputs(nodes))
        groups.keys.retainAll(used)
        return update.copy(recomposedTileCount = recomposed + update.recomposedTileCount,
            reusedTileCount = reused + update.reusedTileCount)
    }
}
