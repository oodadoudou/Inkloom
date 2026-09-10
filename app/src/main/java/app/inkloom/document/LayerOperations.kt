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
        if (source.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) return null

        val duplicateId = nextId()
        val duplicate = source.copy(
            id = duplicateId,
            name = uniqueCopyName(source.name, page.layers.map(InkLayer::name)),
            role = LayerRole.CUSTOM,
        )
        val layers = page.layers.toMutableList().apply { add(sourceIndex + 1, duplicate) }
        val copiedElements = page.elements.filter { it.layerId == sourceLayerId }.map { element ->
            copyElement(element, duplicateId, nextId)
        }
        return LayerMutation(
            page = page.copy(
                layers = layers,
                elements = page.elements + copiedElements,
            ),
            activeLayerId = duplicateId,
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
        is PageElement.Sticky -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Shape -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Image -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Fill -> element.copy(id = nextId(), layerId = layerId)
        is PageElement.Link -> element.copy(id = nextId(), layerId = layerId)
    }

}
