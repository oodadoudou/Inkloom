package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileDocumentStoreTest {
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

    @Test fun realtimeJournalIsVisibleImmediatelyAndPersistsInOrder() {
        val root = Files.createTempDirectory("inkloom-realtime-writer").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Realtime queue")
        store.save(document)
        val pageId = document.pages.single().id

        repeat(24) { index ->
            store.appendRealtime(
                JournalEntry.StrokeAdded(
                    document.id,
                    pageId,
                    InkStroke(id = "queued-$index", points = List(128) { point -> StrokePoint(point / 128f, index / 24f) }),
                ),
            )
            assertEquals(index + 1, store.get(document.id)?.pages?.single()?.strokes?.size)
        }

        store.awaitRealtimeWrites()
        assertEquals(
            (0 until 24).map { "queued-$it" },
            FileDocumentStore(root).get(document.id)?.pages?.single()?.strokes?.map(InkStroke::id),
        )
    }

    @Test fun liveInkJournalDoesNotRewriteWholeDocumentOnEveryStroke() {
        val root = Files.createTempDirectory("inkloom-live-journal").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Realtime")
        store.save(document)
        val documentFile = root.resolve("documents/${document.id}.json")
        val checkpointBytes = documentFile.readBytes()

        repeat(12) { index ->
            store.append(
                JournalEntry.StrokeAdded(
                    document.id,
                    document.pages.single().id,
                    InkStroke(id = "rapid-$index", points = List(48) { point -> StrokePoint(point / 48f, index / 12f) }),
                ),
            )
        }

        assertEquals("Pen-up must only append the recovery journal", checkpointBytes.toList(), documentFile.readBytes().toList())
        assertEquals(12, store.get(document.id)?.pages?.single()?.strokes?.size)
        assertEquals("A new reader must replay uncheckpointed ink", 12, FileDocumentStore(root).get(document.id)?.pages?.single()?.strokes?.size)
        assertEquals(12, store.recover())
        assertEquals(12, FileDocumentStore(root).get(document.id)?.pages?.single()?.strokes?.size)
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

    @Test fun journalIsIdempotentAndRecoversStroke() {
        val root = Files.createTempDirectory("inkloom-recovery").toFile()
        val store = FileDocumentStore(root)
        val document = InkDocument(title = "Recovery")
        store.save(document)
        val stroke = InkStroke(points = listOf(StrokePoint(.1f, .2f), StrokePoint(.3f, .4f)))
        val entry = JournalEntry.StrokeAdded(document.id, document.pages.first().id, stroke)
        store.append(entry)
        assertEquals(1, store.get(document.id)?.pages?.first()?.strokes?.size)
        assertEquals(1, store.recover())
        assertEquals(1, store.get(document.id)?.pages?.first()?.strokes?.size)
        assertEquals(0, store.recover())
    }

    @Test fun layersAndStrokeOwnershipRoundTrip() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-layers").toFile())
        val custom = InkLayer(name = "Diagrams")
        val page = InkPage(
            layers = defaultInkLayers() + custom,
            strokes = listOf(InkStroke(points = listOf(StrokePoint(.1f, .2f)), layerId = custom.id)),
            elements = listOf(PageElement.Text(x = .2f, y = .3f, layerId = custom.id, text = "Layered")),
        )
        val document = InkDocument(title = "Layers", pages = listOf(page))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single()
        assertEquals(custom.id, restored.strokes.single().layerId)
        assertEquals(custom.id, restored.elements.single().layerId)
        assertEquals("Diagrams", restored.layers.last().name)
    }

    @Test fun legacyDocumentsReceiveDefaultLayersAndOwnership() {
        val json = Json { prettyPrint = true; classDiscriminator = "type"; ignoreUnknownKeys = true }
        val original = InkDocument(
            title = "Legacy",
            pages = listOf(InkPage(strokes = listOf(InkStroke(points = listOf(StrokePoint(.1f, .2f))))))
        )
        val legacy = json.encodeToString(original)
            .replace(Regex("\\n\\s*\\\"layers\\\"\\s*:\\s*\\[[\\s\\S]*?\\],(?=\\n\\s*\\\"strokes\\\")"), "")
            .replace(Regex("\\n\\s*\\\"layerId\\\"\\s*:\\s*\\\"[^\\\"]+\\\","), "")

        val restored = json.decodeFromString<InkDocument>(legacy)
        assertEquals(listOf(LayerIds.BACKGROUND, LayerIds.INK), restored.pages.single().layers.map { it.id })
        assertEquals(LayerIds.INK, restored.pages.single().strokes.single().layerId)
    }

    @Test fun journalUpsertsAndRemovesTransformedObjects() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-objects").toFile())
        val document = InkDocument(title = "Objects")
        store.save(document)
        val pageId = document.pages.single().id
        val path = listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, .2f), ShapeVertex(.7f, 1f))
        val shape = PageElement.Shape(x = .2f, y = .3f, width = 400f, height = 220f, rotationDegrees = 30f, opacity = .6f, kind = ShapeKind.FREEFORM, path = path)
        store.append(JournalEntry.ElementUpserted(document.id, pageId, shape))
        val transformed = shape.copy(x = .4f, width = 520f, rotationDegrees = 45f)
        store.append(JournalEntry.ElementUpserted(document.id, pageId, transformed))

        val restored = store.get(document.id)!!.pages.single().elements.single() as PageElement.Shape
        assertEquals(.4f, restored.x)
        assertEquals(520f, restored.width)
        assertEquals(45f, restored.rotationDegrees)
        assertEquals(ShapeKind.FREEFORM, restored.kind)
        assertEquals(path, restored.path)

        store.append(JournalEntry.ElementRemoved(document.id, pageId, shape.id))
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

    @Test fun importedSecondaryDynamicsAndPointVelocityRoundTrip() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-dynamics").toFile())
        val dynamics = BrushSecondaryDynamics(
            tiltEnabled = true,
            tiltMinimum = .2f,
            velocityEnabled = true,
            velocityMinimum = .4f,
            velocityCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)),
            randomEnabled = true,
            randomMinimum = .6f,
        )
        val stroke = InkStroke(
            points = listOf(StrokePoint(.2f, .3f, velocity = .75f)),
            masks = listOf(StrokeMaskDab(.4f, .5f, .03f, .02f)),
            tipAssetPath = "/tmp/tip-a.png",
            tipAssetPaths = listOf("/tmp/tip-a.png", "/tmp/tip-b.png"),
            thicknessVertical = true,
            ribbon = true,
            tipSequenceMode = BrushTipSequence.REVERSE,
            spray = true,
            sprayParticleRotationDegrees = 28f,
            sprayParticleFollowStrokeDirection = true,
            sprayParticleRandomRotationDegrees = 72f,
            dualSpray = true,
            dualSprayParticleRotationDegrees = -14f,
            dualSprayParticleFollowStylusOrientation = true,
            dualSprayParticleRandomRotationDegrees = 45f,
            sizeSecondaryDynamics = dynamics,
        )
        val document = InkDocument(title = "Dynamics", pages = listOf(InkPage(strokes = listOf(stroke))))
        store.save(document)

        val restored = store.get(document.id)!!.pages.single().strokes.single()
        assertEquals(.75f, restored.points.single().velocity)
        assertEquals(dynamics, restored.sizeSecondaryDynamics)
        assertEquals(listOf("/tmp/tip-a.png", "/tmp/tip-b.png"), restored.tipAssetPaths)
        assertEquals(true, restored.thicknessVertical)
        assertEquals(true, restored.ribbon)
        assertEquals(BrushTipSequence.REVERSE, restored.tipSequenceMode)
        assertEquals(28f, restored.sprayParticleRotationDegrees)
        assertEquals(true, restored.sprayParticleFollowStrokeDirection)
        assertEquals(72f, restored.sprayParticleRandomRotationDegrees)
        assertEquals(-14f, restored.dualSprayParticleRotationDegrees)
        assertEquals(true, restored.dualSprayParticleFollowStylusOrientation)
        assertEquals(45f, restored.dualSprayParticleRandomRotationDegrees)
        assertEquals(stroke.masks, restored.masks)
    }

    @Test fun multiStrokeReplacementIsAtomicAndRecoveryIsIdempotent() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-lasso-batch").toFile())
        val first = InkStroke(id = "first", points = listOf(StrokePoint(.1f, .1f)))
        val second = InkStroke(id = "second", points = listOf(StrokePoint(.2f, .2f)))
        val document = InkDocument(title = "Lasso", pages = listOf(InkPage(id = "page", strokes = listOf(first, second))))
        store.save(document)
        val moved = first.copy(points = listOf(StrokePoint(.7f, .7f)))
        val copied = second.copy(id = "copy", points = listOf(StrokePoint(.3f, .3f)))

        store.append(JournalEntry.StrokesReplaced(document.id, "page", listOf(first.id), listOf(moved, copied)))
        assertEquals(listOf("second", "first", "copy"), store.get(document.id)!!.pages.single().strokes.map(InkStroke::id))

        assertEquals(1, store.recover())
        val recovered = store.get(document.id)!!.pages.single().strokes
        assertEquals(listOf("second", "first", "copy"), recovered.map(InkStroke::id))
        assertEquals(.7f, recovered.first { it.id == "first" }.points.single().x)
    }

    @Test fun preservesStrokeRenderSeedAndLoadsLegacyStrokesDeterministically() {
        val json = Json { prettyPrint = true; classDiscriminator = "type"; ignoreUnknownKeys = true }
        val explicit = InkStroke(
            id = "explicit-seed",
            renderSeed = 91_337,
            points = listOf(StrokePoint(.1f, .2f), StrokePoint(.3f, .4f)),
        )

        val restored = json.decodeFromString<InkStroke>(json.encodeToString(explicit))
        assertEquals(91_337, restored.renderSeed)

        val legacy = """{"id":"legacy-stroke","points":[{"x":0.1,"y":0.2}]}"""
        val legacyRestored = json.decodeFromString<InkStroke>(legacy)
        assertEquals("legacy-stroke".hashCode(), legacyRestored.renderSeed)
    }

    @Test fun positionedStrokeReplacementPreservesExactZOrderAndRecovery() {
        val store = FileDocumentStore(Files.createTempDirectory("inkloom-positioned-strokes").toFile())
        val back = InkStroke(id = "back", points = listOf(StrokePoint(.1f, .1f)))
        val middle = InkStroke(id = "middle", points = listOf(StrokePoint(.2f, .2f)))
        val front = InkStroke(id = "front", points = listOf(StrokePoint(.3f, .3f)))
        val document = InkDocument(title = "Order", pages = listOf(InkPage(id = "page", strokes = listOf(back, middle, front))))
        store.save(document)
        val leftPiece = middle.copy(id = "left", points = listOf(StrokePoint(.15f, .2f), StrokePoint(.18f, .2f)))
        val rightPiece = middle.copy(id = "right", points = listOf(StrokePoint(.22f, .2f), StrokePoint(.25f, .2f)))

        store.append(
            JournalEntry.StrokesReplaced(
                document.id,
                "page",
                removedStrokeIds = listOf(middle.id),
                strokes = listOf(leftPiece, rightPiece),
                replacementIndices = listOf(1, 2),
            ),
        )

        assertEquals(listOf("back", "left", "right", "front"), store.get(document.id)!!.pages.single().strokes.map(InkStroke::id))
        assertEquals(1, store.recover())
        assertEquals(listOf("back", "left", "right", "front"), store.get(document.id)!!.pages.single().strokes.map(InkStroke::id))
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
