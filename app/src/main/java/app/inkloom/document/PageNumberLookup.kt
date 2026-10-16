package app.inkloom.document

import java.util.Locale

/** Resolves a typed page number the way the pages panel labels pages. */
object PageNumberLookup {
    /** The label the pages panel shows for the page at [index]: its custom number, else its position. */
    fun label(pages: List<InkPage>, index: Int): String = pages[index].customNumber ?: (index + 1).toString()

    /**
     * An exact custom page number (ignoring case and surrounding spaces) wins, so a notebook that
     * numbers its pages "i, ii, 1, 2" jumps to the page labelled "2". Otherwise a whole number
     * selects that position, counting from 1.
     */
    fun resolve(pages: List<InkPage>, input: String): String? {
        val query = input.trim()
        if (query.isEmpty()) return null
        val key = query.lowercase(Locale.ROOT)
        pages.firstOrNull { it.customNumber?.trim()?.lowercase(Locale.ROOT) == key }?.let { return it.id }
        val position = query.toIntOrNull() ?: return null
        return pages.getOrNull(position - 1)?.id
    }
}
