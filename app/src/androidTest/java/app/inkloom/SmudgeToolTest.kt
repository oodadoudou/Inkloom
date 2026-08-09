package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.LayerIds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmudgeToolTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 200, 200); block() }
        }
        return result
    }

    private fun event(time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val e = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(e) } } finally { e.recycle() }
    }

    @Test fun draggingBlendMixesExistingPigmentWithoutAddingAStroke() {
        var removed = 0
        var replacements = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 400f
            activeLayerId = LayerIds.INK
            setDocumentStrokes(
                listOf(
                    InkStroke(id = "red", colorArgb = 0xFFFF0000, width = 30f, layerId = LayerIds.INK, points = listOf(StrokePoint(.3f, .49f), StrokePoint(.7f, .49f))),
                    InkStroke(id = "blue", colorArgb = 0xFF0000FF, width = 30f, layerId = LayerIds.INK, points = listOf(StrokePoint(.3f, .51f), StrokePoint(.7f, .51f))),
                ),
                "blend-page",
            )
            onStrokesReplaced = { deleted, changed, _ -> removed = deleted.size; replacements = changed.size }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 100f, 100f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 60L, MotionEvent.ACTION_UP, 140f, 100f)
        assertEquals(2, removed)
        assertEquals("Blend edits the two existing strokes; it must not paint a third stroke", 2, replacements)
    }

    @Test fun blendingBlankPaperDoesNotCreatePigment() {
        var committed = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            onStrokeCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 20f, 100f)
        for (step in 1..16) send(view, step * 10L, MotionEvent.ACTION_MOVE, 20f + step * 10f, 100f)
        send(view, 180L, MotionEvent.ACTION_UP, 180f, 100f)
        assertEquals("A blend tool cannot add pigment to blank paper", 0, committed)
    }
}
