package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCropTest {
    @Test fun clampsUnsafeCropAndKeepsMinimumSpan() {
        val crop = ImageCrop(left = .99f, top = -.5f, right = .4f, bottom = 2f).normalized()
        assertEquals(.35f, crop.left, .001f)
        assertEquals(0f, crop.top, .001f)
        assertEquals(.4f, crop.right, .001f)
        assertEquals(1f, crop.bottom, .001f)
    }

    @Test fun convertsNormalizedCropToSafePixelBounds() {
        assertEquals(PixelBounds(100, 100, 800, 300), ImageCrop(.1f, .25f, .9f, 1f).pixelBounds(1000, 400))
        assertEquals(PixelBounds(0, 0, 1, 1), ImageCrop().pixelBounds(1, 1))
    }
}
