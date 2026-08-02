package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfGotoLinkNavigationTest {
    @Test fun hitChoosesTheSmallestOverlappingLink() {
        val large = link("large", PdfTextBounds(.1f, .1f, .5f, .5f))
        val small = link("small", PdfTextBounds(.2f, .2f, .3f, .3f))

        assertEquals("small", PdfGotoLinkNavigation.hit(listOf(large, small), .25f, .25f)?.destinationPageId)
        assertNull(PdfGotoLinkNavigation.hit(listOf(large, small), .8f, .8f))
    }

    @Test fun rotationAndPageIdRemappingKeepDestinationsStable() {
        val source = InkPage(id = "source", pdfGotoLinks = listOf(PdfGotoLink(listOf(PdfTextBounds(.1f, .2f, .3f, .4f)), "target", .2f, .7f)))
        val remapped = PdfGotoLinkNavigation.remapDestinations(listOf(source), mapOf("source" to "copy-source", "target" to "copy-target"))
        val rotated = PdfGotoLinkNavigation.rotateDestinationCoordinates(remapped, "copy-target").single().pdfGotoLinks.single()

        assertEquals("copy-target", rotated.destinationPageId)
        assertEquals(.3f, rotated.destinationX, .0001f)
        assertEquals(.2f, rotated.destinationY, .0001f)
    }

    @Test fun destinationBoundsAreBoundedAtPageEdges() {
        val bounds = PdfGotoLinkNavigation.destinationBounds(link("target", PdfTextBounds(.1f, .1f, .2f, .2f)).copy(destinationX = 0f, destinationY = 1f))

        assertEquals(0f, bounds.left, .0001f)
        assertEquals(1f, bounds.bottom, .0001f)
    }

    private fun link(destination: String, bounds: PdfTextBounds) = PdfGotoLink(listOf(bounds), destination)
}
