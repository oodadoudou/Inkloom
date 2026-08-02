package app.inkloom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.importer.SutMaterialThumbnailNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class SutMaterialThumbnailNormalizerTest {
    @Test fun transparentPreviewPaddingDoesNotBecomePartOfTheBrushTip() {
        val source = Bitmap.createBitmap(8, 7, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.TRANSPARENT)
        for (y in 2..4) for (x in 3..6) source.setPixel(x, y, Color.argb(200, 24, 24, 24))

        val result = SutMaterialThumbnailNormalizer.normalize(source.pngBytes())
        val decoded = BitmapFactory.decodeByteArray(result.png, 0, result.png.size)

        assertTrue(result.trimmed)
        assertEquals(4, result.width)
        assertEquals(3, result.height)
        assertEquals(4, decoded.width)
        assertEquals(3, decoded.height)
        assertEquals(200, Color.alpha(decoded.getPixel(0, 0)))
        decoded.recycle()
        source.recycle()
    }

    @Test fun fullyOccupiedPreviewKeepsItsOriginalGeometry() {
        val source = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        val original = source.pngBytes()

        val result = SutMaterialThumbnailNormalizer.normalize(original)

        assertFalse(result.trimmed)
        assertEquals(5, result.width)
        assertEquals(4, result.height)
        assertTrue(result.png.contentEquals(original))
        source.recycle()
    }

    private fun Bitmap.pngBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        check(compress(Bitmap.CompressFormat.PNG, 100, output))
        return output.toByteArray()
    }
}
