package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasHoverCursorTest {
    @Test
    fun stylusHoverUsesCurrentBrushDiameterAndTracksZoomedCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            tool = EditorTool.PEN
            baseWidth = 160f
            viewportScale = 2f
            viewportPanX = 100f
            viewportPanY = -50f
            layout(0, 0, 1600, 1200)
        }

        instrumentation.runOnMainSync {
            val hover = stylusHover(900f, 550f)
            assertTrue(view.onHoverEvent(hover))
            hover.recycle()
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(1f, state[0], 0f)
        assertEquals(900f, state[1], .01f)
        assertEquals(550f, state[2], .01f)
        assertEquals(160f, state[3], .01f)

        view.baseWidth = 320f
        assertEquals(320f, view.hoverCursorSnapshot()[3], .01f)
    }

    private fun stylusHover(x: Float, y: Float): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 0f
            size = 0f
        })
        return MotionEvent.obtain(
            1L, 2L, MotionEvent.ACTION_HOVER_MOVE, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }
}
