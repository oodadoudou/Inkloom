package app.inkloom.document

import app.inkloom.engine.v5.surface.LinearRgba
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasOrientationTest {
    /** 300 px is not a multiple of the 128 px tile, so mirrored pixels cross tile boundaries. */
    private fun surface(format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888) =
        PixelTileSurface(300, 200, pixelFormat = format).apply {
            beginWrite("fixture").use { write ->
                if (format.isHighPrecision) {
                    write.setLinearPixel(10, 20, LinearRgba(.25f, .5f, 1.5f, .5f))
                    write.setLinearPixel(299, 199, LinearRgba(1f, 0f, 0f, 1f))
                } else {
                    write.setPixel(10, 20, 0xFF112233.toInt())
                    write.setPixel(299, 199, 0x80400000.toInt())
                }
                write.commit().close()
            }
        }.snapshot()

    @Test fun horizontalFlipMirrorsEveryPixelLosslessly() {
        val flipped = CanvasOrientation.flippedPixels(surface(), CanvasFlip.HORIZONTAL).snapshot()
        assertEquals(0xFF112233.toInt(), flipped.pixelAt(289, 20))
        assertEquals(0x80400000.toInt(), flipped.pixelAt(0, 199))
        assertEquals(0, flipped.pixelAt(10, 20))
        val back = CanvasOrientation.flippedPixels(flipped, CanvasFlip.HORIZONTAL).snapshot()
        assertEquals(0xFF112233.toInt(), back.pixelAt(10, 20))
        assertEquals(0x80400000.toInt(), back.pixelAt(299, 199))
    }

    @Test fun verticalFlipMirrorsRowsForHighPrecisionPixels() {
        val flipped = CanvasOrientation.flippedPixels(surface(PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16), CanvasFlip.VERTICAL).snapshot()
        val moved = flipped.linearPixelAt(10, 179)
        assertEquals(.5f, moved.alpha, .002f)
        assertTrue("Extended range is kept", moved.blue > .7f)
        assertEquals(1f, flipped.linearPixelAt(299, 0).alpha, .002f)
        assertEquals(0f, flipped.linearPixelAt(10, 20).alpha, 0f)
    }

    @Test fun masksKeepTheirDefaultAlphaAndMirror() {
        val mask = MaskTileSurface(300, 200, initialDefaultAlpha = 255).apply {
            beginWrite("fixture").use { write -> write.setAlpha(5, 6, 0); write.commit().close() }
        }.snapshot()
        val flipped = CanvasOrientation.flippedMask(mask, CanvasFlip.HORIZONTAL)
        assertEquals(255, flipped.defaultAlpha)
        assertEquals(0, flipped.alphaAt(294, 6))
        assertEquals(255, flipped.alphaAt(5, 6))
    }

    @Test fun rotatedContentFlipsTheOtherStoredAxis() {
        assertEquals(CanvasFlip.HORIZONTAL, CanvasOrientation.storedAxis(CanvasFlip.HORIZONTAL, 0))
        assertEquals(CanvasFlip.HORIZONTAL, CanvasOrientation.storedAxis(CanvasFlip.HORIZONTAL, 2))
        assertEquals(CanvasFlip.VERTICAL, CanvasOrientation.storedAxis(CanvasFlip.HORIZONTAL, 1))
        assertEquals(CanvasFlip.HORIZONTAL, CanvasOrientation.storedAxis(CanvasFlip.VERTICAL, 3))
    }

    @Test fun cropsBoundsAndFramesMirror() {
        fun same(expected: PageCrop, actual: PageCrop) = listOf(expected.left to actual.left, expected.top to actual.top,
            expected.right to actual.right, expected.bottom to actual.bottom).forEach { (e, a) -> assertEquals(e, a, 1e-5f) }
        same(PageCrop(.6f, .2f, .9f, .8f), CanvasOrientation.flippedCrop(PageCrop(.1f, .2f, .4f, .8f), CanvasFlip.HORIZONTAL))
        same(PageCrop(.1f, .2f, .4f, .9f), CanvasOrientation.flippedCrop(PageCrop(.1f, .1f, .4f, .8f), CanvasFlip.VERTICAL))
        val bounds = CanvasOrientation.flippedBounds(PdfTextBounds(.1f, .2f, .3f, .4f), CanvasFlip.VERTICAL)
        assertEquals(.6f, bounds.top, 1e-5f); assertEquals(.8f, bounds.bottom, 1e-5f)
        assertEquals(PageBackgroundFrame(700f, 0f, 200f, 100f),
            CanvasOrientation.flippedFrame(PageBackgroundFrame(100f, 0f, 200f, 100f), 1000f, 500f, CanvasFlip.HORIZONTAL))
    }

    @Test fun objectsMirrorAboutThePageCentre() {
        val image = PageElement.Image(x = .1f, y = .2f, width = 100f, height = 50f, assetPath = "/tmp/image")
        val flipped = CanvasOrientation.flippedElement(image, 1000f, 500f, CanvasFlip.HORIZONTAL)
        // The left edge goes to the right: the content's first column now sits at 900 px.
        val origin = flipped.contentPoint(0f, 0f, 1000f, 500f)
        assertEquals(900f, origin.x, .5f)
        assertEquals(100f, origin.y, .5f)
        // The box itself is where the content is drawn, so it can still be tapped and selected.
        assertEquals(.8f, flipped.x, 1e-5f)
        val rotated = CanvasOrientation.flippedElement(image.copy(rotationDegrees = 30f), 1000f, 500f, CanvasFlip.HORIZONTAL)
        assertEquals(330f, rotated.rotationDegrees, 1e-3f)
        // Flipping twice restores the drawing exactly.
        val twice = CanvasOrientation.flippedElement(flipped, 1000f, 500f, CanvasFlip.HORIZONTAL)
        listOf(0f to 0f, 1f to 0f, .3f to .7f).forEach { (u, v) ->
            val a = image.contentPoint(u, v, 1000f, 500f); val b = twice.contentPoint(u, v, 1000f, 500f)
            assertEquals(a.x, b.x, .01f); assertEquals(a.y, b.y, .01f)
        }
    }

    @Test fun rotationQuarterTurnsCompose() {
        val page = InkPage(width = 1000f, height = 2000f)
        val counter = CanvasOrientation.rotated(page, CanvasOrientationChange.ROTATE_COUNTERCLOCKWISE.clockwiseQuarterTurns)
        assertEquals(2000f, counter.width); assertEquals(1000f, counter.height)
        assertEquals(3, counter.backgroundRotationQuarterTurns)
        assertEquals(page, CanvasOrientation.rotated(CanvasOrientation.rotated(page, 2), 2))
    }

    @Test fun importedBackgroundsCannotBeFlipped() {
        assertTrue(CanvasOrientation.canFlip(InkPage()))
        assertFalse(CanvasOrientation.canFlip(InkPage(background = PageBackground.Pdf("/tmp/a.pdf", 0))))
    }
}
