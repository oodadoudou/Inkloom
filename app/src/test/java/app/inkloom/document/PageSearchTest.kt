package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSearchTest {
    @Test
    fun findsNativePdfTextWithItsApproximatePageBounds() {
        val bounds = PdfTextBounds(.12f, .22f, .72f, .28f)
        val page = InkPage(
            id = "pdf-page",
            pdfTextBlocks = listOf(PdfTextBlock("The transformer uses multi-head attention.", listOf(bounds))),
            pdfTextIndexVersion = 1,
        )

        val hit = PageSearch.find(page, "MULTI-head")!!

        assertEquals(PageSearchSource.PDF, hit.source)
        assertEquals(bounds, hit.bounds)
        assertTrue(hit.snippet.contains("multi-head", ignoreCase = true))
    }

    @Test
    fun pageMetadataAndObjectsRemainSearchable() {
        val metadata = InkPage(id = "metadata", title = "Lecture 7", tags = listOf("systems"))
        val objectText = InkPage(id = "object", elements = listOf(PageElement.Sticky(x = .1f, y = .1f, text = "Review theorem")))

        assertEquals(PageSearchSource.PAGE, PageSearch.find(metadata, "systems")?.source)
        assertEquals(PageSearchSource.OBJECT, PageSearch.find(objectText, "theorem")?.source)
    }

    @Test
    fun blankAndMissingQueriesDoNotProduceHits() {
        val page = InkPage(pdfTextBlocks = listOf(PdfTextBlock("Known text")))
        assertNull(PageSearch.find(page, ""))
        assertNull(PageSearch.find(page, "missing"))
    }
}
