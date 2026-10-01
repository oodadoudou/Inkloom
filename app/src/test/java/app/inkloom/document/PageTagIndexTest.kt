package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTagIndexTest {
    @Test fun categoriesDeduplicateTagsAndPagesWhilePreservingPageOrder() {
        val pages = listOf(InkPage(id = "b", tags = listOf("Study", " study ", " ", "Art")),
            InkPage(id = "a", tags = listOf("STUDY")), InkPage(id = "c"))
        assertEquals(listOf(PageTagGroup("Art", listOf("b")), PageTagGroup("Study", listOf("b", "a"))), PageTagIndex.groups(pages))
        assertEquals(emptyList<PageTagGroup>(), PageTagIndex.groups(listOf(pages.last())))
    }
}
