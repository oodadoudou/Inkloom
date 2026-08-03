package app.inkloom

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasContinuousPlotTest {
    @Test fun holdingStylusDepositsStationaryPointsUntilLift() {
        withAttachedCanvas { view ->
            val committed = mutableListOf<InkStroke>()
            view.onStrokeCommitted = { committed += it }
            val downTime = SystemClock.uptimeMillis()

            sendStylus(view, downTime, downTime, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(125L)
            val upTime = SystemClock.uptimeMillis()
            sendStylus(view, downTime, upTime, MotionEvent.ACTION_UP)

            assertEquals(1, committed.size)
            val stroke = committed.single()
            assertTrue(stroke.continuousPlot)
            assertTrue("A 125 ms hold should add multiple 32 ms plot samples", stroke.points.size >= 3)
            assertTrue(stroke.points.all { it.x == .5f && it.y == .5f })
            assertTrue(stroke.points.zipWithNext().all { (left, right) -> right.timeMs >= left.timeMs })
        }
    }

    @Test fun cancellationStopsTimerAndDiscardsThePendingStroke() {
        withAttachedCanvas { view ->
            val committed = mutableListOf<InkStroke>()
            view.onStrokeCommitted = { committed += it }
            val downTime = SystemClock.uptimeMillis()

            sendStylus(view, downTime, downTime, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(70L)
            sendStylus(view, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL)
            SystemClock.sleep(70L)

            assertTrue(committed.isEmpty())
        }
    }

    private fun withAttachedCanvas(block: (InkCanvasView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1000, 1000)
                tool = EditorTool.PEN
                fingerDrawingEnabled = false
                brushContinuousPlot = true
            }
            activity.addContentView(view, ViewGroup.LayoutParams(1000, 1000))
        }
        instrumentation.waitForIdleSync()
        try {
            block(view)
        } finally {
            instrumentation.runOnMainSync {
                (view.parent as? ViewGroup)?.removeView(view)
                activity.finish()
            }
        }
    }

    private fun sendStylus(view: InkCanvasView, downTime: Long, eventTime: Long, action: Int) {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = 500f
            y = 500f
            pressure = .65f
            size = .05f
        }
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
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
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
