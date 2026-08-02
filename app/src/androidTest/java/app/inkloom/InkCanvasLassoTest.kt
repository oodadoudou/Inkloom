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
class InkCanvasLassoTest {
    @Test fun partialLassoSplitsAndDeletesOnlyTheEnclosedSegment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("original")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)

            view.drawSquareLasso()

            assertEquals(1, edits.size)
            assertEquals(listOf("original"), edits[0].removedIds)
            assertEquals(3, edits[0].replacements.size)
            assertEquals(listOf(0, 1, 2), edits[0].indices)
            val enclosed = edits[0].replacements[1]
            assertEquals(.2f, enclosed.points.first().x, .001f)
            assertEquals(.8f, enclosed.points.last().x, .001f)

            view.deleteSelection()

            assertEquals(2, edits.size)
            assertEquals(listOf(enclosed.id), edits[1].removedIds)
            assertTrue(edits[1].replacements.isEmpty())

            view.undo()
            assertEquals(3, edits.size)
            assertEquals(listOf(enclosed), edits[2].replacements)
            assertEquals(listOf(1), edits[2].indices)

            view.undo()
            assertEquals(4, edits.size)
            assertEquals(edits[0].replacements.map(InkStroke::id), edits[3].removedIds)
            assertEquals(listOf(original), edits[3].replacements)
        }
    }

    @Test fun partialLassoNeverSplitsOrSelectsAProtectedStroke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("locked")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)
            view.setProtectedStrokeIds(setOf(original.id))

            view.drawSquareLasso()
            view.deleteSelection()

            assertTrue(edits.isEmpty())
        }
    }

    private fun configuredView(stroke: InkStroke, edits: MutableList<CapturedEdit>): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.SELECT
            setDocumentStrokes(listOf(stroke), "document:page")
            onStrokesReplaced = { removed, replacements, indices -> edits += CapturedEdit(removed, replacements, indices) }
        }
    }

    private fun horizontalStroke(id: String) = InkStroke(
        id = id,
        width = 5f,
        points = listOf(StrokePoint(.1f, .5f, pressure = .2f, timeMs = 10L), StrokePoint(.9f, .5f, pressure = .9f, timeMs = 90L)),
    )

    private fun InkCanvasView.drawSquareLasso() {
        dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 200f, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 800f, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 200f, 800f, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, 200f, 200f, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private data class CapturedEdit(
        val removedIds: List<String>,
        val replacements: List<InkStroke>,
        val indices: List<Int>,
    )
}
