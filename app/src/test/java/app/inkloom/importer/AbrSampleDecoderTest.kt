package app.inkloom.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class AbrSampleDecoderTest {
    @Test fun decodesRawAndPackBitsSamples() {
        val raw = sample(2, 2, false, byteArrayOf(1, 2, 3, 4), "raw-sample-id")
        val rlePayload = byteArrayOf(
            0, 3, 0, 2, // encoded byte count for each row
            1, 5, 6, // literal run: two bytes
            -1, 7, // repeat run: two bytes
        )
        val rle = sample(2, 2, true, rlePayload)
        val file = kotlin.io.path.createTempFile("inkloom-abr", ".abr").toFile()
        try {
            file.writeBytes(abr(raw, rle))
            val decoded = AbrSampleDecoder.decode(file)
            assertEquals(2, decoded.size)
            assertEquals("raw-sample-id".padEnd(36, '0'), decoded[0].id)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), decoded[0].opacity)
            assertArrayEquals(byteArrayOf(5, 6, 7, 7), decoded[1].opacity)
            assertTrue(decoded[1].compressed)
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsOversizedOrTruncatedSamples() {
        val file = kotlin.io.path.createTempFile("inkloom-bad-abr", ".abr").toFile()
        try {
            file.writeBytes(abr(sample(2, 2, false, byteArrayOf(1))))
            assertTrue(runCatching { AbrSampleDecoder.decode(file) }.isFailure)
        } finally {
            file.delete()
        }
    }

    @Test fun decodesAuthorizedRealAbrSamplesWhenConfigured() {
        val root = System.getProperty("brush.samples.dir")?.let(::File)
        assumeTrue(root != null && root.isDirectory)
        val files = root!!.walkTopDown().filter { it.isFile && it.extension.equals("abr", true) }.toList()
        assumeTrue(files.isNotEmpty())
        files.forEach { file ->
            val samples = AbrSampleDecoder.decode(file)
            assertTrue("${file.name} should contain tips", samples.isNotEmpty())
            assertTrue("${file.name} should contain non-empty masks", samples.all { sample -> sample.opacity.any { (it.toInt() and 0xff) > 0 } })
        }
    }

    private fun abr(vararg samples: ByteArray): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeShort(6)
            out.writeShort(2)
            out.writeBytes("8BIM")
            out.writeBytes("samp")
            val sectionSize = samples.sumOf { 4 + ((it.size + 3) / 4) * 4 }
            out.writeInt(sectionSize)
            samples.forEach { sample ->
                out.writeInt(sample.size)
                out.write(sample)
                repeat((4 - sample.size % 4) % 4) { out.writeByte(0) }
            }
        }
    }.toByteArray()

    private fun sample(width: Int, height: Int, compressed: Boolean, payload: ByteArray, id: String = "fixture-sample-id"): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(("$" + id.padEnd(36, '0').take(36)).toByteArray(Charsets.ISO_8859_1))
                out.write(ByteArray(264))
                out.writeInt(0)
                out.writeInt(0)
                out.writeInt(height)
                out.writeInt(width)
                out.writeShort(8)
                out.writeByte(if (compressed) 1 else 0)
                out.write(payload)
            }
        }.toByteArray()
}
