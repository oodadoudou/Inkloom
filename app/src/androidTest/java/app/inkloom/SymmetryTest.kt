package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.model.SymmetryMode
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymmetryTest {
    @Test fun horizontalSymmetryPaintsOriginalAndMirrorInOneRasterCommit() {
        val surface = PixelTileSurface(400, 400)
        var commits = 0
        val view = canvas(surface) {
            symmetryMode = SymmetryMode.HORIZONTAL
            onRasterLayerCommitted = { commits++ }
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 150f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 150f, 100f)

        assertEquals(1, commits)
        assertTrue(Color.alpha(surface.pixelAt(125, 100)) > 0)
        assertTrue(Color.alpha(surface.pixelAt(125, 300)) > 0)
        assertEquals(0, Color.alpha(surface.pixelAt(125, 200)))
    }

    @Test fun noSymmetryPublishesOnlyTheAuthoredTrajectory() {
        val surface = PixelTileSurface(400, 400)
        val view = canvas(surface) {}

        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 150f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 150f, 100f)

        assertTrue(Color.alpha(surface.pixelAt(125, 100)) > 0)
        assertEquals(0, Color.alpha(surface.pixelAt(125, 300)))
    }

    @Test fun radialSymmetryUsesDocumentPixelAspectRatio() {
        val surface = PixelTileSurface(100, 200)
        val view = canvas(surface) {
            pageModelWidth = 100f
            pageModelHeight = 200f
            symmetryMode = SymmetryMode.RADIAL_4
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, 300f, 200f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 320f, 200f)
        send(view, 40L, MotionEvent.ACTION_UP, 320f, 200f)

        assertTrue(Color.alpha(surface.pixelAt(75, 100)) > 0)
        assertTrue(Color.alpha(surface.pixelAt(50, 125)) > 0)
        assertTrue(Color.alpha(surface.pixelAt(25, 100)) > 0)
        assertTrue(Color.alpha(surface.pixelAt(50, 75)) > 0)
    }

    private fun canvas(surface: PixelTileSurface, block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                tool = EditorTool.PEN
                fingerDrawingEnabled = true
                activeLayerId = "paint"
                baseWidth = 18f
                setRasterLayerSurfaces(mapOf("paint" to surface))
                block()
                installV5TestInk(size = baseWidth)
            }
        }
        return result
    }

    private fun event(time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val event = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }
}
