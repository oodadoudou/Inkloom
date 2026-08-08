package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalLinkTargetTest {
    @Test
    fun `resolves a page in another notebook`() {
        val page = InkPage(title = "Methods")
        val document = InkDocument(title = "Research", pages = listOf(page))
        val link = PageElement.Link(
            x = 0f,
            y = 0f,
            title = "Methods",
            targetDocumentId = document.id,
            targetPageId = page.id,
        )

        val resolved = InternalLinkTarget.resolve(link, listOf(document))

        assertEquals(document.id, resolved?.documentId)
        assertEquals(page.id, resolved?.pageId)
        assertEquals("Research · Methods", InternalLinkTarget.label(link, listOf(document)))
    }

    @Test
    fun `reports a deleted destination as unavailable`() {
        val link = PageElement.Link(x = 0f, y = 0f, title = "Missing", targetDocumentId = "gone", targetPageId = "gone")

        assertNull(InternalLinkTarget.resolve(link, emptyList()))
        assertEquals("Unavailable page", InternalLinkTarget.label(link, emptyList()))
    }
}
