package app.inkloom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.ImageAssetDecoder
import app.inkloom.document.ImageCrop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageAssetDecoderColorSpaceTest {
    @Test fun taggedDisplayP3ImageIsConvertedToTheCurrentSrgbDocumentSpace() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888, true, p3)
        val sample = Color.valueOf(.72f, .35f, .18f, 1f, p3)
        source.eraseColor(sample.pack())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("inkloom-p3-", ".png", context.cacheDir)
        try {
            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val tagged = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try { assertEquals(p3, tagged.colorSpace) } finally { tagged.recycle() }
            val decoded = requireNotNull(ImageAssetDecoder.decodeCropped(file.absolutePath, ImageCrop()))
            try {
                assertEquals(srgb, decoded.colorSpace)
                val actual = decoded.getPixel(2, 2)
                val expected = sample.convert(srgb).toArgb()
                assertTrue(kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= 3)
                assertTrue(kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= 3)
                assertTrue(kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= 3)
            } finally { decoded.recycle() }
        } finally {
            source.recycle()
            file.delete()
        }
    }
}
