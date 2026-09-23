package app.inkloom.document

import java.util.UUID

data class PageTransferResult(val source: InkDocument, val target: InkDocument, val transferredPageId: String, val transferredPageIds: List<String> = listOf(transferredPageId))

object PageTransfer {
    fun apply(
        source: InkDocument,
        target: InkDocument,
        pageId: String,
        copy: Boolean,
        now: Long = System.currentTimeMillis(),
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): PageTransferResult? = applyMany(source, target, setOf(pageId), copy, now, nextId)

    fun applyMany(
        source: InkDocument,
        target: InkDocument,
        pageIds: Set<String>,
        copy: Boolean,
        now: Long = System.currentTimeMillis(),
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): PageTransferResult? {
        if (source.id == target.id || pageIds.isEmpty() || target.trashedAt != null) return null
        val selected = source.pages.filter { it.id in pageIds }
        if (selected.size != pageIds.size) return null
        val idMap = if (copy) selected.associate { it.id to nextId() } else emptyMap()
        val transferred = selected.map { page ->
            if (copy) page.copy(
                id = idMap.getValue(page.id),
                title = "${page.title} copy",
                elements = page.elements.map { it.copyWithId(nextId(), nextId) },
                savedSelections = page.savedSelections.map { it.copy(id = nextId()) },
                recognizedTextBlocks = page.recognizedTextBlocks.map { it.copy(id = nextId()) },
            ) else page
        }.let { PdfGotoLinkNavigation.remapDestinations(it, idMap) }
        val updatedTarget = target.copy(pages = target.pages + transferred, updatedAt = now)
        val updatedSource = if (copy) source else source.copy(
            pages = source.pages.filterNot { it.id in pageIds }.ifEmpty { listOf(InkPage(id = nextId(), title = "Page 1")) },
            updatedAt = now,
        )
        return PageTransferResult(updatedSource, updatedTarget, transferred.first().id, transferred.map(InkPage::id))
    }

    private fun PageElement.copyWithId(id: String, nextId: () -> String): PageElement = when (this) {
        is PageElement.Text -> copy(id = id)
        is PageElement.Sticky -> copy(id = id)
        is PageElement.Shape -> copy(id = id)
        is PageElement.Image -> copy(id = id)
        is PageElement.Fill -> copy(id = id)
        is PageElement.Link -> copy(id = id)
    }
}
