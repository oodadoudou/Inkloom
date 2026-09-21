package app.inkloom.ui

import app.inkloom.document.InkPage
import app.inkloom.document.PageCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasRasterResolutionTest {
    @Test fun defaultPaperKeepsItsAuthored1600PixelWidth() {
        assertEquals(1600, CanvasRasterResolution.forPage(InkPage()))
    }

    @Test fun smallImportedPageGetsBaselineDrawingDensity() {
        assertEquals(1600, CanvasRasterResolution.forDimensions(612f, 792f))
    }

    @Test fun authoredPageAboveBaselineIsNotDownsampledWhenInsideBudget() {
        assertEquals(2400, CanvasRasterResolution.forDimensions(2400f, 1800f))
    }

    @Test fun largeAndTallPagesStayInsideCompositorDimensionAndPixelBudgets() {
        listOf(20_000f to 20_000f, 10_000f to 20_000f, 20_000f to 10_000f).forEach { (width, height) ->
            val chosenWidth = CanvasRasterResolution.forDimensions(width, height)
            val chosenHeight = (chosenWidth * height / width).toInt().coerceAtLeast(1)
            assertTrue(chosenWidth <= CanvasRasterResolution.MAXIMUM_DIMENSION)
            assertTrue(chosenHeight <= CanvasRasterResolution.MAXIMUM_DIMENSION)
            assertTrue(chosenWidth.toLong() * chosenHeight <= CanvasRasterResolution.MAXIMUM_PIXELS)
        }
    }

    @Test fun cropUsesVisiblePaperAspectAndResolution() {
        val cropped = InkPage(width = 3200f, height = 2400f, crop = PageCrop(.25f, 0f, .75f, 1f))
        assertEquals(1600, CanvasRasterResolution.forPage(cropped))
    }
}
