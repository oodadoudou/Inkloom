package app.inkloom.engine.v5.storage

import app.inkloom.document.InkDocument
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.PageElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
        assertTrue(!encoded.contains("\"contentKind\""))
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

    @Test fun checkpointRejectsRemovedLayerCompatibilityFields() {
        val encoded = DocumentCheckpointCodec.encode(
            InkDocument(title = "legacy layers", pages = listOf(InkPage(layers = listOf(InkLayer(name = "Paint"))))),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject.toMutableMap()
        val document = root.getValue("document").jsonObject.toMutableMap()
        val pages = (document.getValue("pages") as JsonArray).toMutableList()
        val page = pages.first().jsonObject.toMutableMap()
        val layers = (page.getValue("layers") as JsonArray).toMutableList()
        val background = layers.first().jsonObject.toMutableMap()
        background["contentKind"] = JsonPrimitive("VECTOR")
        background["rasterAssetPath"] = JsonPrimitive("/legacy/full-page.png")
        background["mask"] = JsonObject(mapOf("regions" to JsonArray(emptyList())))
        layers[0] = JsonObject(background)
        page["layers"] = JsonArray(layers)
        pages[0] = JsonObject(page)
        document["pages"] = JsonArray(pages)
        root["document"] = JsonObject(document)
        val legacy = JsonObject(root).toString()

        val error = assertThrows(IllegalArgumentException::class.java) {
            DocumentCheckpointCodec.decode(legacy)
        }

        assertEquals(DocumentCheckpointCodec.LEGACY_ENGINE_ERROR, error.message)
    }
}
