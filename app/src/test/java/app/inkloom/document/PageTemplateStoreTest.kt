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
            store.setNotebookPaperColor("notebook-1", PaperColorCatalog.SOFT_GREEN)

            val restored = PageTemplateStore(root).load()
            assertEquals(custom, restored.template(custom.id))
            assertTrue(custom.id in restored.favoriteIds)
            assertEquals(custom.id, restored.notebookDefaults["notebook-1"])
            assertEquals(PaperColorCatalog.SOFT_GREEN, restored.notebookPaperColors["notebook-1"])
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

    @Test
    fun `paper color override keeps the selected pattern and becomes the notebook default`() {
        val root = Files.createTempDirectory("inkloom-paper-colors").toFile()
        try {
            val store = PageTemplateStore(root)
            store.setNotebookDefault("notebook-1", "builtin-grid")
            store.setNotebookPaperColor("notebook-1", PaperColorCatalog.WARM_IVORY)

            val selected = store.load().defaultFor("notebook-1")

            assertEquals(
                PageBackground.Paper(PaperColorCatalog.WARM_IVORY, PaperPattern.GRID),
                selected.background,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `batch template replacement changes only selected backgrounds and preserves content`() {
        val content = PageElement.Text(x = 24f, y = 48f, text = "keep me")
        val first = InkPage(title = "First", elements = listOf(content))
        val second = InkPage(title = "Second")
        val third = InkPage(title = "Third")
        val template = PageTemplate(
            name = "Green ruled",
            background = PageBackground.Paper(PaperColorCatalog.SOFT_GREEN, PaperPattern.RULED),
        )

        val result = PageTemplateOperations.applyToPages(listOf(first, second, third), setOf(first.id, third.id), template)

        assertEquals(template.background, result[0].background)
        assertEquals(listOf(content), result[0].elements)
        assertEquals(second, result[1])
        assertEquals(template.background, result[2].background)
    }
}
