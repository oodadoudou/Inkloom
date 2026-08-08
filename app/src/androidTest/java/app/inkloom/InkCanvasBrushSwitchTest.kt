package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasBrushSwitchTest {
    @Test
    fun everyBrushAndToolSwitchStartsAUsableInputStream() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val strokes = mutableListOf<InkStroke>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            onStrokeCommitted = strokes::add
        }
        val first = brush("first", "First")
        val second = brush("second", "Second")

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), first, 16f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 180f)

            // Reproduce a palette tap interrupting the previous stream before its UP arrives.
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 420f, 20L))
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), second, 22f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 30L, 520f)

            view.applyBrushConfiguration(EditorTool.HIGHLIGHTER, AppSettings(), null, 28f, .35f, Color.YELLOW, EraserPreset.NORMAL_FREE)
            drawStroke(view, 60L, 760f)
        }

        assertEquals(listOf("first", "second", "system-highlighter"), strokes.map(InkStroke::brushId))
    }

    private fun brush(id: String, name: String) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.IMAGE,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L,
        report = emptyList(),
    )

    private fun drawStroke(view: InkCanvasView, startTime: Long, y: Float) {
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, y, startTime))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, y, startTime + 4))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, y, startTime + 8))
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .75f; size = .05f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }
}
