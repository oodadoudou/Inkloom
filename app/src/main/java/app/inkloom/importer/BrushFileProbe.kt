package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.InputStream

object BrushFileProbe {
    const val MAX_IMPORT_BYTES = 256L * 1024L * 1024L
    private val sqliteMagic = "SQLite format 3\u0000".encodeToByteArray()

    fun probe(fileName: String, sizeBytes: Long, input: InputStream): ImportedBrush {
        require(sizeBytes in 1..MAX_IMPORT_BYTES) { "Brush file is empty or exceeds the 256 MB safety limit" }
        val header = ByteArray(64)
        val read = input.read(header).coerceAtLeast(0)
        val data = header.copyOf(read)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val format = when {
            data.startsWith(sqliteMagic) && extension == "sut" -> BrushFormat.SUT
            isAbr(data) -> BrushFormat.ABR
            (extension == "brush" || extension == "brushset") && data.size >= 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte() -> BrushFormat.PROCREATE
            extension in setOf("png", "jpg", "jpeg", "webp") -> BrushFormat.IMAGE
            else -> BrushFormat.UNKNOWN
        }
        val compatibility = when (format) {
            BrushFormat.ABR -> Compatibility.BASIC
            BrushFormat.SUT, BrushFormat.PROCREATE -> Compatibility.BASIC
            BrushFormat.IMAGE -> Compatibility.COMPLETE
            BrushFormat.UNKNOWN -> Compatibility.FAILED
            BrushFormat.SYSTEM -> Compatibility.COMPLETE
        }
        val report = when (format) {
            BrushFormat.ABR -> listOf("ABR signature verified", "ABR 6.x sampled-tip extraction will be attempted", "Unsupported dynamics remain on safe Inkloom defaults")
            BrushFormat.SUT -> listOf("CSP SQLite container verified", "Source is eligible for parameter, material-reference and effector-curve decoding", "Unsupported CSP-only behavior is listed separately after conversion")
            BrushFormat.PROCREATE -> listOf("Procreate ZIP container recognized", "Shape, grain, NSKeyedArchive settings and Dual Brush payloads will be decoded", "Unverified rendering and wet-mix behavior will be reported explicitly")
            BrushFormat.IMAGE -> listOf("Raster tip recognized", "Alpha or luminance will define the tip", "Spacing and pressure use safe defaults")
            BrushFormat.UNKNOWN -> listOf("No supported file signature was found")
            BrushFormat.SYSTEM -> emptyList()
        }
        return ImportedBrush(
            name = fileName.substringBeforeLast('.').ifBlank { "Imported Brush" },
            format = format,
            compatibility = compatibility,
            sizeBytes = sizeBytes,
            report = report,
        )
    }

    private fun isAbr(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val version = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
        val marker = bytes.copyOfRange(4, 8).decodeToString()
        return version in 1..10 && marker == "8BIM"
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
