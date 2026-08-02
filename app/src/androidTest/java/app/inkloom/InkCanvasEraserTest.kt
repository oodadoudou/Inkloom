package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasEraserTest {
    @Test
    fun localEraserCommitsOneAtomicEditAndUndoRestoresOriginal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("original", .5f)
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)
            view.localEraser = true

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 500f, 20L)

            assertEquals(1, edits.size)
            assertEquals(listOf("original"), edits[0].removedIds)
            assertEquals(2, edits[0].replacements.size)
            assertEquals(listOf(0, 1), edits[0].indices)
            assertTrue(edits[0].replacements.first().points.last().x < .5f)
            assertTrue(edits[0].replacements.last().points.first().x > .5f)

            view.undo()
            assertEquals(2, edits.size)
            assertEquals(edits[0].replacements.map(InkStroke::id), edits[1].removedIds)
            assertEquals(listOf(original), edits[1].replacements)
            assertEquals(listOf(0), edits[1].indices)

            view.redo()
            assertEquals(3, edits.size)
            assertEquals(listOf("original"), edits[2].removedIds)
            assertEquals(edits[0].replacements, edits[2].replacements)
        }
    }

    @Test
    fun eraserCancellationRestoresTheGestureWithoutPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(horizontalStroke("original", .5f), edits)
            view.localEraser = true

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_CANCEL, 500f, 500f, 20L)
            view.undo()

            assertTrue(edits.isEmpty())
        }
    }

    @Test
    fun wholeStrokeEraserRemovesEveryIntersectingUnlockedStroke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val first = horizontalStroke("first", .48f)
            val second = horizontalStroke("second", .52f)
            val locked = horizontalStroke("locked", .5f)
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(listOf(first, second, locked), edits)
            view.setProtectedStrokeIds(setOf(locked.id))
            view.localEraser = false

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 500f, 20L)

            assertEquals(1, edits.size)
            assertEquals(setOf("first", "second"), edits.single().removedIds.toSet())
            assertTrue(edits.single().replacements.isEmpty())
        }
    }

    @Test
    fun undoDoesNotModifyALayerThatBecameProtected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(horizontalStroke("original", .5f), edits)
            view.localEraser = true

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 500f, 20L)
            assertEquals(1, edits.size)

            view.setProtectedStrokeIds(edits.single().replacements.mapTo(mutableSetOf(), InkStroke::id))
            view.undo()

            assertEquals(1, edits.size)
        }
    }

    private fun configuredView(stroke: InkStroke, edits: MutableList<CapturedEdit>) = configuredView(listOf(stroke), edits)

    private fun configuredView(strokes: List<InkStroke>, edits: MutableList<CapturedEdit>): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1000, 1000)
            tool = EditorTool.ERASER
            eraserWidth = 160f
            setDocumentStrokes(strokes, "document:page")
            onStrokesReplaced = { removed, replacements, indices -> edits += CapturedEdit(removed, replacements, indices) }
        }
    }

    private fun horizontalStroke(id: String, y: Float) = InkStroke(
        id = id,
        width = 5f,
        points = listOf(StrokePoint(.1f, y), StrokePoint(.9f, y)),
    )

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        stylusEvent(action, x, y, time).also { event ->
            try {
                onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .8f
            size = .01f
            touchMajor = 1f
        }
        return MotionEvent.obtain(
            10L,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
    }

    private data class CapturedEdit(
        val removedIds: List<String>,
        val replacements: List<InkStroke>,
        val indices: List<Int>,
    )
}
