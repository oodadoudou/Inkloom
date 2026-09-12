package app.inkloom.document

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NotebookCoverTemplateTest {
    @Test fun reusableCoverSurvivesCollectionAndDeletionKeepsDocumentReferences() {
        val root = Files.createTempDirectory("inkloom-cover-template").toFile()
        try {
            val assets = root.resolve("document-assets").apply { mkdirs() }
            val file = assets.resolve("cover.png").apply { writeText("cover"); setLastModified(1L) }
            val cover = NotebookCoverTemplate(name = "My art", assetPath = file.absolutePath)
            val store = PageTemplateStore(root)
            store.addCover(cover)
            assertEquals(listOf(cover), PageTemplateStore(root).load().customCovers)
            assertEquals(0, DocumentAssetGarbageCollector.collect(assets, emptyList(), store.load()))
            assertTrue(file.isFile)
            val notebook = InkDocument(title = "Using cover", coverStyle = CoverStyle.CUSTOM_IMAGE, coverImagePath = cover.assetPath)
            store.deleteCover(cover.id)
            assertTrue(PageTemplateStore(root).load().customCovers.isEmpty())
            assertEquals(0, DocumentAssetGarbageCollector.collect(assets, listOf(notebook), store.load()))
            assertTrue(file.isFile)
            assertEquals(1, DocumentAssetGarbageCollector.collect(assets, emptyList(), store.load()))
            assertFalse(file.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun coversAreIndependentOfPapersAndRejectEmptyOrDuplicateEntries() {
        val root = Files.createTempDirectory("inkloom-cover-validation").toFile()
        try {
            val store = PageTemplateStore(root)
            val paper = requireNotNull(PageTemplateOperations.customPaper("Dots", 1000, 1500, PageBackground.Paper()))
            store.add(listOf(paper))
            val cover = NotebookCoverTemplate(name = "Cover", assetPath = "asset.png")
            store.addCover(cover)
            assertThrows(IllegalArgumentException::class.java) { store.addCover(cover) }
            assertThrows(IllegalArgumentException::class.java) { store.addCover(cover.copy(id = "empty", assetPath = "")) }
            store.deleteCover(cover.id)
            assertEquals(listOf(paper), store.load().customTemplates)
        } finally { root.deleteRecursively() }
    }
}
