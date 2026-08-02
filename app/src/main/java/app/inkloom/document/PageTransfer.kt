package app.inkloom.document

import java.util.UUID

data class PageTransferResult(val source: InkDocument, val target: InkDocument, val transferredPageId: String)

object PageTransfer {
    fun apply(
        source: InkDocument,
        target: InkDocument,
        pageId: String,
        copy: Boolean,
        now: Long = System.currentTimeMillis(),
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): PageTransferResult? {
        if (source.id == target.id) return null
        val page = source.pages.firstOrNull { it.id == pageId } ?: return null
        val transferred = if (copy) page.copy(
            id = nextId(),
            title = "${page.title} copy",
            strokes = page.strokes.map { it.copy(id = nextId()) },
            elements = page.elements.map { it.copyWithId(nextId(), nextId) },
        ) else page
        val updatedTarget = target.copy(pages = target.pages + transferred, updatedAt = now)
        val updatedSource = if (copy) source else source.copy(
            pages = source.pages.filterNot { it.id == pageId }.ifEmpty { listOf(InkPage(id = nextId(), title = "Page 1")) },
            updatedAt = now,
        )
        return PageTransferResult(updatedSource, updatedTarget, transferred.id)
    }

    private fun PageElement.copyWithId(id: String, nextId: () -> String): PageElement = when (this) {
        is PageElement.Text -> copy(id = id)
        is PageElement.Sticky -> copy(id = id, strokes = strokes.map { it.copy(id = nextId()) })
        is PageElement.Shape -> copy(id = id)
        is PageElement.Image -> copy(id = id)
        is PageElement.AudioMarker -> copy(id = id)
        is PageElement.Link -> copy(id = id)
    }
}
