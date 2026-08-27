package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileSurfaceTest {
    @Test fun linearF16TransactionsPreserveExtendedValuesAndUndoExactTileBits() {
        val surface = PixelTileSurface(
            logicalWidth = 256,
            logicalHeight = 128,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )
        val beforeCommit = surface.snapshot()
        val transaction = surface.beginWrite("HDR paint")
        transaction.setLinearPixel(70, 20, LinearRgba(2.5f, -.25f, .5f, .75f))

        assertEquals(0, beforeCommit.tileCount)
        assertEquals(64L * 64 * 8, transaction.workingByteSize)
        assertEquals(0, surface.tileCount)
        assertEquals(2.5f * .75f, transaction.linearPixelAt(70, 20).red, .002f)

        val record = transaction.commit()
        val committedBits = requireNotNull(surface.snapshot().copyTileF16(PixelTileKey(1, 0)))
        assertEquals(64L * 64 * 8, surface.retainedByteSize)
        assertEquals(64L * 64 * 8, record.retainedByteSize)
        assertEquals(2.5f * .75f, surface.linearPixelAt(70, 20).red, .002f)
        assertEquals(-.25f * .75f, surface.linearPixelAt(70, 20).green, .002f)

        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(0, surface.tileCount)
        surface.apply(record, HistoryDirection.REDO)
        assertArrayEquals(committedBits, surface.snapshot().copyTileF16(PixelTileKey(1, 0)))
        record.close()
    }

    @Test fun formatSpecificBulkAccessRejectsTheWrongPixelEncoding() {
        val rgba8 = PixelTileSurface(64, 64, tileSize = 64)
        val f16 = PixelTileSurface(
            64,
            64,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )

        assertThrows(IllegalArgumentException::class.java) {
            rgba8.snapshot().copyPremultipliedF16(PixelRect(0, 0, 1, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            f16.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 1, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            f16.beginWrite("wrong editor").use { it.editTile(PixelTileKey(0, 0)) { _, _ -> } }
        }
    }

    @Test fun bulkRegionReadAndReplaceObserveUnpublishedGesturePixelsAcrossTiles() {
        val surface = PixelTileSurface(300, 200, tileSize = 64)
        surface.beginWrite("seed").run {
            setPixel(60, 70, 0xff010203.toInt())
            setPixel(140, 90, 0xff040506.toInt())
            commit().close()
        }
        val destination = PixelRect(50, 60, 150, 100)
        val replacement = IntArray(destination.width * destination.height) { index ->
            if (index % 11 == 0) 0xffaabbcc.toInt() else 0
        }
        val transaction = surface.beginWrite("bulk gesture")
        transaction.replacePremultipliedPixels(destination, replacement)

        assertEquals("Published generations remain atomic", 0xff010203.toInt(), surface.pixelAt(60, 70))
        assertTrue(replacement.contentEquals(transaction.copyPremultipliedPixels(destination)))
        assertEquals(6, transaction.touchedTileCount)

        val record = transaction.commit()
        assertEquals(6, record.changedTileCount)
        assertEquals(replacement[(70 - destination.top) * destination.width + 60 - destination.left], surface.pixelAt(60, 70))
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(0xff010203.toInt(), surface.pixelAt(60, 70))
        assertEquals(0xff040506.toInt(), surface.pixelAt(140, 90))
        record.close()
    }

    @Test fun snapshotCopiesSparseRectangleAcrossTilesWithTransparentGaps() {
        val surface = PixelTileSurface(300, 200, tileSize = 64)
        surface.beginWrite("copy fixture").run {
            setPixel(63, 65, 0xff112233.toInt())
            setPixel(130, 100, 0xff445566.toInt())
            commit().close()
        }

        val source = PixelRect(60, 60, 140, 110)
        val pixels = surface.snapshot().copyPremultipliedPixels(source)

        assertEquals(0xff112233.toInt(), pixels[(65 - source.top) * source.width + 63 - source.left])
        assertEquals(0xff445566.toInt(), pixels[(100 - source.top) * source.width + 130 - source.left])
        assertEquals(0, pixels[0])
    }

    @Test fun anEightKLogicalSurfaceStartsSparseWithoutAllocatingPagePixels() {
        val surface = PixelTileSurface(8192, 8192)

        assertEquals(0, surface.tileCount)
        assertEquals(0L, surface.retainedByteSize)
        assertEquals(0, surface.snapshot().tileCount)
    }

    @Test fun firstWriteAllocatesOnlyTheTouchedTileAndPublishesAtomically() {
        val surface = PixelTileSurface(8192, 8192, tileSize = 128)
        val transaction = surface.beginWrite("one pixel")
        transaction.setPixel(4096, 4096, 0xff112233.toInt())

        assertEquals("Published surface stays unchanged before commit", 0, surface.pixelAt(4096, 4096))
        assertEquals(1, transaction.touchedTileCount)
        assertEquals(128L * 128 * 4, transaction.workingByteSize)

        val record = transaction.commit()
        assertEquals(1, record.changedTileCount)
        assertEquals(0xff112233.toInt(), surface.pixelAt(4096, 4096))
        assertEquals(1, surface.tileCount)
    }

    @Test fun transactionPreviewIsImmutableCachedAndNeverPublishesGesturePixels() {
        val surface = PixelTileSurface(8192, 8192, tileSize = 128)
        surface.beginWrite("seed").run {
            setPixel(8, 8, 0xff010203.toInt())
            commit().close()
        }
        val publishedRevision = surface.revision
        val transaction = surface.beginWrite("private gesture")
        transaction.setPixel(4096, 4096, 0xff112233.toInt())

        val firstPreview = transaction.previewSnapshot()
        val cachedPreview = transaction.previewSnapshot()
        assertTrue("An unchanged frame must reuse its immutable preview", firstPreview === cachedPreview)
        assertEquals(publishedRevision, firstPreview.revision)
        assertEquals(0xff010203.toInt(), firstPreview.pixelAt(8, 8))
        assertEquals(0xff112233.toInt(), firstPreview.pixelAt(4096, 4096))
        assertEquals("Preview pixels must not leak into document truth", 0, surface.pixelAt(4096, 4096))
        val firstTileGeneration = firstPreview.tileInfo.first { it.key == PixelTileKey(32, 32) }.generation
        assertTrue(firstTileGeneration < 0L)

        transaction.setPixel(4224, 4096, 0xff445566.toInt())
        val nextPreview = transaction.previewSnapshot()
        assertTrue("A mutation must publish a new private preview", firstPreview !== nextPreview)
        assertEquals(
            "A later edit in another tile must retain the immutable generation already presented",
            firstTileGeneration,
            nextPreview.tileInfo.first { it.key == PixelTileKey(32, 32) }.generation,
        )
        assertEquals(0, firstPreview.pixelAt(4224, 4096))
        assertEquals(0xff445566.toInt(), nextPreview.pixelAt(4224, 4096))
        assertEquals(0, surface.pixelAt(4224, 4096))

        transaction.cancel()
        assertEquals(publishedRevision, surface.revision)
        assertEquals(0, surface.pixelAt(4096, 4096))
        assertEquals(0xff112233.toInt(), firstPreview.pixelAt(4096, 4096))
        assertThrows(IllegalStateException::class.java) { transaction.previewSnapshot() }
    }

    @Test fun writesAcrossBoundariesCreateExactlyOneDeltaPerTouchedTile() {
        val surface = PixelTileSurface(512, 512, tileSize = 128)
        val transaction = surface.beginWrite("cross boundary")
        listOf(127 to 127, 128 to 127, 127 to 128, 128 to 128).forEach { (x, y) ->
            transaction.setPixel(x, y, 0xffffffff.toInt())
        }

        val record = transaction.commit()

        assertEquals(4, record.changedTileCount)
        assertEquals(4, surface.tileCount)
        assertEquals(
            setOf(
                PixelTileKey(0, 0), PixelTileKey(1, 0),
                PixelTileKey(0, 1), PixelTileKey(1, 1),
            ),
            record.deltaInfo.mapTo(linkedSetOf()) { it.key },
        )
    }

    @Test fun cancelDropsWorkingTilesAndLeavesPublishedGenerationByteIdentical() {
        val surface = PixelTileSurface(256, 256)
        surface.beginWrite("seed").run {
            setPixel(10, 10, 0xffabcdef.toInt())
            commit()
        }
        val before = surface.snapshot()

        surface.beginWrite("cancelled eraser").run {
            setPixel(10, 10, 0)
            setPixel(140, 140, 0xffffffff.toInt())
            cancel()
        }

        val after = surface.snapshot()
        assertEquals(before.revision, after.revision)
        assertEquals(before.tileCount, after.tileCount)
        assertTrue(before.copyTilePixels(PixelTileKey(0, 0))!!.contentEquals(after.copyTilePixels(PixelTileKey(0, 0))!!))
        assertEquals(0, after.pixelAt(140, 140))
    }

    @Test fun undoAndRedoSwapOnlyCommittedTileGenerations() {
        val surface = PixelTileSurface(384, 128)
        val first = surface.beginWrite("paint").run {
            setPixel(3, 4, 0xff010203.toInt())
            setPixel(260, 4, 0xffaabbcc.toInt())
            commit()
        }
        val committed = surface.snapshot()
        val committedGenerations = committed.tileInfo.associate { it.key to it.generation }

        surface.apply(first, HistoryDirection.UNDO)
        assertEquals(0, surface.tileCount)
        assertEquals(0, surface.pixelAt(3, 4))

        surface.apply(first, HistoryDirection.REDO)
        val redone = surface.snapshot()
        assertEquals(0xff010203.toInt(), redone.pixelAt(3, 4))
        assertEquals(0xffaabbcc.toInt(), redone.pixelAt(260, 4))
        assertEquals(committedGenerations, redone.tileInfo.associate { it.key to it.generation })
    }

    @Test fun clearingTheLastOpaquePixelPrunesTheTileAndIsUndoable() {
        val surface = PixelTileSurface(128, 128)
        surface.beginWrite("paint").run {
            setPixel(20, 30, 0xff556677.toInt())
            commit()
        }

        val erase = surface.beginWrite("erase").run {
            setPixel(20, 30, 0)
            commit()
        }

        assertEquals(0, surface.tileCount)
        assertEquals(null, erase.deltaInfo.single().afterGeneration)
        surface.apply(erase, HistoryDirection.UNDO)
        assertEquals(0xff556677.toInt(), surface.pixelAt(20, 30))
    }

    @Test fun noOpCommitDoesNotChangeRevisionOrRetainAHistoryDelta() {
        val surface = PixelTileSurface(256, 256)
        val revision = surface.revision
        val record = surface.beginWrite("no-op").run {
            setPixel(1, 1, 0)
            commit()
        }

        assertEquals(0, record.changedTileCount)
        assertEquals(revision, surface.revision)
        assertEquals(0, surface.tileCount)
    }

    @Test fun snapshotsKeepImmutableOldGenerationsAfterLaterCommits() {
        val surface = PixelTileSurface(128, 128)
        surface.beginWrite("first").run {
            setPixel(5, 5, 0xff000001.toInt())
            commit()
        }
        val first = surface.snapshot()

        surface.beginWrite("second").run {
            setPixel(5, 5, 0xff000002.toInt())
            commit()
        }

        assertEquals(0xff000001.toInt(), first.pixelAt(5, 5))
        assertEquals(0xff000002.toInt(), surface.pixelAt(5, 5))
        assertNotEquals(first.tileInfo.single().generation, surface.snapshot().tileInfo.single().generation)
    }

    @Test fun dirtyRectUnionsWritesAndClipsEdgeTiles() {
        val surface = PixelTileSurface(150, 140, tileSize = 128)
        val transaction = surface.beginWrite("edge")
        transaction.setPixel(130, 130, 0xffffffff.toInt())
        transaction.setPixel(149, 139, 0xffffffff.toInt())
        transaction.editTile(
            PixelTileKey(1, 1),
            PixelRect(0, 0, 22, 12),
        ) { pixels, stride -> pixels[1 * stride + 1] = 0xff123456.toInt() }

        val info = transaction.commit().deltaInfo.single()
        assertEquals(PixelRect(0, 0, 22, 12), info.dirtyLocalRect)
    }

    @Test fun concurrentOrCrossSurfaceHistoryMutationIsRejected() {
        val first = PixelTileSurface(128, 128)
        val second = PixelTileSurface(128, 128)
        val active = first.beginWrite("active")
        assertThrows(IllegalStateException::class.java) { first.beginWrite("second") }
        active.cancel()

        val record = first.beginWrite("paint").run {
            setPixel(0, 0, 0xffffffff.toInt())
            commit()
        }
        assertThrows(IllegalArgumentException::class.java) {
            second.apply(record, HistoryDirection.REDO)
        }
    }

    @Test fun releasedHistoryCannotBeApplied() {
        val surface = PixelTileSurface(128, 128)
        val record = surface.beginWrite("paint").run {
            setPixel(0, 0, 0xffffffff.toInt())
            commit()
        }
        record.close()

        assertTrue(record.isClosed)
        assertThrows(IllegalStateException::class.java) {
            surface.apply(record, HistoryDirection.UNDO)
        }
        assertEquals(1, record.changedTileCount)
        assertEquals(1, record.deltaInfo.size)
    }

    @Test fun outOfOrderUndoIsRejectedWithoutOverwritingNewerPixels() {
        val surface = PixelTileSurface(128, 128)
        val older = surface.beginWrite("older").run {
            setPixel(4, 4, 0xff000001.toInt())
            commit()
        }
        val newer = surface.beginWrite("newer").run {
            setPixel(4, 4, 0xff000002.toInt())
            commit()
        }

        assertThrows(IllegalStateException::class.java) {
            surface.apply(older, HistoryDirection.UNDO)
        }
        assertEquals(0xff000002.toInt(), surface.pixelAt(4, 4))

        surface.apply(newer, HistoryDirection.UNDO)
        surface.apply(older, HistoryDirection.UNDO)
        assertEquals(0, surface.pixelAt(4, 4))
    }

    @Test fun allSupportedTileSizesPreserveIdenticalLogicalPixels() {
        val snapshots = PixelTileSurface.SUPPORTED_TILE_SIZES.map { tileSize ->
            PixelTileSurface(520, 270, tileSize).also { surface ->
                surface.beginWrite("fixture").run {
                    repeat(250) { index ->
                        val x = (index * 47) % 520
                        val y = (index * 31) % 270
                        setPixel(x, y, 0xff000000.toInt() or index)
                    }
                    commit()
                }
            }.snapshot()
        }

        repeat(250) { index ->
            val x = (index * 47) % 520
            val y = (index * 31) % 270
            val expected = 0xff000000.toInt() or index
            assertTrue(snapshots.all { it.pixelAt(x, y) == expected })
        }
        assertFalse(snapshots.any { it.tileCount == 0 })
    }
}
