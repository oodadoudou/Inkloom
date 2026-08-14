package app.inkloom.engine.v3.storage

import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileArchiveCodecTest {
    @Test fun sparseEightKSurfaceRoundTripsWithoutMaterializingPagePixels() {
        val source = PixelTileSurface(8192, 8192, tileSize = 128, surfaceId = "surface")
        source.beginWrite("fixture").run {
            setPixel(1, 2, 0xff010203.toInt())
            setPixel(4096, 4097, 0x80402010.toInt())
            setPixel(8191, 8191, 0xffffffff.toInt())
            commit().close()
        }

        val encoded = PixelTileArchiveCodec.encode(source.snapshot())
        val restored = PixelTileArchiveCodec.decode(encoded.manifest, encoded.blobs::get)

        assertEquals(source.surfaceId, restored.surfaceId)
        assertEquals(source.revision, restored.revision)
        assertEquals(3, restored.tileCount)
        assertEquals(0xff010203.toInt(), restored.pixelAt(1, 2))
        assertEquals(0x80402010.toInt(), restored.pixelAt(4096, 4097))
        assertEquals(0xffffffff.toInt(), restored.pixelAt(8191, 8191))
        assertEquals(0, restored.pixelAt(7000, 7000))
        assertTrue(restored.retainedByteSize < 8192L * 8192 * Int.SIZE_BYTES)
    }

    @Test fun identicalTileContentIsDeduplicatedByCompressedContentHash() {
        val source = PixelTileSurface(256, 128, tileSize = 128)
        source.beginWrite("duplicates").run {
            editTile(PixelTileKey(0, 0)) { pixels, _ -> pixels.fill(0xffabcdef.toInt()) }
            editTile(PixelTileKey(1, 0)) { pixels, _ -> pixels.fill(0xffabcdef.toInt()) }
            commit().close()
        }

        val encoded = PixelTileArchiveCodec.encode(source.snapshot())

        assertEquals(1, encoded.blobs.size)
        val restored = PixelTileArchiveCodec.decode(encoded.manifest, encoded.blobs::get)
        assertEquals(2, restored.tileCount)
        assertTrue(restored.snapshot().copyTilePixels(PixelTileKey(0, 0))!!.contentEquals(
            restored.snapshot().copyTilePixels(PixelTileKey(1, 0))!!,
        ))
    }

    @Test fun repetitivePaintTilesCompressBelowTheirRawCapacity() {
        val source = PixelTileSurface(128, 128)
        source.beginWrite("flat color").run {
            editTile(PixelTileKey(0, 0)) { pixels, _ -> pixels.fill(0xff445566.toInt()) }
            commit().close()
        }

        val encoded = PixelTileArchiveCodec.encode(source.snapshot())

        assertTrue(encoded.blobs.values.single().size < 128 * 128 * Int.SIZE_BYTES / 10)
    }

    @Test fun aChangedManifestPayloadFailsItsEnvelopeChecksum() {
        val encoded = fixtureArchive()
        val changed = encoded.manifest.replace("surface", "tampered")

        assertThrows(IllegalArgumentException::class.java) {
            PixelTileArchiveCodec.decode(changed, encoded.blobs::get)
        }
    }

    @Test fun missingChangedAndTruncatedBlobsAreRejectedBeforeRestoration() {
        val encoded = fixtureArchive()
        val hash = encoded.blobs.keys.single()
        val blob = encoded.blobs.getValue(hash)

        assertThrows(IllegalArgumentException::class.java) {
            PixelTileArchiveCodec.decode(encoded.manifest) { null }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelTileArchiveCodec.decode(encoded.manifest) { requested ->
                if (requested == hash) blob.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() } else null
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelTileArchiveCodec.decode(encoded.manifest) { requested ->
                if (requested == hash) blob.copyOf(blob.size - 1) else null
            }
        }
    }

    @Test fun restoreCopiesCallerArraysAndPrunesTransparentTiles() {
        val opaque = IntArray(64 * 64).apply { this[0] = 0xff010203.toInt() }
        val transparent = IntArray(64 * 64)
        val restored = PixelTileSurface.restore(
            logicalWidth = 128,
            logicalHeight = 64,
            tileSize = 64,
            surfaceId = "restored",
            revision = 9,
            tiles = mapOf(PixelTileKey(0, 0) to opaque, PixelTileKey(1, 0) to transparent),
        )
        opaque[0] = 0

        assertEquals(1, restored.tileCount)
        assertEquals(9, restored.revision)
        assertEquals(0xff010203.toInt(), restored.pixelAt(0, 0))
    }

    private fun fixtureArchive(): PixelTileArchiveCodec.Encoded {
        val source = PixelTileSurface(128, 128, tileSize = 128, surfaceId = "surface")
        source.beginWrite("fixture").run {
            setPixel(3, 4, 0xff123456.toInt())
            commit().close()
        }
        return PixelTileArchiveCodec.encode(source.snapshot())
    }
}
