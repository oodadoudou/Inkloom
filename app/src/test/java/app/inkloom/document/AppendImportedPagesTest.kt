package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppendImportedPagesTest {
    @Test fun appendsBatchInOrderWithoutChangingExistingPageOrLinkTargets() {
        val original = InkPage(title = "Annotated PDF", bookmarked = true)
        val first = InkPage(title = "chapter · 1")
        val second = InkPage(title = "chapter · 2", pdfGotoLinks = listOf(
            PdfGotoLink(emptyList(), first.id),
        ))
        val source = InkDocument(title = "Reading", pages = listOf(original))

        val result = AppendImportedPages.apply(source, listOf(first, second), now = 42L)

        assertEquals(listOf(original, first, second), result.pages)
        assertEquals(first.id, result.pages.last().pdfGotoLinks.single().destinationPageId)
        assertEquals(42L, result.updatedAt)
        assertEquals(listOf(original), source.pages)
    }

    @Test fun rejectsDuplicateIdsAndEmptyBatchWithoutChangingSource() {
        val source = InkDocument(title = "Reading", pages = listOf(InkPage()))
        assertThrows(IllegalArgumentException::class.java) { AppendImportedPages.apply(source, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { AppendImportedPages.apply(source, listOf(source.pages.single())) }
        assertEquals(1, source.pages.size)
    }
}
