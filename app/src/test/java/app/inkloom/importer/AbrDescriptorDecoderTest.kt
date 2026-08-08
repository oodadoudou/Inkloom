package app.inkloom.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class AbrDescriptorDecoderTest {
    @Test fun decodesSettingsFromARealActionDescriptorLayout() {
        val file = kotlin.io.path.createTempFile("inkloom-abr-desc", ".abr").toFile()
        try {
            file.writeBytes(abrDescriptorFixture())
            val settings = AbrDescriptorDecoder.decode(file)
            assertNotNull(settings)
            assertEquals("fixture-sample-id", settings!!.sampleId)
            assertEquals("Fixture Brush", settings.name)
            assertEquals(.25f, settings.spacingPercent!!, .001f)
            assertEquals(.90f, settings.hardnessPercent!!, .001f)
            assertEquals(.60f, settings.roundnessPercent!!, .001f)
            assertEquals(30f, settings.angleDegrees!!, .001f)
            assertEquals(120f, settings.diameterPixels!!, .001f)
        } finally {
            file.delete()
        }
    }

    @Test fun malformedDescriptorFailsClosed() {
        val valid = abrDescriptorFixture()
        val file = kotlin.io.path.createTempFile("inkloom-abr-trunc", ".abr").toFile()
        try {
            file.writeBytes(valid.copyOf(valid.size / 2))
            assertTrue(AbrDescriptorDecoder.decodeAll(file).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test fun decodesAuthorizedRealAbrDescriptorsWhenConfigured() {
        val root = System.getProperty("brush.samples.dir")?.let(::File)
        assumeTrue(root != null && root.isDirectory)
        val files = root!!.walkTopDown().filter { it.isFile && it.extension.equals("abr", true) }.toList()
        assumeTrue(files.isNotEmpty())
        files.forEach { file ->
            val settings = AbrDescriptorDecoder.decodeAllStrict(file)
            assertTrue("${file.name} should contain brush descriptors", settings.isNotEmpty())
            assertTrue("${file.name} should expose real geometry", settings.any { it.diameterPixels != null && it.spacingPercent != null })
        }
    }

    private fun abrDescriptorFixture(): ByteArray {
        val brush = descriptor("sampledBrush", listOf(
            entry("Dmtr", "UntF", unit("#Pxl", 120.0)),
            entry("Spcn", "UntF", unit("#Prc", 25.0)),
            entry("Hrdn", "UntF", unit("#Prc", 90.0)),
            entry("Rndn", "UntF", unit("#Prc", 60.0)),
            entry("Angl", "UntF", unit("#Ang", 30.0)),
            entry("sampledData", "TEXT", unicode("fixture-sample-id")),
        ))
        val preset = descriptor("brushPreset", listOf(
            entry("Nm  ", "TEXT", unicode("Fixture Brush")),
            entry("Brsh", "Objc", brush),
        ))
        val list = bytes { out -> out.writeInt(1); out.writeBytes("Objc"); out.write(preset) }
        val top = descriptor("null", listOf(entry("Brsh", "VlLs", list)))
        val payload = bytes { out -> out.writeInt(16); out.write(top) }
        return bytes { out ->
            out.writeShort(6); out.writeShort(2)
            out.writeBytes("8BIM"); out.writeBytes("desc"); out.writeInt(payload.size); out.write(payload)
        }
    }

    private fun descriptor(classId: String, entries: List<ByteArray>): ByteArray = bytes { out ->
        out.write(unicode("")); out.write(id(classId)); out.writeInt(entries.size); entries.forEach(out::write)
    }

    private fun entry(key: String, type: String, value: ByteArray): ByteArray = bytes { out ->
        out.write(id(key)); out.writeBytes(type); out.write(value)
    }

    private fun id(value: String): ByteArray = bytes { out ->
        if (value.length == 4) { out.writeInt(0); out.writeBytes(value) }
        else { out.writeInt(value.length); out.writeBytes(value) }
    }

    private fun unicode(value: String): ByteArray {
        val terminated = "$value\u0000"
        return bytes { out -> out.writeInt(terminated.length); out.write(terminated.toByteArray(Charsets.UTF_16BE)) }
    }

    private fun unit(unit: String, value: Double): ByteArray = bytes { out -> out.writeBytes(unit); out.writeDouble(value) }

    private fun bytes(block: (DataOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use(block)
    }.toByteArray()
}
