package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaintCapabilityTerminalOutcomeTest {
    @Test fun emptyProductionPaintResultIsRejectedWithAnExplicitTerminalReason() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var commits = 0
        val rejections = mutableListOf<String>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 600, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to PixelTileSurface(600, 600)))
            installV5TestInk(size = 32f, opacity = 0f, color = Color.BLACK)
            onRasterLayerCommitted = { commits++ }
            onPaintCapabilityRejected = { rejections += it }
        }

        instrumentation.runOnMainSync {
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 100f, 300f, 1L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 300f, 300f, 2L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 500f, 300f, 3L))
        }
        view.awaitV5StrokeWorkerIdleForTest()
        instrumentation.runOnMainSync { }

        assertEquals(0, commits)
        assertEquals(
            listOf("UNSUPPORTED_WITH_EXPLICIT_REASON: EMPTY_RENDER_RESULT"),
            rejections,
        )
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .8f
            size = .1f
        })
        return MotionEvent.obtain(
            1L,
            time,
            action,
            1,
            properties,
            coordinates,
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
}
