@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.inkloom.engine.v5.storage

import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream

/**
 * Exact, integrity-checked V5 document checkpoint. Legacy documents are intentionally rejected.
 *
 * Checkpoints are compact JSON streamed to and from the file: a long PDF notebook can run to tens
 * of megabytes, and building it as one string (several times, for the checksum) took seconds and
 * could run the app out of memory. The checksum covers the canonical document bytes; older
 * pretty-printed checkpoints still read.
 *
 * `journalSequence` is the last recovery-journal entry the checkpoint already includes; replay
 * skips entries up to it, so a crash between writing the checkpoint and trimming the journal never
 * brings back an older page.
 */
internal object DocumentCheckpointCodec {
    const val LEGACY_ENGINE_ERROR = "This document was created by an unsupported legacy Inkloom engine."
    private const val FORMAT = "inkloom-document-v5"
    private const val SCHEMA_VERSION = 5
    private const val ENGINE_VERSION = 5
    /** Fields only legacy engines wrote; a checkpoint without any of them skips the structural scan. */
    private val DOCUMENT_KEY = "\"document\":".encodeToByteArray()
    private val CHECKSUM_KEY = ",\"checksum\":\"".encodeToByteArray()
    private val LEGACY_MARKERS = listOf("\"strokes\"", "\"contentKind\"", "\"rasterAssetPath\"", "\"regions\"")

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    private val canonicalJson = Json {
        classDiscriminator = "type"
    }

    @Serializable
    private data class Checkpoint(
        val format: String,
        val schemaVersion: Int,
        val engineVersion: Int,
        val journalSequence: Long = 0L,
        val checksum: String,
        val document: InkDocument,
    )

    /** A checkpoint read back: the notebook and the last journal entry it includes. */
    class Stored(val document: InkDocument, val journalSequence: Long)

    private fun header(journalSequence: Long = 0L) =
        """{"format":"$FORMAT","schemaVersion":$SCHEMA_VERSION,"engineVersion":$ENGINE_VERSION,""" +
            (if (journalSequence > 0L) "\"journalSequence\":$journalSequence," else "") + "\"document\":"

    /** Streams the checkpoint: header, canonical document (checksummed as it is written), checksum. */
    fun write(document: InkDocument, output: OutputStream, journalSequence: Long = 0L) {
        requireNativeV5Assets(document)
        output.write(header(journalSequence).encodeToByteArray())
        val crc = CRC32()
        canonicalJson.encodeToStream(InkDocument.serializer(), document, CheckedOutputStream(UnclosedStream(output), crc))
        output.write(""","checksum":"${crc.hex()}"}""".encodeToByteArray())
    }

    fun encode(document: InkDocument): String {
        requireNativeV5Assets(document)
        val payload = canonicalJson.encodeToString(InkDocument.serializer(), document)
        return header() + payload + ""","checksum":"${crc32(payload)}"}"""
    }

    /** Reads a checkpoint file without holding its text in memory, unless it may be a legacy one. */
    fun read(file: File): Stored {
        if (mayContainLegacyFields(file)) return decodeStored(file.readText())
        val checkpoint = file.inputStream().buffered(1 shl 16).use { json.decodeFromStream(Checkpoint.serializer(), it) }
        return Stored(verified(checkpoint) { file.readBytes() }, checkpoint.journalSequence)
    }

    fun decode(content: String): InkDocument = decodeStored(content).document

    private fun decodeStored(content: String): Stored {
        if (LEGACY_MARKERS.any(content::contains)) rejectLegacyStrokePayload(content)
        val checkpoint = json.decodeFromString<Checkpoint>(content)
        return Stored(verified(checkpoint) { content.encodeToByteArray() }, checkpoint.journalSequence)
    }

    /**
     * The checksum was computed over the document bytes as written. Re-encoding gives the same
     * bytes, and is checked first because it needs no second read; when the model has changed
     * since the file was written (a field added, removed or given a new default), only the stored
     * bytes still match, so those are checked instead.
     */
    private fun verified(checkpoint: Checkpoint, storedBytes: () -> ByteArray): InkDocument {
        require(checkpoint.format == FORMAT) { "Unsupported document format ${checkpoint.format}" }
        require(checkpoint.schemaVersion == SCHEMA_VERSION) {
            "Unsupported document schema ${checkpoint.schemaVersion}"
        }
        require(checkpoint.engineVersion == ENGINE_VERSION) {
            "Unsupported engine version ${checkpoint.engineVersion}"
        }
        requireNativeV5Assets(checkpoint.document)
        // Re-encode into the checksum only; no copy of the document text is kept.
        val crc = CRC32()
        canonicalJson.encodeToStream(InkDocument.serializer(), checkpoint.document, CheckedOutputStream(DiscardingStream, crc))
        require(checkpoint.checksum == crc.hex() || checkpoint.checksum == storedPayloadCrc(storedBytes())) {
            "Document checkpoint failed integrity verification"
        }
        return checkpoint.document
    }

