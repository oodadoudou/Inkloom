package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageViewportStoreTest {
    @Test fun eachPageRestoresItsOwnIndependentNavigation() {
        val store = PageViewportStore()
        val first = ViewportState(2.4f, 320f, -180f, 12f)
        val second = ViewportState(1.3f, -90f, 40f, -8f)

        store.save("first", first)
        store.save("second", second)

        assertEquals(first, store.restore("first"))
        assertEquals(second, store.restore("second"))
        assertEquals(PageViewportStore.DEFAULT, store.restore("new-page"))
    }

    @Test fun deletedPagesAndInvalidValuesCannotLeakIntoFuturePages() {
        val store = PageViewportStore()
        store.save("deleted", ViewportState(Float.NaN, Float.POSITIVE_INFINITY, 20f, Float.NaN))
        assertEquals(ViewportState(1f, 0f, 20f, 0f), store.restore("deleted"))

        store.retainPages(setOf("kept"))
        assertEquals(PageViewportStore.DEFAULT, store.restore("deleted"))
    }
}
