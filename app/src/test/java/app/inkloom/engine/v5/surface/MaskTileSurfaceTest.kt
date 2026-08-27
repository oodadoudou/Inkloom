package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.surface.MaskTileSurface.HistoryDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskTileSurfaceTest {
    @Test fun eightKSelectionStartsWithoutAFullPageAllocation() {
        val surface = MaskTileSurface(8192, 8192)

        assertEquals(0, surface.tileCount)
        assertEquals(0L, surface.retainedByteSize)
        assertTrue(surface.snapshot().isEmpty)
    }

    @Test fun nonZeroBoundsUsesSparseTilesAndImplicitDefaultWithoutPageMaterialization() {
        val sparse = MaskTileSurface(8192, 8192, tileSize = 128)
        sparse.beginWrite("two exact islands").run {
            setAlpha(130, 257, 255)
            setAlpha(4099, 6001, 128)
            commit().close()
        }
        assertEquals(PixelRect(130, 257, 4100, 6002), sparse.snapshot().nonZeroBounds())

        val implicit = MaskTileSurface(8192, 8192, tileSize = 128, initialDefaultAlpha = 255)
        assertEquals(PixelRect(0, 0, 8192, 8192), implicit.snapshot().nonZeroBounds())
        assertEquals(0, implicit.tileCount)
    }

    @Test fun onePixelAllocatesOneOneByteTileAndPublishesAtomically() {
        val surface = MaskTileSurface(8192, 8192, tileSize = 128)
        val transaction = surface.beginWrite("lasso")
        transaction.setAlpha(4096, 4096, 220)

        assertEquals(0, surface.alphaAt(4096, 4096))
        assertEquals(128L * 128, transaction.workingByteSize)

        val record = transaction.commit()
        assertEquals(220, surface.alphaAt(4096, 4096))
        assertEquals(1, surface.tileCount)
        assertEquals(128L * 128, surface.retainedByteSize)
        assertEquals(128L * 128, record.retainedByteSize)
    }

    @Test fun boundedBooleanOperationsPreserveFractionalAlpha() {
        val surface = MaskTileSurface(256, 256, tileSize = 64)
        val bounds = PixelRect(50, 50, 150, 100)
        val half = ByteArray(bounds.width * bounds.height) { 128.toByte() }
        surface.beginWrite("replace").run {
            composeAlpha(bounds, half, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        surface.beginWrite("add").run {
            composeAlpha(bounds, half, mode = MaskComposeMode.ADD)
            commit().close()
        }
        assertEquals(192, surface.alphaAt(75, 75))

        surface.beginWrite("intersect").run {
            composeAlpha(bounds, half, mode = MaskComposeMode.INTERSECT)
            commit().close()
        }
        assertEquals(96, surface.alphaAt(75, 75))

        surface.beginWrite("subtract").run {
            composeAlpha(bounds, half, mode = MaskComposeMode.SUBTRACT)
            commit().close()
        }
        assertEquals(48, surface.alphaAt(75, 75))
        assertEquals(0, surface.alphaAt(20, 20))
    }

    @Test fun regionCopiesCrossTilesWithoutMaterializingThePage() {
        val surface = MaskTileSurface(512, 512, tileSize = 64)
        surface.beginWrite("two islands").run {
            setAlpha(63, 63, 255)
            setAlpha(130, 100, 77)
            commit().close()
        }
        val bounds = PixelRect(60, 60, 140, 110)
        val copy = surface.snapshot().copyAlpha(bounds)

        assertEquals(255, copy[(63 - bounds.top) * bounds.width + 63 - bounds.left].toInt().and(0xff))
        assertEquals(77, copy[(100 - bounds.top) * bounds.width + 130 - bounds.left].toInt().and(0xff))
        assertEquals(0, copy[0].toInt().and(0xff))
        assertEquals(bounds.width * bounds.height, copy.size)
    }

    @Test fun transactionPreviewIsVisibleBeforeAtomicPublishAndDoesNotMutateTruth() {
        val surface = MaskTileSurface(256, 256, tileSize = 64)
        val transaction = surface.beginWrite("live layer mask stroke")
        transaction.composeAlpha(
            PixelRect(20, 20, 40, 40),
            ByteArray(20 * 20) { 180.toByte() },
            mode = MaskComposeMode.ADD,
        )

        val preview = transaction.previewSnapshot()
        assertEquals(180, preview.alphaAt(30, 30))
        assertEquals(0, surface.alphaAt(30, 30))
        transaction.commit().close()
        assertEquals(180, surface.alphaAt(30, 30))
    }

    @Test fun commitUndoRedoSwapImmutableMaskGenerations() {
        val surface = MaskTileSurface(256, 128, tileSize = 64)
        val record = surface.beginWrite("selection replace").run {
            setAlpha(10, 10, 255)
            setAlpha(70, 10, 128)
            commit()
        }
        val committed = surface.snapshot().copyAlpha(PixelRect(0, 0, 128, 32))

        surface.apply(record, HistoryDirection.UNDO)
        assertTrue(surface.isEmpty)
        surface.apply(record, HistoryDirection.REDO)
        assertArrayEquals(committed, surface.snapshot().copyAlpha(PixelRect(0, 0, 128, 32)))
        record.close()
    }

    @Test fun clearRemovesEveryPublishedTileInOneTransaction() {
        val surface = MaskTileSurface(512, 512, tileSize = 64)
        surface.beginWrite("fixture").run {
            setAlpha(10, 10, 255)
            setAlpha(400, 400, 255)
            commit().close()
        }
        val clear = surface.beginWrite("deselect").run { clear(); commit() }

        assertTrue(surface.isEmpty)
        assertEquals(2, clear.changedTileCount)
        surface.apply(clear, HistoryDirection.UNDO)
        assertEquals(255, surface.alphaAt(10, 10))
        assertEquals(255, surface.alphaAt(400, 400))
    }

    @Test fun selectAllAndInvertUseImplicitDefaultWithoutAllocatingEightKTilePlane() {
        val surface = MaskTileSurface(8192, 8192, tileSize = 128)
        val selectAll = surface.beginWrite("select all").run { fill(255); commit() }

        assertEquals(255, surface.defaultAlpha)
        assertEquals(255, surface.alphaAt(0, 0))
        assertEquals(255, surface.alphaAt(8191, 8191))
        assertEquals(0, surface.tileCount)
        assertEquals(0L, surface.retainedByteSize)
        assertEquals(0, selectAll.changedTileCount)

        val invert = surface.beginWrite("invert").run { invert(); commit() }
        assertTrue(surface.isEmpty)
        assertEquals(0, surface.defaultAlpha)
        assertEquals(0, surface.tileCount)
        assertEquals(0, invert.changedTileCount)

        surface.apply(invert, HistoryDirection.UNDO)
        assertEquals(255, surface.defaultAlpha)
        assertEquals(255, surface.alphaAt(4096, 4096))
        surface.apply(selectAll, HistoryDirection.UNDO)
        assertTrue(surface.isEmpty)
    }

    @Test fun sparseInvertKeepsOnlySparseDeviationsAndRoundTripsHistory() {
        val surface = MaskTileSurface(8192, 8192, tileSize = 128)
        surface.beginWrite("sparse island").run {
            setAlpha(42, 51, 200)
            commit().close()
        }
        val record = surface.beginWrite("invert sparse").run { invert(); commit() }

        assertEquals(255, surface.defaultAlpha)
        assertEquals(55, surface.alphaAt(42, 51))
        assertEquals(255, surface.alphaAt(7000, 7000))
        assertEquals(1, surface.tileCount)
        assertEquals(128L * 128, surface.retainedByteSize)

        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(0, surface.defaultAlpha)
        assertEquals(200, surface.alphaAt(42, 51))
        assertEquals(0, surface.alphaAt(7000, 7000))
    }

    @Test fun booleanCompositionCombinesImplicitDefaultsAndExplicitDeviations() {
        val destination = MaskTileSurface(256, 256, tileSize = 64, initialDefaultAlpha = 255)
        destination.beginWrite("destination hole").run {
            setAlpha(10, 10, 0)
            commit().close()
        }
        val source = MaskTileSurface(256, 256, tileSize = 64)
        source.beginWrite("source island").run {
            setAlpha(10, 10, 255)
            setAlpha(100, 100, 128)
            commit().close()
        }

        destination.beginWrite("intersect").run {
            composeSurface(source.snapshot(), MaskComposeMode.INTERSECT)
            commit().close()
        }

        assertEquals(0, destination.defaultAlpha)
        assertEquals(0, destination.alphaAt(10, 10))
        assertEquals(128, destination.alphaAt(100, 100))
        assertEquals(0, destination.alphaAt(200, 200))
        assertEquals(1, destination.tileCount)
    }
}
