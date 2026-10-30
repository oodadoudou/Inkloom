package app.inkloom.document

import app.inkloom.engine.v5.storage.DocumentCheckpointCodec
import app.inkloom.engine.v5.storage.RecoveryJournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileDocumentStoreTest {
    @Test fun `failed atomic replacement leaves the previous checkpoint and in memory document intact`() {
        val root = Files.createTempDirectory("inkloom-failed-checkpoint").toFile()
        var refuseReplacement = false
        val store = FileDocumentStore(root) { temporary, target ->
            !refuseReplacement && temporary.renameTo(target)
        }
        val original = InkDocument(title = "Keep this notebook")
        store.save(original)
        val checkpoint = root.resolve("documents/${original.id}.json")
        val originalBytes = checkpoint.readBytes()
        refuseReplacement = true
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            store.save(original.copy(title = "Must not replace the checkpoint"))
        }
        org.junit.Assert.assertArrayEquals(originalBytes, checkpoint.readBytes())
        assertEquals(original, store.get(original.id))
        assertEquals(original, FileDocumentStore(root).get(original.id))
        assertTrue(!root.resolve("documents/.${original.id}.json.tmp").exists())
        root.deleteRecursively()
    }

    @Test fun `checkpoints use the exact v5 envelope and reload`() {
        val root = Files.createTempDirectory("inkloom-v5-checkpoint").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "V5 checkpoint")

        store.save(document)

        val checkpoint = root.resolve("documents/${document.id}.json").readText()
        assertTrue(checkpoint.contains("\"format\":\"inkloom-document-v5\""))
        assertTrue(checkpoint.contains("\"schemaVersion\":5"))
        assertTrue(checkpoint.contains("\"engineVersion\":5"))
        assertTrue(checkpoint.contains("\"checksum\""))
        assertEquals(document, FileDocumentStore(root).get(document.id))
    }

    @Test fun `a page edit is durable through the journal without rewriting the checkpoint`() {
        val root = Files.createTempDirectory("inkloom-page-journal").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Journal", pages = listOf(InkPage(title = "One"), InkPage(title = "Two")))
        store.save(document)
        val checkpointBefore = root.resolve("documents/${document.id}.json").readText()
        val edited = document.pages[1].copy(title = "Two, edited")

        store.replacePage(document.id, edited, updatedAt = 42L)

        assertEquals("the checkpoint is not rewritten on a page edit", checkpointBefore, root.resolve("documents/${document.id}.json").readText())
        assertEquals("Two, edited", store.get(document.id)!!.pages[1].title)
        val reopened = FileDocumentStore(root).get(document.id)!!
        assertEquals("a second reader (or a restart after a crash) replays the journal", "Two, edited", reopened.pages[1].title)
        assertEquals(42L, reopened.updatedAt)
        assertEquals(1, store.recover())
        assertTrue(root.resolve("documents/${document.id}.json").readText().contains("Two, edited"))
        assertEquals(0L, root.resolve("journal.jsonl").length())
        root.deleteRecursively()
    }

    @Test fun `background checkpoint writes edited notebooks and empties the journal`() {
        val root = Files.createTempDirectory("inkloom-background-checkpoint").toFile()
        val store = FileDocumentStore(root, checkpointDelayMillis = 5L)
        val untouched = InkDocument(title = "Untouched")
        val document = InkDocument(title = "Edited")
        store.save(untouched); store.save(document)
        val untouchedModified = root.resolve("documents/${untouched.id}.json").lastModified()
        store.replacePage(document.id, document.pages.single().copy(title = "Inked"))

        store.awaitRealtimeWrites()

        assertTrue(root.resolve("documents/${document.id}.json").readText().contains("Inked"))
        assertEquals(0L, root.resolve("journal.jsonl").length())
        assertEquals("only edited notebooks are written", untouchedModified, root.resolve("documents/${untouched.id}.json").lastModified())
        assertEquals("Inked", FileDocumentStore(root).get(document.id)!!.pages.single().title)
        root.deleteRecursively()
    }

    @Test fun `a whole-notebook save is never undone by older journal entries`() {
        val root = Files.createTempDirectory("inkloom-journal-order").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Order")
        store.save(document)
        store.replacePage(document.id, document.pages.single().copy(title = "Edited page"))
        val renamed = store.get(document.id)!!.copy(title = "Renamed notebook", pages = store.get(document.id)!!.pages.map { it.copy(title = "Saved page") })

        store.save(renamed)

        val reopened = FileDocumentStore(root).get(document.id)!!
        assertEquals("Renamed notebook", reopened.title)
        assertEquals("Saved page", reopened.pages.single().title)
        root.deleteRecursively()
    }

    @Test fun `a crash between a notebook save and trimming the journal keeps the saved pages`() {
        val root = Files.createTempDirectory("inkloom-save-crash").toFile()
        var journalFrozen = false
        val store = FileDocumentStore(root) { temporary, target ->
            !(journalFrozen && target.name == "journal.jsonl") && temporary.renameTo(target)
        }
        val document = InkDocument(title = "Crash", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        store.replacePage(document.id, document.pages.single().copy(title = "Drawn"))
        // The process dies after the checkpoint is replaced and before the journal is trimmed.
        journalFrozen = true

        store.save(store.get(document.id)!!.let { it.copy(pages = it.pages.map { page -> page.copy(title = "Saved") }) })

        assertTrue("the older page is still in the journal", root.resolve("journal.jsonl").readText().contains("Drawn"))
        assertEquals("Saved", FileDocumentStore(root).get(document.id)!!.pages.single().title)
        root.deleteRecursively()
    }

    @Test fun `journal sequences keep growing after the journal is emptied`() {
        val root = Files.createTempDirectory("inkloom-journal-sequence").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Sequence", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        store.replacePage(document.id, document.pages.single().copy(title = "First"))
        assertEquals(1, store.recover())

        store.replacePage(document.id, document.pages.single().copy(title = "Second"))

        assertEquals("an edit after recovery is not taken for one the checkpoint holds",
            "Second", FileDocumentStore(root).get(document.id)!!.pages.single().title)
        root.deleteRecursively()
    }

    @Test fun `a page the checkpoint would refuse never reaches the journal`() {
        val root = Files.createTempDirectory("inkloom-refused-page").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Refused", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        val legacyFill = PageElement.Fill(x = 0f, y = 0f, width = 10f, height = 10f, maskAssetPath = "/legacy/fill.png")

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            store.replacePage(document.id, document.pages.single().copy(elements = listOf(legacyFill)))
        }

        assertEquals(0L, root.resolve("journal.jsonl").length())
        assertEquals(document, store.get(document.id))
        assertEquals(0, store.recover())
        root.deleteRecursively()
    }

    @Test fun `start-up recovery cuts a torn journal tail so later edits are replayed`() {
        val root = Files.createTempDirectory("inkloom-torn-journal").toFile()
        val document = InkDocument(title = "Torn", pages = listOf(InkPage(title = "Page")))
        FileDocumentStore(root).apply {
            save(document)
            replacePage(document.id, document.pages.single().copy(title = "Kept"))
        }
        root.resolve("journal.jsonl").appendText("{\"format\":\"inkloom-journal-v5\",\"payload\":")

        val restarted = FileDocumentStore(root)
        assertEquals("Kept", restarted.get(document.id)!!.pages.single().title)
        assertEquals(1, restarted.recover())
        restarted.replacePage(document.id, document.pages.single().copy(title = "After restart"))

        assertEquals("After restart", FileDocumentStore(root).get(document.id)!!.pages.single().title)
        root.deleteRecursively()
    }

    @Test fun `a closed store still journals edits and the next store checkpoints them`() {
        val root = Files.createTempDirectory("inkloom-closed-store").toFile()
        val store = FileDocumentStore(root, checkpointDelayMillis = 60_000L)
        val document = InkDocument(title = "Closed", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        store.close()

        store.replacePage(document.id, document.pages.single().copy(title = "After close"))
        store.awaitRealtimeWrites()

        val next = FileDocumentStore(root)
        assertEquals("After close", next.get(document.id)!!.pages.single().title)
        assertEquals(1, next.recover())
        root.deleteRecursively()
    }

    @Test fun `exclusive runs a read-modify-write that saves`() {
        val root = Files.createTempDirectory("inkloom-exclusive").toFile()
        val store = FileDocumentStore(root, checkpointDelayMillis = 1L)
        val document = InkDocument(title = "Exclusive", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        store.replacePage(document.id, document.pages.single().copy(title = "Inked"))

        store.exclusive { store.save(store.get(document.id)!!.copy(title = "Renamed")) }
        store.awaitRealtimeWrites()

        val reopened = FileDocumentStore(root).get(document.id)!!
        assertEquals("Renamed", reopened.title)
        assertEquals("Inked", reopened.pages.single().title)
        store.close()
        root.deleteRecursively()
    }

    @Test(timeout = 10_000) fun `a read-modify-write during a background checkpoint does not deadlock`() {
        val root = Files.createTempDirectory("inkloom-lock-order").toFile()
        val checkpointWriting = java.util.concurrent.CountDownLatch(1)
        val store = FileDocumentStore(root, checkpointDelayMillis = 1L) { temporary, target ->
            if (Thread.currentThread().name == "Inkloom-Checkpoint" && target.name != "journal.jsonl") {
                checkpointWriting.countDown()
                Thread.sleep(200)
            }
            temporary.renameTo(target)
        }
        val document = InkDocument(title = "Lock order", pages = listOf(InkPage(title = "Page")))
        store.save(document)
        store.replacePage(document.id, document.pages.single().copy(title = "Inked"))
        checkpointWriting.await()

        store.exclusive { store.save(store.get(document.id)!!.copy(title = "Renamed")) }
        store.awaitRealtimeWrites()

        val reopened = FileDocumentStore(root).get(document.id)!!
        assertEquals("Renamed", reopened.title)
        assertEquals("Inked", reopened.pages.single().title)
        store.close()
        root.deleteRecursively()
    }

    @Test fun `older pretty-printed checkpoints still read`() {
        val root = Files.createTempDirectory("inkloom-pretty-checkpoint").toFile()
        val document = InkDocument(title = "Pretty", pages = listOf(InkPage(title = "Page")))
        val compact = DocumentCheckpointCodec.encode(document)
        val pretty = Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(compact))
        root.resolve("documents").mkdirs()
        root.resolve("documents/${document.id}.json").writeText(pretty)

        assertEquals(document, FileDocumentStore(root).get(document.id))
        root.deleteRecursively()
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
            maskAssetPath = "/private/document-assets/gradient.fill-mask.v5",
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

    @Test fun failedPermanentDeleteKeepsTheCheckpointAndInMemoryDocument() {
        val root = Files.createTempDirectory("inkloom-delete-failure")
        val directory = root.resolve("documents")
        val store = FileDocumentStore(root.toFile())
        val document = InkDocument(title = "Keep after failed deletion")
        store.save(document)
        val checkpoint = directory.resolve("${document.id}.json")
        val bytes = Files.readAllBytes(checkpoint)
        val permissions = Files.getPosixFilePermissions(directory)
        try {
            Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"))
            org.junit.Assert.assertFalse("Fault fixture must deny directory writes", Files.isWritable(directory))
            org.junit.Assert.assertFalse(store.deletePermanently(document.id))
            assertEquals("A failed delete must not disappear from the current library", document, store.get(document.id))
            assertEquals(listOf(document), store.list())
            org.junit.Assert.assertArrayEquals(bytes, Files.readAllBytes(checkpoint))
            assertEquals(document, FileDocumentStore(root.toFile()).get(document.id))
        } finally {
            Files.setPosixFilePermissions(directory, permissions)
            root.toFile().deleteRecursively()
        }
    }

    @Test fun deletingAnAbsentCheckpointIsIdempotentAndJournalCannotResurrectIt() {
        val root = Files.createTempDirectory("inkloom-delete-journal").toFile()
        try {
            val store = FileDocumentStore(root)
            val document = InkDocument(title = "Delete with pending journal")
            store.save(document)
            store.append(RecoveryJournalEntry.ElementUpserted(
                document.id, document.pages.single().id,
                PageElement.Text(x = .1f, y = .2f, text = "Pending ink"),
            ))
            assertTrue(store.deletePermanently(document.id))
            assertTrue(store.deletePermanently(document.id))
            assertNull(store.get(document.id))
            assertNull(FileDocumentStore(root).get(document.id))
            store.recover()
            assertNull(FileDocumentStore(root).get(document.id))
            assertTrue(store.list().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun permanentDeleteFlushesOtherNotebooksPendingInk() {
        val root=Files.createTempDirectory("delete-shared-journal").toFile()
        try{
            val store=FileDocumentStore(root);val removed=InkDocument(title="Removed");val kept=InkDocument(title="Kept")
            store.save(removed);store.save(kept)
            val text=PageElement.Text(text="Unsaved checkpoint ink",x=.1f,y=.2f)
            store.append(RecoveryJournalEntry.ElementUpserted(kept.id,kept.pages.single().id,text))
            assertTrue(store.deletePermanently(removed.id))
            val reopened=FileDocumentStore(root)
            assertNull(reopened.get(removed.id));assertEquals(listOf(text),reopened.get(kept.id)!!.pages.single().elements)
            assertEquals(0,reopened.recover())
        }finally{root.deleteRecursively()}
    }

    @Test fun deleteKeepsDocumentsSavedByAnotherLocalStore() {
        val root = Files.createTempDirectory("inkloom-delete-reader").toFile()
        try {
            val store = FileDocumentStore(root)
            val removed = InkDocument(title = "Remove")
            val kept = InkDocument(title = "Other reader saved")
            store.save(removed)
            FileDocumentStore(root).save(kept)
            assertTrue(store.deletePermanently(removed.id))
            assertEquals(listOf(kept), store.list())
            assertEquals(listOf(kept), FileDocumentStore(root).list())
        } finally {
            root.deleteRecursively()
        }
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
        val custom = InkLayer(name = "Diagrams")
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
