package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PageTemplateStoreTest {
    @Test
    fun `custom templates favorites and defaults survive reload`() {
        val root = Files.createTempDirectory("inkloom-page-templates").toFile()
        try {
            val store = PageTemplateStore(root)
            val custom = PageTemplate(name = "Lecture PDF", width = 1200f, height = 1800f, background = PageBackground.Pdf("/safe/template.pdf", 2))

            store.add(listOf(custom))
            store.toggleFavorite(custom.id)
            store.setNotebookDefault("notebook-1", custom.id)

            val restored = PageTemplateStore(root).load()
            assertEquals(custom, restored.template(custom.id))
            assertTrue(custom.id in restored.favoriteIds)
            assertEquals(custom.id, restored.notebookDefaults["notebook-1"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `deleting custom template removes favorites and defaults`() {
        val root = Files.createTempDirectory("inkloom-page-templates").toFile()
        try {
            val store = PageTemplateStore(root)
            val custom = PageTemplate(name = "Image", background = PageBackground.Image("/safe/template.png"))
            store.add(listOf(custom))
            store.toggleFavorite(custom.id)
            store.setNotebookDefault("notebook-1", custom.id)

            val restored = store.delete(custom.id)

            assertNull(restored.template(custom.id))
            assertFalse(custom.id in restored.favoriteIds)
            assertNull(restored.notebookDefaults["notebook-1"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `applying template preserves populated page content coordinates`() {
        val raster = defaultInkLayers().map { layer ->
            if (layer.id == LayerIds.INK) layer.copy(
                contentKind = LayerContentKind.RASTER,
                rasterTileSurface = RasterTileSurfaceRef("/safe/page.json", "surface", 1600, 2263, 256, 4),
            ) else layer
        }
        val page = InkPage(width = 1600f, height = 2263f, layers = raster)
        val template = PageTemplate(name = "Wide", width = 3000f, height = 2000f, background = PageBackground.Image("/safe/wide.png"))

        val applied = PageTemplateOperations.apply(page, template)

        assertEquals(1600f, applied.width)
        assertEquals(2263f, applied.height)
        assertEquals(raster, applied.layers)
        assertEquals(template.background, applied.background)
    }

    @Test
    fun `empty pages and new pages adopt template dimensions`() {
        val template = PageTemplate(name = "Square", width = 2048f, height = 2048f, background = PageBackground.Paper(pattern = PaperPattern.DOT))

        val applied = PageTemplateOperations.apply(InkPage(), template)
        val added = PageTemplateOperations.newPage(template, "Page 4")

        assertEquals(2048f, applied.width)
        assertEquals(2048f, applied.height)
        assertEquals(2048f, added.width)
        assertEquals(template.background, added.background)
    }
}
