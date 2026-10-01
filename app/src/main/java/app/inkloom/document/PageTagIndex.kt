package app.inkloom.document

/** Case-insensitive tag categories in page order, retaining the first readable label. */
data class PageTagGroup(val name: String, val pageIds: List<String>)
object PageTagIndex {
    fun groups(pages: List<InkPage>): List<PageTagGroup> {
        val names = linkedMapOf<String, String>()
        val ids = linkedMapOf<String, MutableSet<String>>()
        for (page in pages) for (tag in page.tags) {
            val label = tag.trim()
            if (label.isEmpty()) continue
            val key = label.lowercase()
            names.putIfAbsent(key, label)
            ids.getOrPut(key) { linkedSetOf() }.add(page.id)
        }
        return names.map { (key, name) -> PageTagGroup(name, ids.getValue(key).toList()) }
            .sortedBy { it.name.lowercase() }
    }
}
