package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class PageDropPositionTest {
    @Test fun differentlySizedPagesUseActualCenters() {
        val order = listOf("a", "b", "c")
        val centers = mapOf("a" to 45f, "b" to 360f, "c" to 710f)
        assertEquals(0, PageDropPosition.insertionIndex(order, setOf("c"), centers, 20f))
        assertEquals(1, PageDropPosition.insertionIndex(order, setOf("a"), centers, 370f))
        assertEquals(listOf("b", "a", "c"), PageBatchOperations.moveBlock(order, setOf("a"), PageDropPosition.insertionIndex(order, setOf("a"), centers, 370f)!!))
    }
    @Test fun selectedBlockIsExcludedAndClippedViewportKeepsAbsoluteOrder() {
        val order = listOf("a", "b", "c", "d", "e")
        assertEquals(2, PageDropPosition.insertionIndex(order, setOf("a", "c"), mapOf("d" to 100f, "e" to 300f), 110f))
        assertNull(PageDropPosition.insertionIndex(order, setOf("a"), emptyMap(), 20f))
        assertNull(PageDropPosition.insertionIndex(order, setOf("a"), mapOf("d" to 100f), Float.NaN))
    }
}
