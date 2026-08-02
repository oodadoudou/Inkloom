package app.inkloom

import android.content.ClipboardManager
import android.content.Intent
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokeClipboardCodec
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkClipboardTest {
    @Test fun copyAndPasteUseSystemClipboardAndOneReversibleEdit() {
        withFocusedApp { instrumentation ->
            val source = sourceStroke()
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(source, edits)
            view.selectSourceStroke()

            assertTrue(view.copySelectionToClipboard())
            val clipboard = instrumentation.targetContext.getSystemService(ClipboardManager::class.java)
            assertTrue(clipboard.primaryClipDescription!!.hasMimeType(StrokeClipboardCodec.MIME_TYPE))
            assertTrue(view.pasteSelectionFromClipboard())

            assertEquals(1, edits.size)
            assertTrue(edits[0].removedIds.isEmpty())
            val pasted = edits[0].replacements.single()
            assertNotEquals(source.id, pasted.id)
            assertEquals("target-layer", pasted.layerId)
            assertEquals(source.renderSeed, pasted.renderSeed)
            assertEquals(.43f, pasted.points.first().x, .001f)

            view.undo()
            assertEquals(2, edits.size)
            assertEquals(listOf(pasted.id), edits[1].removedIds)
            assertTrue(edits[1].replacements.isEmpty())

            view.setProtectedLayerIds(setOf("target-layer"))
            assertFalse(view.pasteSelectionFromClipboard())
            view.tool = EditorTool.PEN
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, 100f, 60L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 200f, 200f, 70L)
            assertEquals(2, edits.size)
        }
    }

    @Test fun cutWritesClipboardBeforeRemovingAndCanBeUndone() {
        withFocusedApp { instrumentation ->
            val source = sourceStroke()
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(source, edits)
            view.selectSourceStroke()

            assertTrue(view.cutSelectionToClipboard())
            assertEquals(listOf(source.id), edits.single().removedIds)
            val clipboard = instrumentation.targetContext.getSystemService(ClipboardManager::class.java)
            val payload = StrokeClipboardCodec.decode(clipboard.primaryClip!!.getItemAt(0).text)!!
            assertEquals(source, payload.strokes.single())

            view.undo()
            assertEquals(listOf(source), edits.last().replacements)
        }
    }

    private fun withFocusedApp(block: (android.app.Instrumentation) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync { block(instrumentation) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun configuredView(stroke: InkStroke, edits: MutableList<CapturedEdit>): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.SELECT
            activeLayerId = "target-layer"
            setDocumentStrokes(listOf(stroke), "clipboard-page")
            onStrokesReplaced = { removed, replacements, indices -> edits += CapturedEdit(removed, replacements, indices) }
        }
    }

    private fun sourceStroke() = InkStroke(
        id = "source",
        renderSeed = 31_415,
        layerId = "source-layer",
        width = 17f,
        brushId = "imported-sut",
        points = listOf(StrokePoint(.4f, .45f, pressure = .3f), StrokePoint(.6f, .55f, pressure = .9f)),
    )

    private fun InkCanvasView.selectSourceStroke() {
        dispatchStylus(MotionEvent.ACTION_DOWN, 300f, 300f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 700f, 300f, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 700f, 700f, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 300f, 700f, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, 300f, 300f, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private data class CapturedEdit(val removedIds: List<String>, val replacements: List<InkStroke>, val indices: List<Int>)
}
