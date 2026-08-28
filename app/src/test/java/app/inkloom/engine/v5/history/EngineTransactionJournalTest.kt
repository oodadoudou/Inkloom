package app.inkloom.engine.v5.history

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTransactionJournalTest {
    @Test fun externalFillIsOneUndoAndOneRedoAction() {
        val history = EngineTransactionJournal()
        var fills = 1
        history.record(EngineTransaction(undo = { fills-- }, redo = { fills++ }))

        history.takeUndo()!!.undo()
        assertEquals(0, fills)
        history.takeRedo()!!.redo()
        assertEquals(1, fills)
    }

    @Test fun clearingHistoryDisposesRetainedFillAssetsExactlyOnce() {
        val history = EngineTransactionJournal()
        var releases = 0
        history.record(EngineTransaction({}, {}, dispose = { releases++ }))
        history.clear()
        assertEquals(1, releases)
    }

    @Test fun boundedJournalDisposesEvictedTransactionsWithoutGrowingForever() {
        var releases = 0
        val history = EngineTransactionJournal(capacity = 100)
        repeat(1_000) {
            history.record(EngineTransaction({}, {}, dispose = { releases++ }))
        }
        assertEquals(900, releases)
        history.clear()
        assertEquals(1_000, releases)
    }

    @Test fun clearOwnsASnapshotBeforeAReentrantDisposeCallbackRuns() {
        val history = EngineTransactionJournal()
        var releases = 0
        history.record(EngineTransaction({}, {}, dispose = {
            releases++
            history.record(EngineTransaction({}, {}, dispose = { releases++ }))
        }))

        history.clear()
        assertEquals(1, releases)
        history.clear()
        assertEquals(2, releases)
    }
}
