package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    @Test fun `rotation retains authoritative sparse layer mask reference`() {
        val reference = MaskTileSurfaceRef("/tmp/mask.v5", "mask", 100, 200, 64, 1, 0, 1)
        val mask = LayerMask(baseVisible = false, surface = reference)
        val page = InkPage(layers = defaultInkLayers().map { if (it.id == LayerIds.INK) it.copy(mask = mask) else it })

        val rotated = requireNotNull(PageGeometry.rotateClockwise(page).layers.first { it.id == LayerIds.INK }.mask)

        assertEquals(reference, rotated.surface)
    }

    @Test fun clockwiseRotationSwapsPageAndTransformsContent() {
        val image = PageElement.Image(x = .1f, y = .2f, width = 300f, height = 400f, assetPath = "/tmp/image")
        val rotated = PageGeometry.rotateClockwise(InkPage(width = 1000f, height = 2000f, elements = listOf(image)))

        assertEquals(2000f, rotated.width)
        assertEquals(1000f, rotated.height)
        assertEquals(1, rotated.backgroundRotationQuarterTurns)
        val rotatedImage = rotated.elements.single() as PageElement.Image
        assertEquals(.6f, rotatedImage.x, .0001f)
        assertEquals(.1f, rotatedImage.y, .0001f)
        assertEquals(400f, rotatedImage.width)
        assertEquals(300f, rotatedImage.height)
        assertEquals(90f, rotatedImage.rotationDegrees)
    }

    @Test fun fourRotationsReturnToOriginalGeometry() {
        val original = InkPage(
            width = 900f,
            height = 1400f,
            elements = listOf(PageElement.Text(x = .14f, y = .22f, width = 320f, height = 90f, text = "Test")),
        )
        val restored = generateSequence(original, PageGeometry::rotateClockwise).drop(4).first()

        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(0, restored.backgroundRotationQuarterTurns)
        val originalText = original.elements.single() as PageElement.Text
        val restoredText = restored.elements.single() as PageElement.Text
        assertEquals(originalText.x, restoredText.x, .0001f)
        assertEquals(originalText.y, restoredText.y, .0001f)
        assertEquals(originalText.width, restoredText.width)
        assertEquals(originalText.height, restoredText.height)
    }

    @Test fun rotationKeepsPdfSearchHighlightAligned() {
        val page = InkPage(
            width = 1000f,
            height = 2000f,
            pdfTextBlocks = listOf(PdfTextBlock("Result", listOf(PdfTextBounds(.1f, .2f, .4f, .3f)))),
        )

        val rotated = PageGeometry.rotateClockwise(page).pdfTextBlocks.single().bounds.single()

        assertEquals(.7f, rotated.left, .0001f)
        assertEquals(.1f, rotated.top, .0001f)
        assertEquals(.8f, rotated.right, .0001f)
        assertEquals(.4f, rotated.bottom, .0001f)
    }

    @Test fun rotationKeepsPdfGotoLinkSourceAligned() {
        val page = InkPage(pdfGotoLinks = listOf(PdfGotoLink(listOf(PdfTextBounds(.1f, .2f, .4f, .3f)), "target")))

        val rotated = PageGeometry.rotateClockwise(page).pdfGotoLinks.single().bounds.single()

        assertEquals(.7f, rotated.left, .0001f)
        assertEquals(.1f, rotated.top, .0001f)
        assertEquals(.8f, rotated.right, .0001f)
        assertEquals(.4f, rotated.bottom, .0001f)
    }

    @Test fun rotationTracksPersistentRasterSurfaceOrientation() {
        val raster = InkLayer(name = "Raster", rasterTileSurface = RasterTileSurfaceRef("/tmp/surface.json", "surface", 400, 200, 128, 1))
        val original = InkPage(width = 400f, height = 200f, layers = defaultInkLayers() + raster)

        val once = PageGeometry.rotateClockwise(original)
        val restored = generateSequence(original, PageGeometry::rotateClockwise).drop(4).first()

        assertEquals(1, once.layers.last().rasterRotationQuarterTurns)
        assertEquals(0, restored.layers.last().rasterRotationQuarterTurns)
    }
}
