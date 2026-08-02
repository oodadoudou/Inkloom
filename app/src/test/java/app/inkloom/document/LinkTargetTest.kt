package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTargetTest {
    @Test fun addsHttpsToAnOrdinaryWebAddress() {
        assertEquals("https://inkloom.app/guide?q=pen", LinkTarget.normalize("inkloom.app/guide?q=pen"))
    }

    @Test fun retainsSupportedExplicitTargets() {
        assertEquals("https://example.com/docs", LinkTarget.normalize("HTTPS://example.com/docs"))
        assertEquals("mailto:hello@example.com?subject=Inkloom", LinkTarget.normalize("mailto:hello@example.com?subject=Inkloom"))
        assertEquals("tel:+46 70 123 45 67", LinkTarget.normalize("tel:+46 70 123 45 67"))
    }

    @Test fun rejectsUnsafeOrMalformedTargets() {
        assertNull(LinkTarget.normalize("javascript:alert(1)"))
        assertNull(LinkTarget.normalize("intent://settings"))
        assertNull(LinkTarget.normalize("https://exa mple.com"))
        assertNull(LinkTarget.normalize("https://."))
        assertNull(LinkTarget.normalize("mailto:missing-address"))
        assertNull(LinkTarget.normalize("tel:call-me"))
    }

    @Test fun producesACompactDestinationForCardsAndExports() {
        assertEquals("example.com/notes", LinkTarget.displayDestination("https://example.com/notes"))
        assertEquals("example.c…", LinkTarget.displayDestination("https://example.com/notes", 10))
    }
}
