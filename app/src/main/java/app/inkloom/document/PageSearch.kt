package app.inkloom.document

enum class PageSearchSource { PAGE, OBJECT, PDF }

data class PageSearchHit(
    val pageId: String,
    val snippet: String,
    val source: PageSearchSource,
    val bounds: PdfTextBounds? = null,
)

object PageSearch {
    fun find(page: InkPage, rawQuery: String): PageSearchHit? {
        val query = rawQuery.trim()
        if (query.isEmpty()) return null

        page.title.match(query)?.let { return PageSearchHit(page.id, snippet(it, query), PageSearchSource.PAGE) }
        page.customNumber?.match(query)?.let { return PageSearchHit(page.id, snippet(it, query), PageSearchSource.PAGE) }
        page.tags.firstOrNull { it.contains(query, ignoreCase = true) }?.let { return PageSearchHit(page.id, "#$it", PageSearchSource.PAGE) }

        page.elements.forEach { element ->
            val text = when (element) {
                is PageElement.Text -> element.text
                is PageElement.Sticky -> element.text
                is PageElement.AudioMarker -> element.title
                is PageElement.Link -> "${element.title}\n${element.url}"
                else -> null
            }
            text?.match(query)?.let { return PageSearchHit(page.id, snippet(it, query), PageSearchSource.OBJECT) }
        }

        page.pdfTextBlocks.forEach { block ->
            block.text.match(query)?.let {
                return PageSearchHit(page.id, snippet(it, query), PageSearchSource.PDF, block.bounds.firstOrNull())
            }
        }
        return null
    }

    fun matches(page: InkPage, rawQuery: String): Boolean = rawQuery.isBlank() || find(page, rawQuery) != null

    private fun String.match(query: String): String? = takeIf { contains(query, ignoreCase = true) }

    private fun snippet(text: String, query: String, radius: Int = 52): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val index = normalized.indexOf(query, ignoreCase = true).coerceAtLeast(0)
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + query.length + radius).coerceAtMost(normalized.length)
        return buildString {
            if (start > 0) append("…")
            append(normalized.substring(start, end))
            if (end < normalized.length) append("…")
        }
    }
}
