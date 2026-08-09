package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.PerspectiveGuideMode
import app.inkloom.document.PerspectiveGuideState
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class InkCanvasPerspectiveGuideTest {
    @Test fun drawingAssistLocksStylusStrokeToOnePerspectiveDirection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var committed: InkStroke? = null
            val view = canvas().apply {
                perspectiveGuideState = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.ONE_POINT).copy(editing = false)
                onStrokeCommitted = { committed = it }
            }

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 200f, 800f, 10L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 250f, 735f, 20L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 700f, 900f, 30L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 900f, 900f, 40L)

            val points = requireNotNull(committed).points
            assertTrue(points.size >= 2)
            val expectedSlope = (.42f - .8f) / (.5f - .2f)
            points.drop(1).forEach { point ->
                val actualSlope = (point.y - .8f) / (point.x - .2f).takeUnless { abs(it) < .0001f }!!
                assertEquals(expectedSlope, actualSlope, .03f)
            }
        }
    }

    @Test fun stylusCanMoveAnEditableVanishingPointWithoutLeavingInk() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var committed = 0
            val changes = mutableListOf<PerspectiveGuideState>()
            val view = canvas().apply {
                perspectiveGuideState = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.ONE_POINT).copy(editing = true)
                onPerspectiveGuideChanged = changes::add
                onStrokeCommitted = { committed++ }
            }

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 500f, 420f, 100L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 620f, 360f, 120L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 620f, 360f, 140L)

            assertEquals(0, committed)
            assertEquals(.62f, changes.last().points[0].x, .001f)
            assertEquals(.36f, changes.last().points[0].y, .001f)
        }
    }

    private fun canvas() = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        layout(0, 0, 1_000, 1_000)
        tool = EditorTool.PEN
        fingerDrawingEnabled = false
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
    }
}
