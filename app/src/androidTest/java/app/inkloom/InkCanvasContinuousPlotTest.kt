package app.inkloom

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerIds
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasContinuousPlotTest {
    @Test fun holdingStylusDepositsStationaryPointsUntilLift() {
        withAttachedCanvas { view, surface ->
            val downTime = SystemClock.uptimeMillis()

            sendStylus(view, downTime, downTime, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(125L)
            val upTime = SystemClock.uptimeMillis()
            sendStylus(view, downTime, upTime, MotionEvent.ACTION_UP)

            assertEquals(1L, surface.revision)
            assertTrue(surface.tileCount > 0)
            val samples = view.v5LastCommittedNormalizedPoints()
            assertTrue("A 125 ms hold should add multiple 32 ms V5 plot samples", samples.size >= 3)
            assertTrue(samples.all { (x, y) -> x == .5f && y == .5f })
        }
    }

    @Test fun delayedHistoryAfterStationaryTimerMovesAndCommitsOnce() {
        withAttachedCanvas { view, surface ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val downTime = SystemClock.uptimeMillis()
            sendStylus(view, downTime, downTime, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(80L)
            // A UI timer can overtake batched device input. Deliver older historical positions
            // through MotionEvent itself instead of synthesizing a controller-only command.
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                x = 600f; y = 500f; pressure = .4f; size = .05f
                setAxisValue(MotionEvent.AXIS_TILT, .3f)
            }
            val event = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE,
                1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_STYLUS, 0)
            coordinates.x = 700f
            coordinates.pressure = .8f
            event.addBatch(downTime + 20, arrayOf(coordinates), 0)
            try {
                instrumentation.runOnMainSync { view.onTouchEvent(event) }
            } finally {
                event.recycle()
            }
            sendStylus(view, downTime, downTime + 30, MotionEvent.ACTION_UP)
            assertEquals(1L, surface.revision)
            val points = view.v5LastCommittedNormalizedPoints()
            assertTrue("Historical motion must survive timer interleaving", .6f to .5f in points)
            assertTrue("Latest motion must survive timer interleaving", .7f to .5f in points)
            assertTrue("Actual moving ink must commit", surface.pixelAt(650, 500) != 0)
            SystemClock.sleep(80L)
            assertEquals("No timer publication after UP", 1L, surface.revision)
            val next = SystemClock.uptimeMillis()
            sendStylus(view, next, next, MotionEvent.ACTION_DOWN)
            sendStylus(view, next, next + 1, MotionEvent.ACTION_UP)
            assertEquals("The next contact remains usable", 2L, surface.revision)
        }
    }

    @Test fun cancellationStopsTimerAndDiscardsThePendingStroke() {
        withAttachedCanvas { view, surface ->
            val downTime = SystemClock.uptimeMillis()

            sendStylus(view, downTime, downTime, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(70L)
            sendStylus(view, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL)
            SystemClock.sleep(70L)

            assertEquals(0L, surface.revision)
            assertEquals(0, surface.tileCount)
        }
    }

    private fun withAttachedCanvas(block: (InkCanvasView, PixelTileSurface) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        lateinit var view: InkCanvasView
        val surface = PixelTileSurface(1000, 1000)
        instrumentation.runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1000, 1000)
                tool = EditorTool.PEN
                fingerDrawingEnabled = false
                setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
                installV5TestInk(continuousPlot = true)
            }
            activity.addContentView(view, ViewGroup.LayoutParams(1000, 1000))
        }
        instrumentation.waitForIdleSync()
        try {
            block(view, surface)
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
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.awaitV5StrokeWorkerIdleForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        }
    }
}
