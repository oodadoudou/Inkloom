package app.inkloom.engine.v5.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileHistoryTest {
    @Test fun chronologicalUndoAndRedoSwapCommittedGenerationsAcrossSurfaces() {
        val notes = PixelTileSurface(256, 256, surfaceId = "notes")
        val highlights = PixelTileSurface(256, 256, surfaceId = "highlights")
        val history = PixelTileHistory()
        history.record(notes, notes.paint("note", 10, 10, 0xff010203.toInt()))
        history.record(highlights, highlights.paint("highlight", 20, 20, 0xffaabbcc.toInt()))

        assertEquals("highlight", history.undo())
        assertEquals(0, highlights.pixelAt(20, 20))
        assertEquals(0xff010203.toInt(), notes.pixelAt(10, 10))
        assertEquals("note", history.undo())
        assertEquals(0, notes.pixelAt(10, 10))
        assertNull(history.undo())

        assertEquals("note", history.redo())
        assertEquals("highlight", history.redo())
        assertEquals(0xff010203.toInt(), notes.pixelAt(10, 10))
        assertEquals(0xffaabbcc.toInt(), highlights.pixelAt(20, 20))
        assertNull(history.redo())
    }

    @Test fun recordingAfterUndoDisposesAndClearsTheRedoBranch() {
        val surface = PixelTileSurface(128, 128)
        val history = PixelTileHistory()
        history.record(surface, surface.paint("first", 1, 1, 0xff000001.toInt()))
        val abandoned = surface.paint("abandoned", 1, 1, 0xff000002.toInt())
        history.record(surface, abandoned)
        history.undo()

        history.record(surface, surface.paint("replacement", 1, 1, 0xff000003.toInt()))

        assertTrue(abandoned.isClosed)
        assertEquals(0, history.redoCount)
        assertNull(history.redo())
        assertEquals(0xff000003.toInt(), surface.pixelAt(1, 1))
    }

    @Test fun countBudgetEvictsAndReleasesOldestRecords() {
        val surface = PixelTileSurface(128, 128, tileSize = 64)
        val history = PixelTileHistory(maxEntries = 2, maxRetainedBytes = Long.MAX_VALUE)
        val records = (1..3).map { value ->
            surface.paint("edit $value", value, 1, 0xff000000.toInt() or value).also {
                history.record(surface, it)
            }
        }

        assertTrue(records.first().isClosed)
        assertFalse(records[1].isClosed)
        assertFalse(records[2].isClosed)
        assertEquals(2, history.undoCount)
        assertEquals("edit 3", history.undo())
        assertEquals("edit 2", history.undo())
        assertNull(history.undo())
        assertEquals(0xff000001.toInt(), surface.pixelAt(1, 1))
    }

    @Test fun byteBudgetIsHardForOldEntriesButRetainsTheLatestUndo() {
        val surface = PixelTileSurface(128, 128, tileSize = 64)
        val oneNewTileBytes = 64L * 64 * Int.SIZE_BYTES
        val history = PixelTileHistory(maxEntries = 100, maxRetainedBytes = oneNewTileBytes)
        val first = surface.paint("first", 1, 1, 0xff000001.toInt())
        history.record(surface, first)
        val second = surface.paint("second", 70, 1, 0xff000002.toInt())
        history.record(surface, second)

        assertTrue(first.isClosed)
        assertFalse(second.isClosed)
        assertEquals(1, history.undoCount)
        assertEquals(oneNewTileBytes, history.retainedByteSize)

        val oversized = surface.beginWrite("oversized").run {
            setPixel(2, 2, 0xff000003.toInt())
            setPixel(70, 2, 0xff000004.toInt())
            commit()
        }
        history.record(surface, oversized)
        assertEquals(1, history.undoCount)
        assertTrue(history.retainedByteSize > oneNewTileBytes)
        assertEquals("oversized", history.undo())
    }

    @Test fun emptyCommitsAreReleasedAndDoNotCreatePhantomUndoSteps() {
        val surface = PixelTileSurface(128, 128)
        val history = PixelTileHistory()
        val empty = surface.beginWrite("empty").commit()

        assertFalse(history.record(surface, empty))
        assertTrue(empty.isClosed)
        assertEquals(0, history.undoCount)
    }

    @Test fun staleHistoryFailureLeavesJournalOrderUntouched() {
        val surface = PixelTileSurface(128, 128)
        val history = PixelTileHistory()
        history.record(surface, surface.paint("journal edit", 2, 2, 0xff000001.toInt()))
        surface.paint("untracked edit", 2, 2, 0xff000002.toInt()).close()

        assertThrows(IllegalStateException::class.java) { history.undo() }
        assertEquals(1, history.undoCount)
        assertEquals(0, history.redoCount)
        assertEquals(0xff000002.toInt(), surface.pixelAt(2, 2))
    }

    @Test fun aRecordCannotEnterHistoryAfterAnUntrackedEditHasSupersededIt() {
        val surface = PixelTileSurface(128, 128)
        val history = PixelTileHistory()
        val stale = surface.paint("stale", 2, 2, 0xff000001.toInt())
        surface.paint("newer", 2, 2, 0xff000002.toInt()).close()

        assertThrows(IllegalStateException::class.java) { history.record(surface, stale) }
        assertEquals(0, history.undoCount)
        assertFalse(stale.isClosed)
        stale.close()
    }

    @Test fun clearReleasesBothStacksAndResetsRetainedBytes() {
        val surface = PixelTileSurface(128, 128)
        val history = PixelTileHistory()
        val first = surface.paint("first", 1, 1, 0xff000001.toInt())
        history.record(surface, first)
        val second = surface.paint("second", 1, 1, 0xff000002.toInt())
        history.record(surface, second)
        history.undo()

        history.clear()

        assertTrue(first.isClosed)
        assertTrue(second.isClosed)
        assertEquals(0L, history.retainedByteSize)
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    private fun PixelTileSurface.paint(label: String, x: Int, y: Int, color: Int) =
        beginWrite(label).run {
            setPixel(x, y, color)
            commit()
        }
}
