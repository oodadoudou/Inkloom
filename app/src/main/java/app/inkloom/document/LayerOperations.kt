package app.inkloom.document

import java.util.UUID

data class LayerMutation(
    val page: InkPage,
    val activeLayerId: String,
)

object LayerOperations {
    fun duplicate(
        page: InkPage,
        sourceLayerId: String,
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): LayerMutation? {
        val sourceIndex = page.layers.indexOfFirst { it.id == sourceLayerId }
        if (sourceIndex < 0) return null
        val source = page.layers[sourceIndex]
        if (source.role == LayerRole.BACKGROUND) return null

        val duplicateId = nextId()
        val duplicate = source.copy(
            id = duplicateId,
            name = uniqueCopyName(source.name, page.layers.map(InkLayer::name)),
            role = LayerRole.CUSTOM,
        )
        val layers = page.layers.toMutableList().apply { add(sourceIndex + 1, duplicate) }
        val copiedStrokes = page.strokes.filter { it.layerId == sourceLayerId }.map { stroke ->
            stroke.copy(id = nextId(), layerId = duplicateId)
        }
        val copiedElements = page.elements.filter { it.layerId == sourceLayerId }.map { element ->
            copyElement(element, duplicateId, nextId)
        }
        return LayerMutation(
            page = page.copy(
                layers = layers,
                strokes = page.strokes + copiedStrokes,
                elements = page.elements + copiedElements,
            ),
            activeLayerId = duplicateId,
        )
    }

    fun canMergeDown(page: InkPage, sourceLayerId: String): Boolean {
        val sourceIndex = page.layers.indexOfFirst { it.id == sourceLayerId }
        if (sourceIndex <= 0) return false
        val source = page.layers[sourceIndex]
        val target = page.layers[sourceIndex - 1]
        return source.role != LayerRole.BACKGROUND &&
            target.role != LayerRole.BACKGROUND &&
            !source.locked && !target.locked &&
            source.visible == target.visible &&
            source.blendMode == LayerBlendMode.NORMAL &&
            target.blendMode == LayerBlendMode.NORMAL
    }

    fun mergeDown(page: InkPage, sourceLayerId: String): LayerMutation? {
        if (!canMergeDown(page, sourceLayerId)) return null
        val sourceIndex = page.layers.indexOfFirst { it.id == sourceLayerId }
        val source = page.layers[sourceIndex]
        val target = page.layers[sourceIndex - 1]
        val mergedStrokes = page.strokes.filterNot { it.layerId == sourceLayerId || it.layerId == target.id } +
            page.strokes.filter { it.layerId == target.id }.map { it.copy(opacity = it.opacity * target.opacity) } +
            page.strokes.filter { it.layerId == sourceLayerId }.map { it.copy(layerId = target.id, opacity = it.opacity * source.opacity) }
        val mergedElements = page.elements.filterNot { it.layerId == sourceLayerId || it.layerId == target.id } +
            page.elements.filter { it.layerId == target.id }.map { it.withOpacity(it.opacity * target.opacity) } +
            page.elements.filter { it.layerId == sourceLayerId }.map { it.withLayer(target.id).withOpacity(it.opacity * source.opacity) }
        return LayerMutation(
            page = page.copy(
                layers = page.layers.filterNot { it.id == sourceLayerId }.map { if (it.id == target.id) it.copy(opacity = 1f) else it },
                strokes = mergedStrokes,
                elements = mergedElements,
            ),
            activeLayerId = target.id,
        )
    }

    private fun uniqueCopyName(sourceName: String, existingNames: List<String>): String {
        val existing = existingNames.mapTo(mutableSetOf()) { it.lowercase() }
        val base = "$sourceName copy"
        if (base.lowercase() !in existing) return base
        var suffix = 2
        while ("$base $suffix".lowercase() in existing) suffix++
        return "$base $suffix"
    }

    private fun copyElement(element: PageElement, layerId: String, nextId: () -> String): PageElement = when (element) {
        is PageElement.Text -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Sticky -> element.copy(
            id = nextId(),
            layerId = layerId,
            strokes = element.strokes.map { it.copy(id = nextId()) },
        )
        is PageElement.Shape -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Image -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.AudioMarker -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Link -> element.copy(id = nextId(), layerId = layerId)
    }

    private fun PageElement.withLayer(layerId: String): PageElement = when (this) {
        is PageElement.Text -> copy(layerId = layerId)
        is PageElement.Sticky -> copy(layerId = layerId)
        is PageElement.Shape -> copy(layerId = layerId)
        is PageElement.Image -> copy(layerId = layerId)
        is PageElement.AudioMarker -> copy(layerId = layerId)
        is PageElement.Link -> copy(layerId = layerId)
    }

    private fun PageElement.withOpacity(value: Float): PageElement = when (this) {
        is PageElement.Text -> copy(opacity = value)
        is PageElement.Sticky -> copy(opacity = value)
        is PageElement.Shape -> copy(opacity = value)
        is PageElement.Image -> copy(opacity = value)
        is PageElement.AudioMarker -> copy(opacity = value)
        is PageElement.Link -> copy(opacity = value)
    }
}
