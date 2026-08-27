package app.inkloom.document

import app.inkloom.engine.v5.storage.RecoveryJournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileDocumentStoreTest {
    @Test fun `checkpoints use the exact v5 envelope and reload`() {
        val root = Files.createTempDirectory("inkloom-v5-checkpoint").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "V5 checkpoint")

        store.save(document)

        val checkpoint = root.resolve("documents/${document.id}.json").readText()
        assertTrue(checkpoint.contains("\"format\": \"inkloom-document-v5\""))
        assertTrue(checkpoint.contains("\"schemaVersion\": 5"))
        assertTrue(checkpoint.contains("\"engineVersion\": 5"))
        assertTrue(checkpoint.contains("\"checksum\""))
        assertEquals(document, FileDocumentStore(root).get(document.id))
    }

    @Test fun `legacy bare document checkpoint is rejected without migration`() {
        val root = Files.createTempDirectory("inkloom-legacy-checkpoint").toFile()
        val document = InkDocument(title = "Legacy checkpoint")
        val json = Json { prettyPrint = true; classDiscriminator = "type"; ignoreUnknownKeys = true }
        root.resolve("documents").mkdirs()
        root.resolve("documents/${document.id}.json").writeText(json.encodeToString(document))

        val restored = FileDocumentStore(root).get(document.id)

        assertNull(restored)
    }

    @Test fun `truncated journal tail preserves every verified prior operation`() {
        val root = Files.createTempDirectory("inkloom-truncated-journal").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Truncated recovery")
        store.save(document)
        repeat(2) { index ->
            store.append(RecoveryJournalEntry.ElementUpserted(
                document.id,
                document.pages.single().id,
                PageElement.Text(id = "safe-$index", x = 0f, y = 0f, text = "$index"),
            ))
        }
        root.resolve("journal.jsonl").appendText("{\"format\":\"inkloom-journal-v5\",\"payload\":")

        val restored = FileDocumentStore(root).get(document.id)!!

        assertEquals(listOf("safe-0", "safe-1"), restored.pages.single().elements.map(PageElement::id))
    }

    @Test fun `journal checksum failure stops replay before later dependent operations`() {
        val root = Files.createTempDirectory("inkloom-corrupt-journal").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Verified recovery")
        store.save(document)
        repeat(3) { index ->
            store.append(RecoveryJournalEntry.ElementUpserted(
                document.id,
                document.pages.single().id,
                PageElement.Text(id = "ordered-$index", x = 0f, y = 0f, text = "$index"),
            ))
        }
        val journal = root.resolve("journal.jsonl")
        val lines = journal.readLines().toMutableList()
        val checksumMarker = "\"checksum\":\""
        val checksumStart = lines[1].indexOf(checksumMarker) + checksumMarker.length
        assertTrue(checksumStart >= checksumMarker.length)
        lines[1] = lines[1].replaceRange(
            checksumStart,
            checksumStart + 1,
            if (lines[1][checksumStart] == '0') "1" else "0",
        )
        journal.writeText(lines.joinToString("\n", postfix = "\n"))

        val restored = FileDocumentStore(root).get(document.id)!!

        assertEquals(listOf("ordered-0"), restored.pages.single().elements.map(PageElement::id))
    }

    @Test fun `professional gradient properties survive document persistence`() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-gradient").toFile())
        val gradient = PageElement.Shape(
            x = 0f,
            y = 0f,
            width = 1600f,
            height = 2263f,
            fillArgb = 0xFF112233L,
            gradientEndArgb = 0xFFABCDEF,
            gradientKind = GradientKind.DIAMOND,
            gradientEdgeMode = GradientEdgeMode.MIRROR,
            gradientReverse = true,
            gradientDither = false,
            gradientEllipseRatio = .42f,
            gradientStops = listOf(
                GradientColorStop(0f, 0xFF112233L),
                GradientColorStop(.37f, 0xFF445566L),
                GradientColorStop(1f, 0xFFABCDEF),
            ),
            maskAssetPath = "/private/document-assets/gradient-mask.png",
        )
        val document = InkDocument(title = "Gradient", pages = listOf(InkPage(elements = listOf(gradient))))

        store.save(document)

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Shape
        assertEquals(GradientKind.DIAMOND, restored.gradientKind)
        assertEquals(GradientEdgeMode.MIRROR, restored.gradientEdgeMode)
        assertEquals(true, restored.gradientReverse)
        assertEquals(false, restored.gradientDither)
        assertEquals(.42f, restored.gradientEllipseRatio)
        assertEquals(gradient.gradientStops, restored.gradientStops)
        assertEquals(gradient.maskAssetPath, restored.maskAssetPath)
    }

    @Test fun savesListsAndDeletesDocuments() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-store").toFile())
        val document = InkDocument(title = "Persistent note")
        store.save(document)
        assertEquals("Persistent note", store.get(document.id)?.title)
        assertEquals(1, store.list().size)
        assertTrue(store.deletePermanently(document.id))
        assertNull(store.get(document.id))
    }

    @Test fun journalIsIdempotentAndRecoversPageObject() {
        val root = Files.createTempDirectory("inkloom-recovery").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Recovery")
        store.save(document)
        val element = PageElement.Text(x = .1f, y = .2f, text = "Recovered")
        val entry = RecoveryJournalEntry.ElementUpserted(document.id, document.pages.first().id, element)
        store.append(entry)
        assertEquals(1, store.get(document.id)?.pages?.first()?.elements?.size)
        assertEquals(1, store.recover())
        assertEquals(1, store.get(document.id)?.pages?.first()?.elements?.size)
        assertEquals(0, store.recover())
    }

    @Test fun layersAndObjectOwnershipRoundTrip() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-layers").toFile())
        val custom = InkLayer(name = "Diagrams", contentKind = LayerContentKind.RASTER)
        val page = InkPage(
            layers = defaultInkLayers() + custom,
            elements = listOf(PageElement.Text(x = .2f, y = .3f, layerId = custom.id, text = "Layered")),
        )
        val document = InkDocument(title = "Layers", pages = listOf(page))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single()
        assertEquals(custom.id, restored.elements.single().layerId)
        assertEquals("Diagrams", restored.layers.last().name)
    }

    @Test fun journalUpsertsAndRemovesTransformedObjects() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-objects").toFile())
        val document = InkDocument(title = "Objects")
        store.save(document)
        val pageId = document.pages.single().id
        val path = listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, .2f), ShapeVertex(.7f, 1f))
        val shape = PageElement.Shape(x = .2f, y = .3f, width = 400f, height = 220f, rotationDegrees = 30f, opacity = .6f, kind = ShapeKind.FREEFORM, path = path)
        store.append(RecoveryJournalEntry.ElementUpserted(document.id, pageId, shape))
        val transformed = shape.copy(x = .4f, width = 520f, rotationDegrees = 45f)
        store.append(RecoveryJournalEntry.ElementUpserted(document.id, pageId, transformed))

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Shape
        assertEquals(.4f, restored.x)
        assertEquals(520f, restored.width)
        assertEquals(45f, restored.rotationDegrees)
        assertEquals(ShapeKind.FREEFORM, restored.kind)
        assertEquals(path, restored.path)

        store.append(RecoveryJournalEntry.ElementRemoved(document.id, pageId, shape.id))
        assertTrue(store.get(document.id)!!.pages.single().elements.isEmpty())
    }

    @Test fun audioMarkersPersistAsPortablePageObjects() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-audio").toFile())
        val marker = PageElement.AudioMarker(x = .15f, y = .2f, assetPath = "/data/audio/note.m4a", title = "Lecture", durationMs = 65_000)
        val document = InkDocument(title = "Audio", pages = listOf(InkPage(elements = listOf(marker))))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.AudioMarker
        assertEquals("Lecture", restored.title)
        assertEquals(65_000, restored.durationMs)
        assertEquals(LayerIds.INK, restored.layerId)
    }

    @Test fun richTextPropertiesRoundTrip() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-rich-text").toFile())
        val text = PageElement.Text(
            x = .1f, y = .2f, text = "First\nSecond", bold = true, italic = true,
            underline = true, alignment = InkTextAlignment.CENTER,
            listStyle = TextListStyle.NUMBERED, lineSpacing = 1.6f,
        )
        val document = InkDocument(title = "Rich text", pages = listOf(InkPage(elements = listOf(text))))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Text
        assertEquals(true, restored.bold)
        assertEquals(true, restored.italic)
        assertEquals(true, restored.underline)
        assertEquals(InkTextAlignment.CENTER, restored.alignment)
        assertEquals(TextListStyle.NUMBERED, restored.listStyle)
        assertEquals(1.6f, restored.lineSpacing)
    }

    @Test fun nonDestructiveImageCropAndMaskRoundTrip() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-image-crop").toFile())
        val image = PageElement.Image(
            x = .1f, y = .2f, width = 500f, height = 300f, assetPath = "/data/image.png",
            crop = ImageCrop(.1f, .2f, .85f, .9f), mask = ImageMask.ROUNDED, cornerRadius = 72f,
        )
        val document = InkDocument(title = "Image crop", pages = listOf(InkPage(elements = listOf(image))))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Image
        assertEquals(ImageCrop(.1f, .2f, .85f, .9f), restored.crop)
        assertEquals(ImageMask.ROUNDED, restored.mask)
        assertEquals(72f, restored.cornerRadius)
        assertEquals("/data/image.png", restored.assetPath)
    }

    @Test fun stickySubcanvasStrokesRoundTripWithBrushSnapshot() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-sticky-ink").toFile())
        val childStroke = InkStroke(
            id = "sticky-stroke",
            points = listOf(StrokePoint(.1f, .2f), StrokePoint(.8f, .7f)),
            brushId = "imported-brush",
            tipAssetPath = "/data/tip.png",
            width = 38f,
        )
        val sticky = PageElement.Sticky(
            id = "sticky",
            x = .2f,
            y = .3f,
            text = "Formula",
            collapsed = true,
            strokes = listOf(childStroke),
        )
        val document = InkDocument(title = "Sticky ink", pages = listOf(InkPage(elements = listOf(sticky))))

        store.save(document)

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Sticky
        assertEquals(true, restored.collapsed)
        assertEquals(listOf(childStroke), restored.strokes)
        assertEquals("/data/tip.png", restored.strokes.single().tipAssetPath)
    }

    @Test fun nativePdfTextIndexRoundTripsWithoutChangingThePdfBackground() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-pdf-index").toFile())
        val bounds = PdfTextBounds(.1f, .2f, .7f, .27f)
        val page = InkPage(
            background = PageBackground.Pdf("/data/lecture.pdf", 3),
            pdfTextBlocks = listOf(PdfTextBlock("Searchable lecture text", listOf(bounds))),
            pdfTextIndexVersion = 1,
        )
        val document = InkDocument(title = "PDF index", pages = listOf(page))

        store.save(document)

        val restored = store.get(document.id)!!.pages.single()
        assertEquals(PageBackground.Pdf("/data/lecture.pdf", 3), restored.background)
        assertEquals(1, restored.pdfTextIndexVersion)
        assertEquals(bounds, restored.pdfTextBlocks.single().bounds.single())
        assertEquals("Searchable lecture text", restored.pdfTextBlocks.single().text)
    }

    @Test fun linkObjectRoundTripsWithItsSafeTargetAndLayout() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-link").toFile())
        val link = PageElement.Link(
            id = "link",
            x = .18f,
            y = .27f,
            width = 640f,
            height = 170f,
            rotationDegrees = 12f,
            opacity = .8f,
            title = "Inkloom guide",
            url = "https://example.com/inkloom",
        )
        val document = InkDocument(title = "Links", pages = listOf(InkPage(elements = listOf(link))))

        store.save(document)

        assertEquals(link, store.get(document.id)!!.pages.single().elements.single())
    }
}
