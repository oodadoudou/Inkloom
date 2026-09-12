package app.inkloom.color

import org.junit.Assert.*
import org.junit.Test

class MixingBoardTest {
    @Test fun pressureControlsContinuousPaintWidthAndPreviewMatchesPublication() {
        val board = MixingBoard(160, 100)
        board.begin(10f, 20f, MixingBoard.Tool.PAINT, 0xff115588.toInt(), 14f, .1f)
        board.move(145f, 20f, .1f); board.finish()
        board.begin(10f, 70f, MixingBoard.Tool.PAINT, 0xffff4400.toInt(), 14f, 1f)
        board.move(145f, 70f, 1f)
        val preview = board.snapshot()
        board.finish(); assertArrayEquals(preview, board.snapshot())
        val lightWidth = (0..40).count { board.sample(70f, it.toFloat()) ushr 24 > 10 }
        val firmWidth = (45..99).count { board.sample(70f, it.toFloat()) ushr 24 > 10 }
        assertTrue("Light contact is visibly finer without becoming disconnected", lightWidth in 3..12)
        assertTrue("Full pressure uses a broader footprint", firmWidth > lightWidth * 2)
        for (x in 15..140) assertTrue(board.sample(x.toFloat(), 20f) ushr 24 > 200)
    }

    @Test fun overlappingPaintPreviewContainsTheBackdropAndIsExactAtCommit() {
        val board = MixingBoard(120, 80)
        board.begin(15f, 30f, MixingBoard.Tool.PAINT, 0xff0000ff.toInt(), 16f)
        board.move(100f, 30f); board.finish()
        val backdrop = board.snapshot()
        board.begin(60f, 15f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 16f)
        board.move(60f, 65f)
        val preview = board.snapshot()
        assertEquals("Unchanged backdrop is visible during new paint", 0xff0000ff.toInt(), board.sample(25f, 30f))
        board.finish(); assertArrayEquals(preview, board.snapshot())
        board.undo(); assertArrayEquals(backdrop, board.snapshot())
        board.redo(); assertArrayEquals(preview, board.snapshot())
        val restored = MixingBoard(120, 80)
        restored.restore(preview); assertArrayEquals(preview, restored.snapshot())
    }

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
    @Test fun transparentPaperDoesNotTintPigmentAndMixingRespectsAlpha() {
        val board = MixingBoard(60, 40)
        assertEquals(0, board.sample(20f, 20f))
        board.begin(20f, 20f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 8f)
        board.finish()
        val red = board.sample(26f, 20f)
        assertTrue(red ushr 24 in 1..254)
        assertEquals(0x00ff0000, red and 0x00ffffff)
        assertEquals(0x80ff0000.toInt(), MixingBoard.sourceOver(0, 0xffff0000.toInt(), .5f))
        val snapshot = board.snapshot()
        board.clear(); assertTrue(board.snapshot().all { it == 0 })
        board.undo(); assertArrayEquals(snapshot, board.snapshot())
    }

}
