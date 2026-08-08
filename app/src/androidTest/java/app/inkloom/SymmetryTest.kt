package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerIds
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.model.SymmetryMode
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymmetryTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 400, 400); block() }
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

    @Test fun horizontalSymmetryCommitsMirrorStrokes() {
        val committed = mutableListOf<InkStroke>()
        val view = canvas {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
            symmetryMode = SymmetryMode.HORIZONTAL
            onStrokeCommitted = committed::add
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 150f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 150f, 100f)
        // Original + 1 horizontal mirror = 2 strokes.
        assertEquals("Horizontal symmetry should commit the original plus one mirror", 2, committed.size)
        assertEquals(.25f, committed[1].points.first().x, .01f)
        assertEquals(.75f, committed[1].points.first().y, .01f)
    }

    @Test fun noSymmetryCommitsASingleStroke() {
        var committed = 0
        val view = canvas {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
            onStrokeCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 200f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 150f, 200f)
        send(view, 40L, MotionEvent.ACTION_UP, 150f, 200f)
        assertEquals(1, committed)
    }

    @Test fun radialSymmetryUsesPhysicalPageAspectRatio() {
        val committed = mutableListOf<InkStroke>()
        val view = canvas {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
            symmetryMode = SymmetryMode.RADIAL_4
            pageModelWidth = 100f
            pageModelHeight = 200f
            onStrokeCommitted = committed::add
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 300f, 200f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 320f, 200f)
        send(view, 40L, MotionEvent.ACTION_UP, 320f, 200f)
        assertEquals(4, committed.size)
        assertEquals(.5f, committed[1].points.first().x, .01f)
        assertEquals(.625f, committed[1].points.first().y, .01f)
    }
}
