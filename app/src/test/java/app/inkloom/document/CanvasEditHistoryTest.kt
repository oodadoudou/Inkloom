package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasEditHistoryTest {
    @Test fun externalFillIsOneUndoAndOneRedoAction() {
        val history = CanvasEditHistory()
        var fills = 1
        history.record(CanvasEdit.External(undo = { fills-- }, redo = { fills++ }))

        (history.takeUndo() as CanvasEdit.External).undo()
        assertEquals(0, fills)
        (history.takeRedo() as CanvasEdit.External).redo()
        assertEquals(1, fills)
    }

    @Test fun clearingHistoryDisposesRetainedFillAssetsExactlyOnce() {
        val history = CanvasEditHistory()
        var releases = 0
        history.record(CanvasEdit.External({}, {}, dispose = { releases++ }))
        history.clear()
        assertEquals(1, releases)
    }
}
