package app.inkloom.document

import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DocumentAssetLifecyclesTest {
    @Test fun editorAndCollectorShareReservationsAndReferenceCountHistoryPins() {
        val directory = Files.createTempDirectory("inkloom-shared-history").toFile()
        try {
            fun service() = DocumentAssetLifecycles.forDirectory(directory, PixelTileArchiveStore(File(directory, "pixel-tiles")))
            val editor = service(); val collector = service()
            val path = editor.saveBytes(byteArrayOf(1, 2, 3), "layer-mask.v5")
            val file = File(path).apply { setLastModified(System.currentTimeMillis() - 600_000) }
            assertEquals(0, collector.garbageCollect(emptyList(), PageTemplateLibrary()))
            val releaseFirst = editor.retainHistoryAssets(listOf(path))
            val releaseSecond = service().retainHistoryAssets(listOf(path))
            editor.releaseAssetReservation(path)
            releaseFirst()
            assertEquals(0, collector.garbageCollect(emptyList(), PageTemplateLibrary()))
            assertTrue(file.isFile)
            releaseSecond()
            assertEquals(1, collector.garbageCollect(emptyList(), PageTemplateLibrary()))
            assertFalse(file.exists())
        } finally { directory.deleteRecursively() }
    }
}
