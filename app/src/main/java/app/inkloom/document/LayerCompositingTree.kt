package app.inkloom.document

/** Layer storage is a bottom-to-top sibling sequence; parent links define actual compositing scope. */
data class LayerCompositingNode(val layer: InkLayer, val children: List<LayerCompositingNode> = emptyList())

object LayerCompositingTree {
    fun <T> project(layers: List<InkLayer>, paint: (InkLayer) -> T?, group: (InkLayer, List<T>) -> T): List<T> {
        fun projectNode(node: LayerCompositingNode): T? = if (node.layer.role == LayerRole.FOLDER)
            group(node.layer, node.children.mapNotNull(::projectNode)) else paint(node.layer)
        return roots(layers).mapNotNull(::projectNode)
    }

    fun roots(layers: List<InkLayer>): List<LayerCompositingNode> {
        val children = layers.groupBy(InkLayer::parentId)
        fun nodes(parent: String?): List<LayerCompositingNode> = children[parent].orEmpty()
            .filter { it.visible && it.role != LayerRole.BACKGROUND }
            .map { layer -> LayerCompositingNode(layer, if (layer.role == LayerRole.FOLDER) nodes(layer.id) else emptyList()) }
        return nodes(null)
    }
}
