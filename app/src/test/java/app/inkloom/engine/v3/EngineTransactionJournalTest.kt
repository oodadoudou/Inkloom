package app.inkloom.engine.v3

import app.inkloom.engine.v3.scene.EngineTransaction
import app.inkloom.engine.v3.scene.EngineTransactionJournal
import app.inkloom.document.InkStroke
import app.inkloom.document.PositionedStroke
import app.inkloom.document.StrokeEdit
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTransactionJournalTest {
    @Test fun externalFillIsOneUndoAndOneRedoAction() {
        val history = EngineTransactionJournal()
        var fills = 1
        history.record(EngineTransaction.External(undo = { fills-- }, redo = { fills++ }))

        (history.takeUndo() as EngineTransaction.External).undo()
        assertEquals(0, fills)
        (history.takeRedo() as EngineTransaction.External).redo()
        assertEquals(1, fills)
    }

    @Test fun clearingHistoryDisposesRetainedFillAssetsExactlyOnce() {
        val history = EngineTransactionJournal()
        var releases = 0
        history.record(EngineTransaction.External({}, {}, dispose = { releases++ }))
        history.clear()
        assertEquals(1, releases)
    }

    @Test fun strokeTransactionCarriesItsExactLayerIsolationBoundary() {
        val before = InkStroke(id = "stroke", layerId = "notes", points = emptyList())
        val after = before.copy(layerId = "paint")
        val transaction = EngineTransaction.Strokes(
            StrokeEdit(
                before = listOf(PositionedStroke(0, before)),
                after = listOf(PositionedStroke(0, after)),
            ),
        )

        assertEquals(setOf("notes", "paint"), transaction.affectedLayerIds)
    }

    @Test fun boundedJournalDisposesEvictedTransactionsWithoutGrowingForever() {
        var releases = 0
        val history = EngineTransactionJournal(capacity = 100)
        repeat(1_000) {
            history.record(EngineTransaction.External({}, {}, dispose = { releases++ }))
        }
        assertEquals(900, releases)
        history.clear()
        assertEquals(1_000, releases)
    }
}
