package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognizedTextGeometryTest {
    @Test
    fun `maps cropped image text into page coordinates`() {
        val page = InkPage(width = 1000f, height = 2000f)
        val image = PageElement.Image(
            x = .1f,
            y = .2f,
            width = 500f,
            height = 1000f,
            assetPath = "/tmp/image.png",
            crop = ImageCrop(.25f, .25f, .75f, .75f),
        )
        val block = RecognizedTextBlock(
            text = "Inkloom",
            bounds = PdfTextBounds(.25f, .25f, .5f, .5f),
            source = RecognizedTextSource.IMAGE_OCR,
        )

        val mapped = requireNotNull(RecognizedTextGeometry.mapImageBlock(page, image, block))

        assertEquals(.1f, mapped.bounds.left, .0001f)
        assertEquals(.2f, mapped.bounds.top, .0001f)
        assertEquals(.35f, mapped.bounds.right, .0001f)
        assertEquals(.45f, mapped.bounds.bottom, .0001f)
    }

    @Test
    fun `drops text outside the visible image crop`() {
        val page = InkPage()
        val image = PageElement.Image(
            x = 0f,
            y = 0f,
            width = 100f,
            height = 100f,
            assetPath = "/tmp/image.png",
            crop = ImageCrop(.5f, .5f, 1f, 1f),
        )
        val block = RecognizedTextBlock(
            text = "hidden",
            bounds = PdfTextBounds(0f, 0f, .4f, .4f),
            source = RecognizedTextSource.IMAGE_OCR,
        )

        assertNull(RecognizedTextGeometry.mapImageBlock(page, image, block))
    }
}
