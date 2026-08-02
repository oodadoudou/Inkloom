package app.inkloom.document

object PageOutline {
    fun changeLevel(pages: List<InkPage>, pageId: String, delta: Int): List<InkPage> {
        val index = pages.indexOfFirst { it.id == pageId }
        if (index < 0) return pages
        val current = pages[index]
        val maximum = if (index == 0) 0 else (pages[index - 1].outlineLevel.coerceIn(0, 3) + 1).coerceAtMost(3)
        val level = (current.outlineLevel + delta).coerceIn(0, maximum)
        if (level == current.outlineLevel) return pages
        return pages.mapIndexed { pageIndex, page -> if (pageIndex == index) page.copy(outlineLevel = level, outlineCollapsed = false) else page }
    }

    fun hasChildren(pages: List<InkPage>, index: Int): Boolean = index in pages.indices && index < pages.lastIndex && pages[index + 1].outlineLevel > pages[index].outlineLevel

    fun visiblePages(pages: List<InkPage>): List<InkPage> {
        val visible = mutableListOf<InkPage>()
        var hiddenBelowLevel: Int? = null
        pages.forEachIndexed { index, page ->
            val hiddenLevel = hiddenBelowLevel
            if (hiddenLevel != null && page.outlineLevel > hiddenLevel) return@forEachIndexed
            hiddenBelowLevel = null
            visible += page
            if (page.outlineCollapsed && hasChildren(pages, index)) hiddenBelowLevel = page.outlineLevel
        }
        return visible
    }
}
