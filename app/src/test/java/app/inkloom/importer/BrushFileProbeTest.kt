package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class BrushFileProbeTest {
    @Test fun recognizesAbrSignature() {
        val bytes = byteArrayOf(0, 6, 0, 2) + "8BIMsamp".encodeToByteArray()
        val result = BrushFileProbe.probe("sample.abr", bytes.size.toLong(), ByteArrayInputStream(bytes))
        assertEquals(BrushFormat.ABR, result.format)
        assertEquals(Compatibility.BASIC, result.compatibility)
    }

    @Test fun procreateRequiresZipSignature() {
        val valid = BrushFileProbe.probe("sample.brushset", 8, ByteArrayInputStream(byteArrayOf(0x50, 0x4b, 3, 4, 0, 0, 0, 0)))
        val invalid = BrushFileProbe.probe("sample.brushset", 8, ByteArrayInputStream(ByteArray(8)))
        assertEquals(BrushFormat.PROCREATE, valid.format)
        assertEquals(BrushFormat.UNKNOWN, invalid.format)
    }

    @Test fun recognizesSutSqliteContainer() {
        val bytes = "SQLite format 3\u0000".encodeToByteArray() + ByteArray(48)
        val result = BrushFileProbe.probe("sample.sut", bytes.size.toLong(), ByteArrayInputStream(bytes))
        assertEquals(BrushFormat.SUT, result.format)
        assertEquals(Compatibility.BASIC, result.compatibility)
    }

    @Test fun rejectsOversizedInput() {
        val failure = runCatching { BrushFileProbe.probe("huge.abr", BrushFileProbe.MAX_IMPORT_BYTES + 1, ByteArrayInputStream(byteArrayOf(1))) }
        assertTrue(failure.isFailure)
    }

    @Test fun probesAuthorizedRealSamplesWhenConfigured() {
        val root = System.getProperty("brush.samples.dir")?.let(::File)
        assumeTrue(root != null && root.isDirectory)
        val samples = root!!.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("abr", "sut") }.toList()
        assertTrue("Expected at least one ABR or SUT fixture", samples.isNotEmpty())
        samples.forEach { file ->
            val result = file.inputStream().use { BrushFileProbe.probe(file.name, file.length(), it) }
            assertTrue("${file.name} should be recognized", result.format == BrushFormat.ABR || result.format == BrushFormat.SUT)
        }
    }
}
