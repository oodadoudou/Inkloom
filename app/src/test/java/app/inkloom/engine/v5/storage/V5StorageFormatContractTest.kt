package app.inkloom.engine.v5.storage

import app.inkloom.document.InkDocument
import app.inkloom.document.PageElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class V5StorageFormatContractTest {
    @Test fun documentEnvelopeUsesExactV5Identity() {
        val document = InkDocument(title = "V5")

        val encoded = DocumentCheckpointCodec.encode(document)

        assertTrue(encoded.contains("\"format\": \"inkloom-document-v5\""))
        assertTrue(encoded.contains("\"schemaVersion\": 5"))
        assertTrue(encoded.contains("\"engineVersion\": 5"))
        assertEquals(document, DocumentCheckpointCodec.decode(encoded))
    }

    @Test fun barePreV5DocumentIsRejectedRatherThanMigrated() {
        val legacy = Json.encodeToString(InkDocument(title = "legacy"))

        assertThrows(Exception::class.java) { DocumentCheckpointCodec.decode(legacy) }
    }

    @Test fun journalUsesExactV5IdentityAndRejectsV3Envelope() {
        val document = InkDocument(title = "V5")
        val entry = RecoveryJournalEntry.ElementUpserted(
            documentId = document.id,
            pageId = document.pages.single().id,
            element = PageElement.Text(x = 0f, y = 0f, text = "V5 object"),
        )
        val encoded = RecoveryJournalCodec.encode(sequence = 1L, entry = entry)

        assertTrue(encoded.contains("\"format\":\"inkloom-journal-v5\""))
        assertEquals(entry, RecoveryJournalCodec.decode(encoded).entry)
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryJournalCodec.decode(encoded.replace("inkloom-journal-v5", "inkloom-v3-journal"))
        }
    }

    @Test fun checkpointRejectsLegacyVectorStrokePayloads() {
        val encoded = DocumentCheckpointCodec.encode(InkDocument(title = "legacy vector"))
        val legacy = encoded.replaceFirst("\"pages\": [\n            {", "\"pages\": [\n            {\n                \"strokes\": [],")

        val error = assertThrows(IllegalArgumentException::class.java) {
            DocumentCheckpointCodec.decode(legacy)
        }
        assertEquals(DocumentCheckpointCodec.LEGACY_ENGINE_ERROR, error.message)
    }
}
