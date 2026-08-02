package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTransferTest {
    @Test fun movesPageAndKeepsSourceDocumentOpenable() {
        val page = InkPage(id = "page", strokes = listOf(InkStroke(id = "stroke", points = listOf(StrokePoint(.2f, .2f)))))
        val source = InkDocument(id = "source", title = "Source", pages = listOf(page))
        val target = InkDocument(id = "target", title = "Target", pages = listOf(InkPage(id = "target-page")))
        val ids = ArrayDeque(listOf("replacement-page"))

        val result = PageTransfer.apply(source, target, page.id, copy = false, now = 99L, nextId = ids::removeFirst)!!

        assertEquals(listOf("replacement-page"), result.source.pages.map(InkPage::id))
        assertEquals(listOf("target-page", "page"), result.target.pages.map(InkPage::id))
        assertEquals("page", result.transferredPageId)
        assertEquals(99L, result.source.updatedAt)
        assertEquals(99L, result.target.updatedAt)
    }

    @Test fun copiesPageWithFreshNestedIds() {
        val page = InkPage(
            id = "page",
            strokes = listOf(InkStroke(id = "stroke", points = listOf(StrokePoint(.2f, .2f)))),
            elements = listOf(PageElement.Sticky(id = "sticky", x = .1f, y = .1f, text = "Hello", strokes = listOf(InkStroke(id = "sticky-stroke", points = listOf(StrokePoint(.3f, .4f)))))),
        )
        val source = InkDocument(id = "source", title = "Source", pages = listOf(page))
        val target = InkDocument(id = "target", title = "Target")
        val ids = ArrayDeque(listOf("copy-page", "copy-stroke", "copy-sticky", "copy-sticky-stroke"))

        val result = PageTransfer.apply(source, target, page.id, copy = true, nextId = ids::removeFirst)!!
        val copied = result.target.pages.last()

        assertEquals(source, result.source)
        assertEquals("copy-page", copied.id)
        assertEquals("copy-stroke", copied.strokes.single().id)
        val copiedSticky = copied.elements.single() as PageElement.Sticky
        assertEquals("copy-sticky", copiedSticky.id)
        assertEquals("copy-sticky-stroke", copiedSticky.strokes.single().id)
        assertNotEquals(page.id, copied.id)
    }

    @Test fun rejectsSameDocumentOrMissingPage() {
        val source = InkDocument(id = "source", title = "Source")
        assertNull(PageTransfer.apply(source, source, source.pages.single().id, copy = false))
        assertNull(PageTransfer.apply(source, InkDocument(id = "target", title = "Target"), "missing", copy = false))
    }

    @Test fun copiedPagesGiveLinksFreshObjectIdsWithoutChangingTargets() {
        val page = InkPage(id = "page", elements = listOf(PageElement.Link(id = "link", x = .1f, y = .2f, title = "Guide", url = "https://example.com/guide")))
        val source = InkDocument(id = "source", title = "Source", pages = listOf(page))
        val target = InkDocument(id = "target", title = "Target")
        val ids = ArrayDeque(listOf("copy-page", "copy-link"))

        val copiedLink = PageTransfer.apply(source, target, page.id, copy = true, nextId = ids::removeFirst)!!.target.pages.last().elements.single() as PageElement.Link

        assertEquals("copy-link", copiedLink.id)
        assertEquals("https://example.com/guide", copiedLink.url)
    }
}
