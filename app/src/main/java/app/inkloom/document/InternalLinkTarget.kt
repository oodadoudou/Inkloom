package app.inkloom.document

data class ResolvedInternalLink(
    val documentId: String,
    val pageId: String,
    val documentTitle: String,
    val pageTitle: String,
)

object InternalLinkTarget {
    fun isInternal(link: PageElement.Link): Boolean =
        !link.targetDocumentId.isNullOrBlank() && !link.targetPageId.isNullOrBlank()

    fun resolve(link: PageElement.Link, documents: List<InkDocument>): ResolvedInternalLink? {
        val documentId = link.targetDocumentId ?: return null
        val pageId = link.targetPageId ?: return null
        val document = documents.firstOrNull { it.id == documentId } ?: return null
        val page = document.pages.firstOrNull { it.id == pageId } ?: return null
        return ResolvedInternalLink(document.id, page.id, document.title, page.title)
    }

    fun label(link: PageElement.Link, documents: List<InkDocument>): String =
        resolve(link, documents)?.let { "${it.documentTitle} · ${it.pageTitle}" } ?: "Unavailable page"
}