    /** CRC of the bytes between the envelope's `"document":` and its closing `,"checksum":"`. */
    private fun storedPayloadCrc(bytes: ByteArray): String? {
        val start = indexOf(bytes, bytes.size, DOCUMENT_KEY).takeIf { it >= 0 }?.plus(DOCUMENT_KEY.size) ?: return null
        val end = lastIndexOf(bytes, CHECKSUM_KEY).takeIf { it > start } ?: return null
        return CRC32().run { update(bytes, start, end - start); hex() }
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (start in haystack.size - needle.size downTo 0) {
            for (index in needle.indices) if (haystack[start + index] != needle[index]) continue@outer
            return start
        }
        return -1
    }

    /** Scans the file in chunks for the legacy field names (overlapping so none is split). */
    private fun mayContainLegacyFields(file: File): Boolean {
        val needles = LEGACY_MARKERS.map(String::encodeToByteArray)
        val overlap = needles.maxOf { it.size } - 1
        val buffer = ByteArray((1 shl 16) + overlap)
        var carried = 0
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer, carried, buffer.size - carried)
                if (read <= 0) return false
                val length = carried + read
                if (needles.any { needle -> indexOf(buffer, length, needle) >= 0 }) return true
                carried = minOf(overlap, length)
                System.arraycopy(buffer, length - carried, buffer, 0, carried)
            }
        }
    }

    private fun indexOf(haystack: ByteArray, length: Int, needle: ByteArray): Int {
        outer@ for (start in 0..length - needle.size) {
            for (index in needle.indices) if (haystack[start + index] != needle[index]) continue@outer
            return start
        }
        return -1
    }

    private object DiscardingStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    /** The serializer may close what it writes to; the caller still owns [target]. */
    private class UnclosedStream(private val target: OutputStream) : OutputStream() {
        override fun write(b: Int) = target.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
        override fun flush() = target.flush()
        override fun close() = flush()
    }

    private fun CRC32.hex(): String = value.toString(16).padStart(8, '0')

    private fun requireNativeV5Assets(document: InkDocument) = document.pages.forEach(::requireNativeV5Page)

    /** The check a checkpoint applies to each page; the store runs it before journaling a page. */
    fun requireNativeV5Page(page: InkPage) {
        val nativeMasks = page.layers.all { layer ->
            layer.mask?.surface?.assetPath?.endsWith(".layer-mask.v5") != false
        } && page.elements.all { element ->
            when (element) {
                is app.inkloom.document.PageElement.Fill -> element.maskAssetPath.endsWith(".fill-mask.v5")
                is app.inkloom.document.PageElement.Shape ->
                    element.maskAssetPath?.endsWith(".fill-mask.v5") != false
                else -> true
            }
        }
        require(nativeMasks) { LEGACY_ENGINE_ERROR }
    }

    private fun rejectLegacyStrokePayload(content: String) {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return
        val document = root["document"] as? JsonObject ?: return
        val pages = document["pages"] as? JsonArray ?: return
        val containsLegacyProductionState = pages.any { pageElement ->
            val page = pageElement as? JsonObject ?: return@any false
            if (page.containsKey("strokes")) return@any true
            val layers = page["layers"] as? JsonArray ?: return@any false
            layers.any { layerElement ->
                val layer = layerElement as? JsonObject
                val mask = layer?.get("mask") as? JsonObject
                layer?.containsKey("contentKind") == true ||
                    layer?.containsKey("rasterAssetPath") == true ||
                    mask?.containsKey("regions") == true
            }
        }
        require(!containsLegacyProductionState) {
            LEGACY_ENGINE_ERROR
        }
    }

    private fun crc32(content: String): String = CRC32().run {
        update(content.encodeToByteArray())
        hex()
    }
}
