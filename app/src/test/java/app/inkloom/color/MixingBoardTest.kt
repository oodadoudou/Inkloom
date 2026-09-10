package app.inkloom.color

import org.junit.Assert.*
import org.junit.Test

class MixingBoardTest {
    @Test fun fastSparseStrokeHasNoHoles() {
        val board = MixingBoard(180, 60)
        board.begin(10f, 30f, MixingBoard.Tool.PAINT, 0xff112244.toInt(), 8f)
        board.move(170f, 30f); assertTrue(board.finish())
        for (x in 11..169) assertNotEquals("hole at $x", MixingBoard.PAPER, board.sample(x.toFloat(), 30f))
    }
    @Test fun cancellationDoesNotCommitAndUndoRedoAreExact() {
        val board = MixingBoard(60, 40)
        val blank = board.snapshot()
        board.begin(10f, 20f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 6f)
        board.move(50f, 20f); board.cancel()
        assertArrayEquals(blank, board.snapshot()); assertFalse(board.canUndo)
        board.begin(10f, 20f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 6f)
        board.move(50f, 20f); board.finish(); val painted = board.snapshot()
        assertTrue(board.undo()); assertArrayEquals(blank, board.snapshot())
        assertTrue(board.redo()); assertArrayEquals(painted, board.snapshot())
        assertTrue(board.clear()); assertArrayEquals(blank, board.snapshot())
        assertTrue(board.undo()); assertArrayEquals(painted, board.snapshot())
    }
    @Test fun blendCarriesExistingColorInsteadOfPaintingTheSelectedColor() {
        val board = MixingBoard(80, 40)
        board.begin(10f, 20f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 10f)
        board.move(35f, 20f); board.finish()
        board.begin(45f, 20f, MixingBoard.Tool.PAINT, 0xff0000ff.toInt(), 10f)
        board.move(70f, 20f); board.finish()
        board.begin(25f, 20f, MixingBoard.Tool.BLEND, 0xff00ff00.toInt(), 10f)
        board.move(60f, 20f); board.finish()
        val mixed = board.sample(48f, 20f)
        assertTrue((mixed ushr 16 and 255) > 10)
        assertTrue((mixed and 255) > 10)
        assertTrue((mixed ushr 8 and 255) < 30)
    }
    @Test fun pickingDoesNotChangePixelsOrHistory() {
        val board = MixingBoard(60, 40); val before = board.snapshot()
        board.begin(20f, 20f, MixingBoard.Tool.PICK, 0xffff0000.toInt(), 10f)
        board.move(30f, 20f); assertFalse(board.finish())
        assertArrayEquals(before, board.snapshot()); assertFalse(board.canUndo)
    }
    @Test fun historyIsBoundedAndNewStrokeClearsRedo() {
        val board = MixingBoard(120, 40)
        repeat(16) { i ->
            board.begin(i * 7f, 20f, MixingBoard.Tool.PAINT, 0xff111111.toInt(), 3f); board.finish()
        }
        var count = 0; while (board.undo()) count++
        assertEquals(12, count)
        board.begin(40f, 10f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 4f); board.finish()
        assertFalse(board.canRedo)
    }
    @Test fun restoredBoardOwnsItsPixels() {
        val board = MixingBoard(40, 40)
        val data = IntArray(1600) { 0xffff0000.toInt() }
        board.restore(data); data.fill(0)
        assertEquals(0xffff0000.toInt(), board.sample(-10f, 300f))
        assertFalse(board.canUndo)
    }
}
