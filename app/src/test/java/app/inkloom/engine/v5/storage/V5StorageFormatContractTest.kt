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

        assertTrue(encoded.contains("\"format\":\"inkloom-document-v5\""))
        assertTrue(encoded.contains("\"schemaVersion\":5"))
        assertTrue(encoded.contains("\"engineVersion\":5"))
        assertTrue(!encoded.contains("\"contentKind\""))
        assertEquals(document, DocumentCheckpointCodec.decode(encoded))
    }

    @Test fun layerDrawingAssistRoundTripsWithoutChangingLegacyDefault() {
        val document = InkDocument(title = "Assist", pages = listOf(InkPage(layers = listOf(InkLayer(name = "Assisted", drawingAssist = true)))))
        val restored = DocumentCheckpointCodec.decode(DocumentCheckpointCodec.encode(document))
        assertTrue(restored.pages.single().layers.single().drawingAssist)
        assertTrue(!InkLayer(name = "Legacy default").drawingAssist)
    }

    /** A notebook written before a field was removed or renamed still opens: its stored bytes match. */
    @Test fun checkpointWithAFieldThisVersionDoesNotKnowStillVerifies() {
        val document = InkDocument(title = "Older field")
        val encoded = DocumentCheckpointCodec.encode(document)
        val start = encoded.indexOf("\"document\":") + "\"document\":".length
        val end = encoded.lastIndexOf(",\"checksum\":\"")
        val payload = "{\"retiredField\":7," + encoded.substring(start + 1, end)
        val crc = java.util.zip.CRC32().apply { update(payload.encodeToByteArray()) }.value.toString(16).padStart(8, '0')
        val written = encoded.substring(0, start) + payload + ",\"checksum\":\"$crc\"}"
        val file = java.io.File.createTempFile("checkpoint", ".json").apply { writeText(written) }

        assertEquals(document, DocumentCheckpointCodec.decode(written))
        assertEquals(document, DocumentCheckpointCodec.read(file).document)
        file.delete()
    }

    @Test fun checkpointWhoseBytesWereChangedIsStillRejected() {
        val encoded = DocumentCheckpointCodec.encode(InkDocument(title = "Original"))
        val tampered = encoded.replace("\"Original\"", "\"Changed!\"")

        assertThrows(IllegalArgumentException::class.java) { DocumentCheckpointCodec.decode(tampered) }
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
        val legacy = encoded.replaceFirst("\"pages\":[{", "\"pages\":[{\"strokes\":[],")

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

    @Test fun checkpointRejectsLegacyPngFillMasksBeforeWriting() {
        val document = InkDocument(
            title = "legacy fill",
            pages = listOf(InkPage(elements = listOf(
                PageElement.Fill(x = 0f, y = 0f, width = 10f, height = 10f, maskAssetPath = "/legacy/mask.png"),
            ))),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DocumentCheckpointCodec.encode(document)
        }

        assertEquals(DocumentCheckpointCodec.LEGACY_ENGINE_ERROR, error.message)
    }
}
