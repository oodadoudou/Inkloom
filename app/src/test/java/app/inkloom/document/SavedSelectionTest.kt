package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.selection.SavedSelectionComposition
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SavedSelectionTest {
    @Test fun savedSelectionModesPreserveSoftAlphaAndLeaveOriginalsUnchanged() {
        val current = MaskTileSurface(20, 20, 64)
        current.beginWrite("current").use { it.setAlpha(5, 5, 128); it.commit().close() }
        val saved = MaskTileSurface(20, 20, 64)
        saved.beginWrite("saved").use {
            it.setAlpha(5, 5, 128)
            it.setAlpha(6, 5, 255)
            it.commit().close()
        }
        val a = current.snapshot(); val b = saved.snapshot()
        assertEquals(192, SavedSelectionComposition.apply(a, b, MaskComposeMode.ADD).alphaAt(5, 5))
        assertEquals(64, SavedSelectionComposition.apply(a, b, MaskComposeMode.SUBTRACT).alphaAt(5, 5))
        assertEquals(64, SavedSelectionComposition.apply(a, b, MaskComposeMode.INTERSECT).alphaAt(5, 5))
        assertEquals(255, SavedSelectionComposition.apply(a, b, MaskComposeMode.REPLACE).alphaAt(6, 5))
        assertEquals(0, a.alphaAt(6, 5))
        assertEquals(255, b.alphaAt(6, 5))
    }

    @Test fun savedMaskSurvivesCheckpointAndGarbageCollection() {
        val root = Files.createTempDirectory("saved-selection").toFile()
        try {
            val assets = root.resolve("assets").apply { mkdirs() }
            val mask = assets.resolve("selection.layer-mask.v5").apply {
                writeText("fixture"); setLastModified(1)
            }
            val entry = SavedSelection(name = "Selection 1",
                surface = MaskTileSurfaceRef(mask.path, "selection", 40, 30, 64, 2, 0, 1))
            val document = InkDocument(title = "Saved selection", pages = listOf(InkPage(savedSelections = listOf(entry))))
            FileDocumentStore(root).save(document)
            val restored = FileDocumentStore(root).get(document.id)!!
            assertEquals(entry, restored.pages.single().savedSelections.single())
            assertEquals(0, DocumentAssetGarbageCollector.collect(assets, listOf(restored), PageTemplateLibrary()))
            assertTrue(mask.exists())
            assertEquals(1, DocumentAssetGarbageCollector.collect(assets, listOf(restored.copy(pages = emptyList())), PageTemplateLibrary()))
        } finally { root.deleteRecursively() }
    }

    @Test fun savedSelectionRetainsItsPagePositionAcrossCropAndRotation() {
        val mask = MaskTileSurface(100, 80, 64)
        mask.beginWrite("fixture").use { it.setAlpha(40, 30, 128); it.commit().close() }
        val cropped = SavedSelectionGeometry.projected(mask.snapshot(), 0, PageCrop(), PageCrop(.2f, .25f, .8f, .75f), 60, 40)
        assertEquals(128, cropped.alphaAt(20, 10))
        val restored = SavedSelectionGeometry.projected(cropped, 0, PageCrop(.2f, .25f, .8f, .75f), PageCrop(), 100, 80)
        assertEquals(128, restored.alphaAt(40, 30))
        assertEquals(0, restored.alphaAt(0, 0))
        val turned = SavedSelectionGeometry.projected(cropped, 1, PageCrop(.2f, .25f, .8f, .75f), PageCrop(), 80, 100)
        assertEquals(128, turned.alphaAt(49, 40))
    }

    @Test fun rotationPreservesSoftAlphaAndInvertedDefault() {
        val mask = MaskTileSurface(40, 30, 64)
        mask.beginWrite("fixture").use { it.fill(255); it.setAlpha(3, 8, 37); it.commit().close() }
        val rotated = SavedSelectionGeometry.rotated(mask.snapshot(), 1)
        assertEquals(30, rotated.logicalWidth); assertEquals(40, rotated.logicalHeight)
        assertEquals(37, rotated.alphaAt(21, 3)); assertEquals(255, rotated.alphaAt(0, 0))
        val roundTrip = SavedSelectionGeometry.rotated(rotated, 3)
        assertArrayEquals(mask.snapshot().copyAlpha(app.inkloom.engine.v5.surface.PixelRect(0, 0, 40, 30)),
            roundTrip.copyAlpha(app.inkloom.engine.v5.surface.PixelRect(0, 0, 40, 30)))
        val page = InkPage(savedSelections = listOf(SavedSelection(name = "Soft",
            surface = MaskTileSurfaceRef("test.layer-mask.v5", "m", 40, 30, 64, 1, 255, 1))))
        assertEquals(1, PageGeometry.rotateClockwise(page).savedSelections.single().rotationQuarterTurns)
    }
}
