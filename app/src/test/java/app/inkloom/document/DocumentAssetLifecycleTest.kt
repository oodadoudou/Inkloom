package app.inkloom.document

import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import app.inkloom.engine.v5.surface.PixelTileSurface
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAssetLifecycleTest {
    @Test fun `new document asset remains reserved until its owning transaction releases it`() {
        val root = Files.createTempDirectory("inkloom-asset-lifecycle").toFile()
        try {
            val assets = root.resolve("document-assets").apply(File::mkdirs)
            val lifecycle = DocumentAssetLifecycle(assets, PixelTileArchiveStore(assets.resolve("pixel-tiles")))
            val path = lifecycle.saveBytes("mask".encodeToByteArray(), "fill-mask.v5")
            val file = File(path).apply { setLastModified(System.currentTimeMillis() - 10 * 60 * 1_000L) }

            assertEquals(0, lifecycle.garbageCollect(emptyList(), PageTemplateLibrary()))
            assertTrue(file.isFile)

            lifecycle.releaseAssetReservation(path)
            assertEquals(1, lifecycle.garbageCollect(emptyList(), PageTemplateLibrary()))
            assertFalse(file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `raster manifest remains reserved until document commit releases it`() {
        val root = Files.createTempDirectory("inkloom-raster-lifecycle").toFile()
        try {
            val assets = root.resolve("document-assets").apply(File::mkdirs)
            val lifecycle = DocumentAssetLifecycle(assets, PixelTileArchiveStore(assets.resolve("pixel-tiles")))
            val reference = lifecycle.saveRasterTileSurface(PixelTileSurface(512, 512), null)

            assertEquals(0, lifecycle.garbageCollect(emptyList(), PageTemplateLibrary()))
            assertTrue(File(reference.manifestAssetPath).isFile)

            lifecycle.releaseRasterTileReservation(reference)
            assertTrue(lifecycle.garbageCollect(emptyList(), PageTemplateLibrary()) >= 1)
            assertFalse(File(reference.manifestAssetPath).exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
