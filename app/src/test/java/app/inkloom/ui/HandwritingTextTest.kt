package app.inkloom.ui

import app.inkloom.document.PdfTextBounds
import app.inkloom.document.RecognizedTextBlock
import app.inkloom.document.RecognizedTextSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingTextTest {
    private fun block(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        RecognizedTextBlock(text = text, bounds = PdfTextBounds(left, top, right, bottom), source = RecognizedTextSource.IMAGE_OCR)

    @Test fun linesReadTopToBottomAndWordsLeftToRight() {
        val blocks = listOf(
            block("world", .5f, .12f, .8f, .2f),
            block("second line", .1f, .4f, .7f, .5f),
            block("Hello", .1f, .1f, .4f, .2f),
        )
        assertEquals("Hello world\nsecond line", HandwritingText.join(blocks))
        assertEquals("", HandwritingText.join(emptyList()))
    }

    @Test fun theTextObjectSitsOverTheInkWithATypeSizeFromItsLineHeight() {
        val element = HandwritingText.element("Hello\nworld", PdfTextBounds(.1f, .2f, .6f, .3f), 1600f, 2000f, "ink")
        assertEquals(.1f, element.x, 1e-6f); assertEquals(.2f, element.y, 1e-6f)
        assertEquals(800f, element.width, .01f); assertEquals(200f, element.height, .01f)
        assertEquals(62f, element.textSize, .01f)
        assertEquals("ink", element.layerId)
        val tiny = HandwritingText.element("a", PdfTextBounds(.1f, .1f, .11f, .105f), 1600f, 2000f, "ink")
        assertEquals(160f, tiny.width, .01f); assertEquals(20f, tiny.textSize, .01f)
    }

    @Test fun inkIsCroppedThickenedAndScaledToALegibleHeight() {
        val width = 400; val height = 300
        val coverage = IntArray(width * height)
        for (y in 100 until 260) coverage[y * width + 50] = 255
        val box = requireNotNull(HandwritingImage.inkBounds(coverage, width, height))
        assertEquals(HandwritingImage.Box(50, 100, 51, 260), box)
        assertEquals(null, HandwritingImage.inkBounds(IntArray(16), 4, 4))
        val dilated = HandwritingImage.dilate(coverage, width, height, 2)
        assertEquals(255, dilated[150 * width + 52]); assertEquals(0, dilated[150 * width + 53])
        val (pixels, outWidth, outHeight) = HandwritingImage.prepare(HandwritingImage.crop(coverage, width, box), box.width, box.height, 48)
        assertEquals(48 + 32, outHeight); assertEquals(1 + 32, outWidth)
        assertEquals(pixels.size, outWidth * outHeight)
        assertEquals("ink stays dark after scaling", true, (pixels[(16 + 24) * outWidth + 16] and 0xFF) < 128)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }
}
