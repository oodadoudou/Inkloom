package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTransferTest {
    @Test fun movesPageAndKeepsSourceDocumentOpenable() {
        val page = InkPage(id = "page")
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
            elements = listOf(PageElement.Sticky(id = "sticky", x = .1f, y = .1f, text = "Hello")),
        )
        val source = InkDocument(id = "source", title = "Source", pages = listOf(page))
        val target = InkDocument(id = "target", title = "Target")
        val ids = ArrayDeque(listOf("copy-page", "copy-sticky"))

        val result = PageTransfer.apply(source, target, page.id, copy = true, nextId = ids::removeFirst)!!
        val copied = result.target.pages.last()

        assertEquals(source, result.source)
        assertEquals("copy-page", copied.id)
        val copiedSticky = copied.elements.single() as PageElement.Sticky
        assertEquals("copy-sticky", copiedSticky.id)
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

    @Test fun copiedPagesPreserveFreeformGeometryWithAFreshObjectId() {
        val path = listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, .3f), ShapeVertex(.4f, 1f))
        val page = InkPage(id = "page", elements = listOf(PageElement.Shape(id = "polygon", x = .1f, y = .2f, width = 300f, height = 240f, kind = ShapeKind.FREEFORM, path = path)))
        val source = InkDocument(id = "source", title = "Source", pages = listOf(page))
        val target = InkDocument(id = "target", title = "Target")
        val ids = ArrayDeque(listOf("copy-page", "copy-polygon"))

        val copied = PageTransfer.apply(source, target, page.id, copy = true, nextId = ids::removeFirst)!!.target.pages.last().elements.single() as PageElement.Shape

        assertEquals("copy-polygon", copied.id)
        assertEquals(ShapeKind.FREEFORM, copied.kind)
        assertEquals(path, copied.path)
    }

    @Test fun batchMoveAppendsInSourceOrderAndKeepsRemainingSourcePage() {
        val source = InkDocument(id = "source", title = "Source", pages = listOf(InkPage(id = "a"), InkPage(id = "b"), InkPage(id = "c")))
        val target = InkDocument(id = "target", title = "Target", pages = listOf(InkPage(id = "d")))
        val moved = PageTransfer.applyMany(source, target, linkedSetOf("c", "a"), copy = false)!!
        assertEquals(listOf("b"), moved.source.pages.map(InkPage::id))
        assertEquals(listOf("d", "a", "c"), moved.target.pages.map(InkPage::id))
        assertEquals(listOf("a", "c"), moved.transferredPageIds)
    }

    @Test fun batchCopyRemapsLinksBetweenCopiedPagesAndRejectsPartialSelection() {
        val a = InkPage(id = "a", pdfGotoLinks = listOf(PdfGotoLink(emptyList(), destinationPageId = "b")))
        val b = InkPage(id = "b")
        val source = InkDocument(id = "source", title = "Source", pages = listOf(a, b))
        val target = InkDocument(id = "target", title = "Target", pages = listOf(InkPage(id = "d")))
        val ids = ArrayDeque(listOf("copy-a", "copy-b"))
        val copied = PageTransfer.applyMany(source, target, setOf("a", "b"), copy = true, nextId = ids::removeFirst)!!
        assertEquals(source, copied.source)
        assertEquals(listOf("copy-a", "copy-b"), copied.transferredPageIds)
        assertEquals("copy-b", copied.target.pages[1].pdfGotoLinks.single().destinationPageId)
        assertNull(PageTransfer.applyMany(source, target, setOf("a", "missing"), copy = false))
    }
}
