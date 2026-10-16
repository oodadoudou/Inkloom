package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageNumberLookupTest {
    private val pages = listOf(InkPage(id = "cover", customNumber = "i"), InkPage(id = "one"), InkPage(id = "two", customNumber = "A-2"),
        InkPage(id = "three"), InkPage(id = "four", customNumber = "2"))

    @Test fun wholeNumbersSelectPagesByPosition() {
        assertEquals("cover", PageNumberLookup.resolve(pages, "1"))
        assertEquals("two", PageNumberLookup.resolve(pages, " 3 "))
        assertEquals("three", PageNumberLookup.resolve(pages, "4"))
        assertEquals("four", PageNumberLookup.resolve(pages, "5"))
    }

    @Test fun customNumbersWinOverPositionsAndIgnoreCase() {
        assertEquals("four", PageNumberLookup.resolve(pages, "2"))
        assertEquals("two", PageNumberLookup.resolve(pages, " a-2"))
        assertEquals("cover", PageNumberLookup.resolve(pages, "I"))
    }

    @Test fun outOfRangeOrUnknownInputFindsNothing() {
        assertNull(PageNumberLookup.resolve(pages, "0"))
        assertNull(PageNumberLookup.resolve(pages, "6"))
        assertNull(PageNumberLookup.resolve(pages, "-1"))
        assertNull(PageNumberLookup.resolve(pages, ""))
        assertNull(PageNumberLookup.resolve(pages, "chapter"))
        assertNull(PageNumberLookup.resolve(pages, "99999999999999999999"))
    }

    @Test fun labelsMatchThePagesPanel() {
        assertEquals("i", PageNumberLookup.label(pages, 0))
        assertEquals("2", PageNumberLookup.label(pages, 1))
        assertEquals("A-2", PageNumberLookup.label(pages, 2))
    }
}
