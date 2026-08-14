package app.inkloom.engine.v3.storage

import app.inkloom.engine.v3.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PixelTileArchiveStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savePublishesAReopenableManifestAfterItsContentAddressedBlobs() {
        val store = PixelTileArchiveStore(temporary.newFolder("store"))
        val source = fixture("surface", 0xff123456.toInt())

        val manifest = store.save(source.snapshot())
        val reopened = store.load(manifest)
        val storeRoot = requireNotNull(requireNotNull(manifest.parentFile).parentFile)

        assertTrue(manifest.isFile)
        assertEquals("surface", reopened.surfaceId)
        assertEquals(0xff123456.toInt(), reopened.pixelAt(3, 4))
        assertEquals(1, File(storeRoot, "blobs").listFiles().orEmpty().size)
    }

    @Test fun savingAnIdenticalSnapshotReusesTheSameManifestAndBlob() {
        val root = temporary.newFolder("store")
        val store = PixelTileArchiveStore(root)
        val source = fixture("same", 0xffabcdef.toInt())

        val first = store.save(source.snapshot())
        val second = store.save(source.snapshot())

        assertEquals(first.canonicalPath, second.canonicalPath)
        assertEquals(1, File(root, "manifests").listFiles().orEmpty().size)
        assertEquals(1, File(root, "blobs").listFiles().orEmpty().size)
    }

    @Test fun inspectAndReferencedFilesExposeVerifiedPortableSurfaceClosure() {
        val store = PixelTileArchiveStore(temporary.newFolder("store"))
        val manifest = store.save(fixture("portable", 0xff445566.toInt()).snapshot())

        val metadata = store.inspect(manifest)
        val closure = store.referencedFiles(manifest)

        assertEquals("portable", metadata.surfaceId)
        assertEquals(8192, metadata.logicalWidth)
        assertEquals(8192, metadata.logicalHeight)
        assertEquals(PixelTileSurface.DEFAULT_TILE_SIZE, metadata.tileSize)
        assertEquals(1, metadata.tileCount)
        assertEquals(2, closure.size)
        assertEquals(manifest.canonicalPath, closure.first().canonicalPath)
        assertTrue(closure.last().name.endsWith(".tile"))
    }

    @Test fun garbageCollectionTracesRetainedManifestBlobsBeforeDeletingOrphans() {
        val root = temporary.newFolder("store")
        val store = PixelTileArchiveStore(root)
        val retained = store.save(fixture("retained", 0xff000001.toInt()).snapshot())
        val orphan = store.save(fixture("orphan", 0xff000002.toInt()).snapshot())
        assertTrue(orphan.exists())

        val result = store.garbageCollect(listOf(retained))

        assertEquals(1, result.manifestsDeleted)
        assertEquals(1, result.blobsDeleted)
        assertTrue(retained.exists())
        assertEquals(0xff000001.toInt(), store.load(retained).pixelAt(3, 4))
    }

    @Test fun aManifestOutsideTheStoreCannotBeLoadedOrRetained() {
        val store = PixelTileArchiveStore(temporary.newFolder("store"))
        val outside = temporary.newFile("outside.json")

        assertThrows(IllegalArgumentException::class.java) { store.load(outside) }
        assertThrows(IllegalArgumentException::class.java) { store.garbageCollect(listOf(outside)) }
    }

    @Test fun corruptExistingContentAddressedBlobIsNeverSilentlyReused() {
        val root = temporary.newFolder("store")
        val store = PixelTileArchiveStore(root)
        val source = fixture("surface", 0xff102030.toInt())
        store.save(source.snapshot())
        val blob = File(root, "blobs").listFiles().orEmpty().single()
        blob.writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(IllegalArgumentException::class.java) { store.save(source.snapshot()) }
    }

    @Test fun identicalPixelsAcrossDifferentSurfacesShareABlobButKeepDistinctManifests() {
        val root = temporary.newFolder("store")
        val store = PixelTileArchiveStore(root)
        val first = store.save(fixture("first", 0xff778899.toInt()).snapshot())
        val second = store.save(fixture("second", 0xff778899.toInt()).snapshot())

        assertTrue(first != second)
        assertEquals(2, File(root, "manifests").listFiles().orEmpty().size)
        assertEquals(1, File(root, "blobs").listFiles().orEmpty().size)
    }

    private fun fixture(id: String, color: Int): PixelTileSurface =
        PixelTileSurface(8192, 8192, surfaceId = id).also { surface ->
            surface.beginWrite("fixture").run {
                setPixel(3, 4, color)
                commit().close()
            }
        }
}
