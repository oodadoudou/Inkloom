package app.inkloom.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class AbrDescriptorDecoderTest {
    private fun ostype(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)

    private fun unitFloat(value: Double): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(ostype("UntF"))
            d.write(ostype("#Pct"))
            d.writeDouble(value)
        }
        return out.toByteArray()
    }

    private fun descriptorWithSettings(spacing: Double, hardness: Double, roundness: Double, angle: Double, diameter: Double): ByteArray {
        val settings = ByteArrayOutputStream()
        DataOutputStream(settings).use { d ->
            d.write(ostype("bset")); d.writeInt(1); d.write(ostype("Blnk")); d.writeInt(5)
            d.write(ostype("Dmtr")); d.write(ostype("UntF")); d.write(ostype("#Pxl")); d.writeDouble(diameter)
            d.write(ostype("Spcn")); d.write(unitFloat(spacing))
            d.write(ostype("Hrdn")); d.write(unitFloat(hardness))
            d.write(ostype("Rndn")); d.write(unitFloat(roundness))
            d.write(ostype("Angl")); d.write(ostype("UntF")); d.write(ostype("#Ang")); d.writeDouble(angle)
        }
        // top-level descriptor: classId 'brush', version 1, keyType 'Blnk', 1 item, key 'Brsh' -> ObJC settings
        val top = ByteArrayOutputStream()
        DataOutputStream(top).use { d ->
            d.write(ostype("bset")); d.writeInt(1); d.write(ostype("Blnk")); d.writeInt(1)
            d.write(ostype("Brsh")); d.write(ostype("ObJC")); d.write(settings.toByteArray())
        }
        val file = ByteArrayOutputStream()
        DataOutputStream(file).use { d ->
            d.writeShort(6); d.writeShort(2)
            d.write(ostype("8BIM")); d.write(ostype("brsh")); d.writeInt(top.size() + 4)
            d.write(ostype("bset")); d.write(top.toByteArray())
        }
        return file.toByteArray()
    }

    @Test fun decodesBrushSettingsFromDescriptor() {
        val file = kotlin.io.path.createTempFile("inkloom-abr-brsh", ".abr").toFile()
        try {
            file.writeBytes(descriptorWithSettings(25.0, 90.0, 60.0, 30.0, 120.0))
            val settings = AbrDescriptorDecoder.decode(file)
            assertNotNull(settings)
            assertEquals(.25f, settings!!.spacingPercent!!, .001f)
            assertEquals(.90f, settings.hardnessPercent!!, .001f)
            assertEquals(.60f, settings.roundnessPercent!!, .001f)
            assertEquals(30f, settings.angleDegrees!!, .001f)
            assertEquals(120f, settings.diameterPixels!!, .001f)
        } finally {
            file.delete()
        }
    }

    @Test fun missingBrshBlockReturnsNull() {
        val file = kotlin.io.path.createTempFile("inkloom-abr-tiponly", ".abr").toFile()
        try {
            file.writeBytes(byteArrayOf(0, 6, 0, 2))
            assertNull(AbrDescriptorDecoder.decode(file))
        } finally {
            file.delete()
        }
    }

    @Test fun truncatedDescriptorReturnsNullSafely() {
        val bytes = descriptorWithSettings(25.0, 90.0, 60.0, 30.0, 120.0)
        val truncated = bytes.copyOf(bytes.size / 2)
        val file = kotlin.io.path.createTempFile("inkloom-abr-trunc", ".abr").toFile()
        try {
            file.writeBytes(truncated)
            assertNull(AbrDescriptorDecoder.decode(file))
        } finally {
            file.delete()
        }
    }
}
