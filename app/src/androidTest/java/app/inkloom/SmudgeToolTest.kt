package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun draggingTheSmudgeToolCommitsSampledColorStrokes() {
        var committed = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            pagePaperColor = Color.rgb(200, 30, 30) // red paper -> smudge picks up red
            onStrokeCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 100f, 100f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 60L, MotionEvent.ACTION_UP, 140f, 100f)
        assertEquals("One uniform-color segment should be persisted exactly once", 1, committed)
    }

    @Test fun smudgingAcrossAGradientStillCommitsContinuousSegments() {
        var committed = 0
        val background = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until width) {
                val shade = x.coerceIn(0, 255)
                for (y in 0 until height) setPixel(x, y, Color.rgb(shade, 40, 255 - shade))
            }
        }
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            setPageBackground(background)
            onStrokeCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 20f, 100f)
        for (step in 1..16) send(view, step * 10L, MotionEvent.ACTION_MOVE, 20f + step * 10f, 100f)
        send(view, 180L, MotionEvent.ACTION_UP, 180f, 100f)
        assertTrue("Changing sampled colors must not discard the gesture", committed > 0)
        background.recycle()
    }
}
