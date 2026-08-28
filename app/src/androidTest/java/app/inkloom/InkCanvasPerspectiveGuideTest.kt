package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.PerspectiveGuides
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.document.PerspectiveGuideMode
import app.inkloom.document.PerspectiveGuideState
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasPerspectiveGuideTest {
    @Test fun drawingAssistLocksStylusStrokeToOnePerspectiveDirection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000)
            val guide = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.ONE_POINT).copy(editing = false)
            val view = canvas(surface).apply { perspectiveGuideState = guide }

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 200f, 800f, 10L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 250f, 735f, 20L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 700f, 900f, 30L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 900f, 900f, 40L)

            val constraint = requireNotNull(PerspectiveGuides.constraintAt(guide, .2f, .8f, .25f, .735f, 1_000f, 1_000f))
            val projected = constraint.apply(.7f, .9f)
            assertTrue(Color.alpha(surface.pixelAt((projected.x * 1_000).toInt(), (projected.y * 1_000).toInt())) > 0)
            assertEquals(0, Color.alpha(surface.pixelAt(700, 900)))
        }
    }

    @Test fun stylusCanMoveAnEditableVanishingPointWithoutLeavingInk() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000)
            val changes = mutableListOf<PerspectiveGuideState>()
            val view = canvas(surface).apply {
                perspectiveGuideState = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.ONE_POINT).copy(editing = true)
                onPerspectiveGuideChanged = changes::add
            }

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 500f, 420f, 100L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 620f, 360f, 120L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 620f, 360f, 140L)

            assertEquals(0, surface.snapshot().tileCount)
            assertEquals(.62f, changes.last().points[0].x, .001f)
            assertEquals(.36f, changes.last().points[0].y, .001f)
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

    private fun InkCanvasView.send(toolType: Int, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .7f
            size = .01f
            touchMajor = if (toolType == MotionEvent.TOOL_TYPE_FINGER) 8f else 1f
        }
        MotionEvent.obtain(
            10L,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,
            0,
        ).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS && action == MotionEvent.ACTION_UP) {
            awaitV5StrokeWorkerIdleForTest()
        }
    }
}
