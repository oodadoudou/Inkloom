package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MaterialStoreTest {
    private val raster = RasterTileSurfaceRef("/tmp/m.json", "s", 10, 10, 128, 1)

    @Test fun materialsPersistRenameAndDelete() {
        val root = Files.createTempDirectory("materials").toFile()
        val store = MaterialStore(root)
        val image = Material(name = "Tree", kind = MaterialKind.IMAGE, imageAssetPath = "/tmp/tree.png", imageWidth = 40, imageHeight = 80)
        val template = Material(name = "Frames", kind = MaterialKind.TEMPLATE, page = InkPage(title = "Frames"))
        store.add(image); store.add(template)
        assertEquals(listOf("Tree", "Frames"), MaterialStore(root).list().map { it.name })
        store.rename(image.id, "  Pine  ")
        assertEquals("Pine", MaterialStore(root).list().first { it.id == image.id }.name)
        store.delete(template.id)
        assertEquals(listOf(image.id), MaterialStore(root).list().map { it.id })
        root.deleteRecursively()
    }

    @Test fun garbageCollectionSeesEveryMaterialAsset() {
        val page = InkPage(layers = defaultInkLayers().map { if (it.id == LayerIds.INK) it.copy(rasterTileSurface = raster) else it })
        val document = MaterialStore.asDocument(listOf(
            Material(name = "Tree", kind = MaterialKind.IMAGE, imageAssetPath = "/tmp/tree.png", imageWidth = 4, imageHeight = 4),
            Material(name = "Page", kind = MaterialKind.TEMPLATE, page = page)))
        assertTrue(document.pages.any { p -> p.elements.any { (it as? PageElement.Image)?.assetPath == "/tmp/tree.png" } })
        assertTrue(document.pages.any { p -> p.layers.any { it.rasterTileSurface == raster } })
    }

    @Test fun pagesFromTemplatesGetNewIdsAndShareImmutablePixels() {
        val text = PageElement.Text(x = .1f, y = .1f, width = 10f, height = 10f, text = "Title")
        val template = InkPage(id = "t", title = "Template", bookmarked = true, elements = listOf(text),
            layers = defaultInkLayers().map { if (it.id == LayerIds.INK) it.copy(rasterTileSurface = raster) else it })
        var n = 0
        val page = MaterialStore.pageFromTemplate(template, "Page 4") { "id${n++}" }
        assertNotEquals("t", page.id); assertEquals("Page 4", page.title); assertFalse(page.bookmarked)
        assertNotEquals(text.id, page.elements.single().id)
        assertEquals(raster, page.layers.first { it.id == LayerIds.INK }.rasterTileSurface)
    }

    @Test fun eachKindNeedsItsContent() {
        assertTrue(runCatching { Material(name = "x", kind = MaterialKind.IMAGE) }.isFailure)
        assertTrue(runCatching { Material(name = "x", kind = MaterialKind.TEMPLATE, imageAssetPath = "/a") }.isFailure)
    }
}
