package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.document.RulerGuide
import app.inkloom.document.RulerGuideState
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasRulerTest {
    @Test fun stylusStrokeIsConstrainedToRulerWithoutLosingPressure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000)
            val view = rulerCanvas(surface)

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 100f, 200f, 10L, .4f)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 500f, 800f, 20L, .7f)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 900f, 300f, 30L, .9f)

            assertTrue(Color.alpha(surface.pixelAt(500, 500)) > 0)
            assertTrue(Color.alpha(surface.pixelAt(850, 500)) > 0)
            assertEquals(0, Color.alpha(surface.pixelAt(500, 800)))
        }
    }

    @Test fun fingerMovesRulerAndRotationHandleUsesAngleSnap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val changes = mutableListOf<RulerGuideState>()
            val view = rulerCanvas().apply { onRulerGuideChanged = changes::add }

            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, 500f, 500f, 100L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_MOVE, 600f, 600f, 120L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 600f, 600f, 140L)
            assertEquals(.6f, changes.last().centerX, .001f)
            assertEquals(.6f, changes.last().centerY, .001f)

            val handle = RulerGuide.rotationHandle(view.rulerGuide, 1_000f, 1_000f)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, handle.x, handle.y, 200L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_MOVE, 900f, 775f, 220L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 900f, 775f, 240L)
            assertEquals(30f, changes.last().angleDegrees, .001f)
        }
    }

    @Test fun recentStylusContactPreventsPalmFromMovingRuler() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val changes = mutableListOf<RulerGuideState>()
            val view = rulerCanvas().apply { onRulerGuideChanged = changes::add }
            val original = view.rulerGuide

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 200f, 200f, 1_000L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_CANCEL, 200f, 200f, 1_010L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, 500f, 500f, 1_020L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_MOVE, 700f, 700f, 1_030L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 700f, 700f, 1_040L)

            assertEquals(original, view.rulerGuide)
            assertTrue(changes.isEmpty())
        }
    }

    private fun rulerCanvas(surface: PixelTileSurface = PixelTileSurface(1_000, 1_000)) = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        layout(0, 0, 1_000, 1_000)
        tool = EditorTool.PEN
        fingerDrawingEnabled = false
        rulerEnabled = true
        rulerGuide = RulerGuideState(centerX = .5f, centerY = .5f, angleDegrees = 0f, snapDegrees = 15f)
        activeLayerId = "paint"
        baseWidth = 18f
        setRasterLayerSurfaces(mapOf("paint" to surface))
        installV5TestInk(size = 18f)
    }

    private fun InkCanvasView.send(toolType: Int, action: Int, x: Float, y: Float, time: Long, pressure: Float = .7f) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = .01f; touchMajor = if (toolType == MotionEvent.TOOL_TYPE_FINGER) 8f else 1f }
        MotionEvent.obtain(
            10L, time, action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0,
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,
            0,
        ).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS && action == MotionEvent.ACTION_UP) {
            awaitV5StrokeWorkerIdleForTest()
        }
    }
}
