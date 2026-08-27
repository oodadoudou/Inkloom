package app.inkloom.engine.v5.storage

import app.inkloom.document.JournalEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.CRC32

/** Ordered V5 recovery records. Older/raw records are intentionally rejected. */
internal object RecoveryJournalCodec {
    private const val FORMAT = "inkloom-journal-v5"
    private const val SCHEMA_VERSION = 5
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
        val record = json.decodeFromString<Record>(line)
        require(record.format == FORMAT) { "Unsupported recovery journal format ${record.format}" }
        require(record.schemaVersion == SCHEMA_VERSION) {
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
