package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PngColorMetadataTest {
    @Test fun exportedSrgbBitmapDeclaresItsColorSpace() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(30, 80, 160))
            val png = ByteArrayOutputStream().also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
            var offset = 8 // PNG signature
            val chunks = mutableListOf<String>()
            while (offset + 12 <= png.size) {
                val length = (0..3).fold(0) { size, index -> (size shl 8) or (png[offset + index].toInt() and 0xff) }
                if (length < 0 || offset + length + 12 > png.size) break
                val type = String(png, offset + 4, 4, Charsets.US_ASCII)
                chunks += type
                if (type == "IDAT") break
                offset += length + 12
            }
            assertTrue("PNG export lacks an embedded sRGB declaration: $chunks", "sRGB" in chunks || "iCCP" in chunks)
        } finally {
            bitmap.recycle()
        }
    }
}
