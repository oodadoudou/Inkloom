package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LaserTrail
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasLaserPointerTest {
    @Test fun stylusLaserRendersTemporarilyWithoutCommittingInk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 500, 500)
                tool = EditorTool.LASER
                pagePaperColor = Color.WHITE
            }
            val downTime = SystemClock.uptimeMillis()
            view.sendStylus(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 120f)
            view.sendStylus(downTime, downTime + 20L, MotionEvent.ACTION_MOVE, 250f, 250f)
            view.sendStylus(downTime, downTime + 40L, MotionEvent.ACTION_UP, 400f, 380f)

            assertTrue(view.hasVisibleLaserTrail(downTime + 40L))
            val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                var redPixelFound = false
                for (y in 80 until 420 step 4) for (x in 60 until 440 step 4) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.red(color) > Color.blue(color) + 25) redPixelFound = true
                }
                assertTrue("Transient laser glow should be visible", redPixelFound)
            } finally {
                bitmap.recycle()
            }
            assertFalse(view.hasVisibleLaserTrail(downTime + 40L + LaserTrail.DEFAULT_LIFETIME_MS + 1L))
        }
    }

    @Test fun cancelledLaserContactDiscardsItsTemporaryTrail() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 500, 500)
                tool = EditorTool.LASER
            }
            val downTime = SystemClock.uptimeMillis()
            view.sendStylus(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f)
            view.sendStylus(downTime, downTime + 20L, MotionEvent.ACTION_MOVE, 300f, 300f)
            view.sendStylus(downTime, downTime + 30L, MotionEvent.ACTION_CANCEL, 300f, 300f)

            assertFalse(view.hasVisibleLaserTrail(downTime + 30L))
        }
    }

    private fun InkCanvasView.sendStylus(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .7f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(
            downTime, eventTime, action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        ).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }
}
