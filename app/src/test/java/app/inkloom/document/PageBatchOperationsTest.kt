package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageBatchOperationsTest {
    @Test fun `removal preserves the current page and prefers the next surviving page`() {
        val pages = listOf("a", "b", "c", "d", "e")
        assertEquals("d", PageBatchOperations.pageAfterRemoval(pages, setOf("b"), "d"))
        assertEquals("d", PageBatchOperations.pageAfterRemoval(pages, setOf("e"), "d"))
        assertEquals("d", PageBatchOperations.pageAfterRemoval(pages, setOf("c"), "c"))
        assertEquals("d", PageBatchOperations.pageAfterRemoval(pages, setOf("b", "c"), "b"))
        assertEquals("c", PageBatchOperations.pageAfterRemoval(pages, setOf("d", "e"), "e"))
        assertEquals("b", PageBatchOperations.pageAfterRemoval(pages, setOf("a"), "a"))
    }

    @Test fun `selected pages move together without changing their relative order`() {
        assertEquals(
            listOf("a", "c", "e", "b", "d"),
            PageBatchOperations.moveBlock(listOf("a", "b", "c", "d", "e"), setOf("b", "d"), 3),
        )
    }

    @Test fun `empty and whole-document selections are stable`() {
        val pages = listOf("a", "b")
        assertEquals(pages, PageBatchOperations.moveBlock(pages, emptySet(), 1))
        assertEquals(pages, PageBatchOperations.moveBlock(pages, pages.toSet(), 0))
    }
}
