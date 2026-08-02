package app.inkloom

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.AbrBrushParser
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AbrBrushParserTest {
    @Test fun exportsAbrOpacityInInkloomMaskOrientation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "abr-parser-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "mask.abr").apply { writeBytes(twoPixelAbr()) }
            val fallback = ImportedBrush(
                name = "fixture",
                format = BrushFormat.ABR,
                compatibility = Compatibility.BASIC,
                sizeBytes = source.length(),
                report = emptyList(),
            )
            val result = AbrBrushParser(File(root, "assets")).enrich(source, "fixture-hash", fallback)
            assertEquals(1, result.size)
            assertEquals(Compatibility.EXTRACTED, result.single().compatibility)
            val bitmap = BitmapFactory.decodeFile(result.single().tipPreviewPath)
            assertEquals(2, bitmap.width)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            assertEquals(Color.BLACK, bitmap.getPixel(1, 0))
            bitmap.recycle()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun twoPixelAbr(): ByteArray {
        val sample = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(ByteArray(37 + 264))
                out.writeInt(0); out.writeInt(0); out.writeInt(1); out.writeInt(2)
                out.writeShort(8); out.writeByte(0)
                out.write(byteArrayOf(0, -1))
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeShort(6); out.writeShort(2); out.writeBytes("8BIM"); out.writeBytes("samp")
                out.writeInt(4 + ((sample.size + 3) / 4) * 4)
                out.writeInt(sample.size); out.write(sample)
                repeat((4 - sample.size % 4) % 4) { out.writeByte(0) }
            }
        }.toByteArray()
    }
}
