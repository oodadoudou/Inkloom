package app.inkloom.document

/** One document update for a prepared batch; callers publish only after every source succeeds. */
object AppendImportedPages {
    fun apply(document: InkDocument, imported: List<InkPage>, now: Long = System.currentTimeMillis()): InkDocument {
        require(imported.isNotEmpty()) { "No pages were imported" }
        val existingIds = document.pages.mapTo(hashSetOf(), InkPage::id)
        require(imported.map(InkPage::id).toSet().size == imported.size && imported.none { it.id in existingIds }) {
            "Imported pages contain duplicate page IDs"
        }
        require(document.pages.size.toLong() + imported.size <= MAX_NOTEBOOK_PAGES) {
            "Notebook exceeds the supported page limit"
        }
        return document.copy(pages = document.pages + imported, updatedAt = now)
    }
}
