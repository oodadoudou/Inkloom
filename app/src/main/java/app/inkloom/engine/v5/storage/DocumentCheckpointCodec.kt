package app.inkloom.engine.v5.storage

import app.inkloom.document.InkDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.CRC32

/** Exact, integrity-checked V5 document checkpoint. Legacy documents are intentionally rejected. */
internal object DocumentCheckpointCodec {
    private const val FORMAT = "inkloom-document-v5"
    private const val SCHEMA_VERSION = 5
    private const val ENGINE_VERSION = 5

    private val json = Json {
        prettyPrint = true
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
        val checksum: String,
        val document: InkDocument,
    )

    fun encode(document: InkDocument): String {
        val payload = canonicalJson.encodeToString(document)
        return json.encodeToString(Checkpoint(FORMAT, SCHEMA_VERSION, ENGINE_VERSION, crc32(payload), document))
    }

    fun decode(content: String): InkDocument {
        val checkpoint = json.decodeFromString<Checkpoint>(content)
        require(checkpoint.format == FORMAT) { "Unsupported document format ${checkpoint.format}" }
        require(checkpoint.schemaVersion == SCHEMA_VERSION) {
            "Unsupported document schema ${checkpoint.schemaVersion}"
        }
        require(checkpoint.engineVersion == ENGINE_VERSION) {
            "Unsupported engine version ${checkpoint.engineVersion}"
        }
        val payload = canonicalJson.encodeToString(checkpoint.document)
        require(checkpoint.checksum == crc32(payload)) { "Document checkpoint failed integrity verification" }
        return checkpoint.document
    }

    private fun crc32(content: String): String = CRC32().run {
        update(content.encodeToByteArray())
        value.toString(16).padStart(8, '0')
    }
}
