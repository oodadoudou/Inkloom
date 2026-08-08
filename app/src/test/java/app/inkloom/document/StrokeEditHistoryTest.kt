package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrokeEditHistoryTest {
    private val back = InkStroke(id = "back", points = listOf(StrokePoint(.1f, .1f)))
    private val middle = InkStroke(id = "middle", points = listOf(StrokePoint(.2f, .2f)))
    private val front = InkStroke(id = "front", points = listOf(StrokePoint(.3f, .3f)))

    @Test
    fun localSplitAndUndoPreserveExactZOrder() {
        val before = listOf(back, middle, front)
        val left = middle.copy(id = "left", points = listOf(StrokePoint(.15f, .2f), StrokePoint(.18f, .2f)))
        val right = middle.copy(id = "right", points = listOf(StrokePoint(.22f, .2f), StrokePoint(.25f, .2f)))
        val after = listOf(back, left, right, front)
        val edit = StrokeEdits.difference(before, after)!!

        assertEquals(after, StrokeEdits.apply(before, edit.before, edit.after))
        assertEquals(before, StrokeEdits.apply(after, edit.after, edit.before))
        assertEquals(listOf(1), edit.before.map(PositionedStroke::index))
        assertEquals(listOf(1, 2), edit.after.map(PositionedStroke::index))
    }

    @Test
    fun propertyChangeWithTheSameIdIsStillAnEdit() {
        val moved = middle.copy(points = listOf(StrokePoint(.7f, .7f)))
        val edit = StrokeEdits.difference(listOf(middle), listOf(moved))!!
        assertEquals(listOf(middle), edit.before.map(PositionedStroke::stroke))
        assertEquals(listOf(moved), edit.after.map(PositionedStroke::stroke))
    }

    @Test
    fun historyMovesEditsBetweenUndoAndRedoAndClearsRedoOnNewInput() {
        val history = StrokeEditHistory()
        val first = StrokeEdits.difference(emptyList(), listOf(back))!!
        val second = StrokeEdits.difference(listOf(back), listOf(back, middle))!!
        history.record(first)
        history.record(second)

        assertEquals(second, history.takeUndo())
        assertEquals(second, history.takeRedo())
        history.takeUndo()
        history.record(StrokeEdits.difference(listOf(back), listOf(back, front))!!)
        assertNull(history.takeRedo())
    }

    @Test
    fun emptyDifferenceDoesNotCreateHistory() {
        assertNull(StrokeEdits.difference(listOf(back), listOf(back)))
        val history = StrokeEditHistory()
        assertNull(history.takeUndo())
    }
}
