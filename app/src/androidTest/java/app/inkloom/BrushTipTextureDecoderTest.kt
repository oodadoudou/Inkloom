package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushTipTextureDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrushTipTextureDecoderTest {
    @Test
    fun blackRgbGrainUsesAlphaAsDensity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "brush-tip-texture-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tipFile = File(root, "tip.png")
            writePng(tipFile, intArrayOf(Color.BLACK, Color.BLACK))

            val grainFile = File(root, "grain.png")
            writePng(
                grainFile,
                intArrayOf(
                    Color.argb(0, 0, 0, 0),
                    Color.argb(255, 0, 0, 0),
                ),
            )

            val decoded = BrushTipTextureDecoder.decode(
                tipPath = tipFile.absolutePath,
                hardness = 1f,
                grainPath = grainFile.absolutePath,
                grainDensity = 1f,
            )

            assertNotNull(decoded)
            decoded!!
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
            assertEquals(255, Color.alpha(decoded.getPixel(1, 0)))
            decoded.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writePng(file: File, pixels: IntArray) {
        val bitmap = Bitmap.createBitmap(pixels, 2, 1, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
