package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.document.SpecialRulerMode
import app.inkloom.document.SpecialRulerState
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasSpecialRulerTest {
    @Test fun parallelRulerConstrainsStylusWithoutLosingPressure() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000)
            val view = canvas(surface).apply {
                specialRulerState = SpecialRulerState(SpecialRulerMode.PARALLEL, editing = false, angleDegrees = 0f)
            }

            view.sendStylus(MotionEvent.ACTION_DOWN, 100f, 330f, 10L, .3f)
            view.sendStylus(MotionEvent.ACTION_MOVE, 500f, 700f, 20L, .6f)
            view.sendStylus(MotionEvent.ACTION_UP, 900f, 150f, 30L, .9f)

            assertTrue(Color.alpha(surface.pixelAt(500, 330)) > 0)
            assertTrue(Color.alpha(surface.pixelAt(850, 330)) > 0)
            assertEquals(0, Color.alpha(surface.pixelAt(500, 700)))
        }
    }

    @Test fun stylusMovesRulerHandleWithoutCommittingInk() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var committed = 0
            val changes = mutableListOf<SpecialRulerState>()
            val view = canvas().apply {
                specialRulerState = SpecialRulerState.defaultFor(SpecialRulerMode.RADIAL)
                onSpecialRulerChanged = changes::add
                onStrokeCommitted = { committed++ }
            }

            view.sendStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 100L)
            view.sendStylus(MotionEvent.ACTION_MOVE, 650f, 350f, 120L)
            view.sendStylus(MotionEvent.ACTION_UP, 650f, 350f, 140L)

            assertEquals(0, committed)
            assertEquals(.65f, changes.last().centerX, .001f)
            assertEquals(.35f, changes.last().centerY, .001f)
        }
    }

    @Test fun twoFingerNavigationCancelsAnInProgressRulerEdit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = canvas().apply { specialRulerState = SpecialRulerState.defaultFor(SpecialRulerMode.ISOMETRIC) }
            var scale = 1f
            view.onViewportScale = { factor, _, _ -> scale *= factor }

            view.send(0L, MotionEvent.ACTION_DOWN, finger(0, 500f, 500f))
            view.send(10L, MotionEvent.ACTION_MOVE, finger(0, 600f, 600f))
            view.send(20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger(0, 600f, 600f), finger(1, 800f, 600f))
            view.send(40L, MotionEvent.ACTION_MOVE, finger(0, 500f, 600f), finger(1, 900f, 600f))
            view.send(60L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger(0, 500f, 600f), finger(1, 900f, 600f))
            view.send(80L, MotionEvent.ACTION_UP, finger(0, 500f, 600f))

            assertEquals(.5f, view.specialRulerState.centerX, .001f)
            assertEquals(.5f, view.specialRulerState.centerY, .001f)
            assertTrue(scale > 1.5f)
        }
    }

    private fun canvas(surface: PixelTileSurface = PixelTileSurface(1_000, 1_000)) = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        layout(0, 0, 1_000, 1_000)
        tool = EditorTool.PEN
        fingerDrawingEnabled = false
        activeLayerId = "paint"
        baseWidth = 18f
        setRasterLayerSurfaces(mapOf("paint" to surface))
        installV5TestInk(size = 18f)
    }

    private fun InkCanvasView.sendStylus(action: Int, x: Float, y: Float, time: Long, pressure: Float = .7f) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = .01f; touchMajor = 1f }
        send(time, action, properties to coordinates, source = InputDevice.SOURCE_STYLUS)
    }

    private fun finger(id: Int, x: Float, y: Float): Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords> =
        MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } to
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .7f; size = .01f; touchMajor = 8f }

    private fun InkCanvasView.send(time: Long, action: Int, vararg points: Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords>, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        MotionEvent.obtain(
            0L,
            time,
            action,
            points.size,
            points.map { it.first }.toTypedArray(),
            points.map { it.second }.toTypedArray(),
            0,
            0,
            1f,
            1f,
            0,
            0,
            source,
            0,
        ).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }
}
