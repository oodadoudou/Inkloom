package app.inkloom.engine.v3.storage

import app.inkloom.document.JournalEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.CRC32

/** Ordered V3 recovery records. The exact serialized payload is checksummed before decoding. */
internal object RecoveryJournalCodec {
    private const val FORMAT = "inkloom-v3-journal"
    private const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    @Serializable
    private data class Record(
        val format: String,
        val schemaVersion: Int,
        val sequence: Long,
        val checksum: String,
        val payload: String,
    )

    data class Decoded(val entry: JournalEntry, val sequence: Long?)

    fun encode(sequence: Long, entry: JournalEntry): String {
        require(sequence > 0L)
        val payload = json.encodeToString<JournalEntry>(entry)
        return json.encodeToString(Record(FORMAT, SCHEMA_VERSION, sequence, crc32(payload), payload))
    }

    fun decode(line: String): Decoded {
        val root = json.parseToJsonElement(line).jsonObject
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT) {
            return Decoded(json.decodeFromString<JournalEntry>(line), null)
        }
        val record = json.decodeFromString<Record>(line)
        require(record.schemaVersion in 1..SCHEMA_VERSION) {
            "Unsupported recovery journal schema ${record.schemaVersion}"
        }
        require(record.sequence > 0L) { "Recovery journal sequence must be positive" }
        require(record.checksum == crc32(record.payload)) { "Recovery journal checksum mismatch" }
        return Decoded(json.decodeFromString<JournalEntry>(record.payload), record.sequence)
    }

    private fun crc32(content: String): String = CRC32().run {
        update(content.encodeToByteArray())
        value.toString(16).padStart(8, '0')
    }
}
