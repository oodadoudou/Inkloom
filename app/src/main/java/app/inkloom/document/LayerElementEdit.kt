package app.inkloom.document

/** Targeted element delta travels with its raster checkpoint; unrelated imports are preserved. */
data class LayerElementEdit(val sourceIds: Set<String>, val replacement: List<PageElement>) {
    fun apply(elements: List<PageElement>): List<PageElement> =
        elements.filterNot { it.id in sourceIds } + replacement
}
