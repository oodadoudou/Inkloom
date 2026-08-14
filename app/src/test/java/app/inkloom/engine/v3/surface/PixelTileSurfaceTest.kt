package app.inkloom.engine.v3.surface

import app.inkloom.engine.v3.surface.PixelTileSurface.HistoryDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileSurfaceTest {
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
