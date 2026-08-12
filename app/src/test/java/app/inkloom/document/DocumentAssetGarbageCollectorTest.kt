package app.inkloom.document

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAssetGarbageCollectorTest {
    @Test fun `collector retains shared document and template assets and deletes only orphans`() {
        val root = Files.createTempDirectory("inkloom-asset-gc").toFile()
        try {
            val mask = root.resolve("mask.png").apply { writeText("mask") }
            val raster = root.resolve("raster-layer.png").apply { writeText("raster") }
            val image = root.resolve("image.png").apply { writeText("image") }
            val template = root.resolve("template.pdf").apply { writeText("template") }
            val orphan = root.resolve("orphan.png").apply {
                writeText("orphan")
                setLastModified(System.currentTimeMillis() - 10 * 60 * 1_000L)
            }
            val newImport = root.resolve("new-import.png").apply { writeText("not committed yet") }
            val pending = root.resolve("pending.png").apply { writeText("pending") }
            val outside = Files.createTempFile("inkloom-outside", ".png").toFile().apply { writeText("outside") }
            val document = InkDocument(
                title = "GC",
                coverImagePath = outside.absolutePath,
                pages = listOf(InkPage(
                    layers = defaultInkLayers() + InkLayer(name = "Pixels", contentKind = LayerContentKind.RASTER, rasterAssetPath = raster.absolutePath),
                    elements = listOf(
                    PageElement.Image(x = 0f, y = 0f, width = 10f, height = 10f, assetPath = image.absolutePath),
                    PageElement.Shape(x = 0f, y = 0f, width = 10f, height = 10f, maskAssetPath = mask.absolutePath),
                ))),
            )
            val templates = PageTemplateLibrary(customTemplates = listOf(PageTemplate(name = "PDF", background = PageBackground.Pdf(template.absolutePath, 0))))

            assertEquals(1, DocumentAssetGarbageCollector.collect(root, listOf(document), templates, setOf(pending.absolutePath)))
            assertTrue(mask.isFile)
            assertTrue(raster.isFile)
            assertTrue(image.isFile)
            assertTrue(template.isFile)
            assertTrue(pending.isFile)
            assertTrue(newImport.isFile)
            assertTrue(outside.isFile)
            assertFalse(orphan.exists())
            outside.delete()
        } finally {
            root.deleteRecursively()
        }
    }
}
