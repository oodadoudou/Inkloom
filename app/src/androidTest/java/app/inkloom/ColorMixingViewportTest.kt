package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.color.MixingBoard
import app.inkloom.ui.ColorMixingView
import org.junit.Assert.*
import org.junit.Test

class ColorMixingViewportTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun backgroundIsPresentationOnlyAndNavigationDoesNotPaint() {
        instrumentation.runOnMainSync {
            val view = ColorMixingView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 600, 400)
            view.board.begin(100f, 100f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 10f)
            view.board.finish()
            val original = view.board.snapshot()
            view.backgroundTone = android.graphics.Color.WHITE
            val white = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(white))
            view.backgroundTone = android.graphics.Color.BLACK
            val black = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(black))
            assertEquals(android.graphics.Color.WHITE, white.getPixel(0, 0))
            assertEquals(android.graphics.Color.BLACK, black.getPixel(0, 0))
            assertArrayEquals(original, view.board.snapshot())
            assertTrue(view.board.canUndo)
            white.recycle(); black.recycle()
        }
    }
    @Test fun wheelZoomKeepsPixelsAndResetRestoresViewport() {
        instrumentation.runOnMainSync {
            val view = ColorMixingView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 600, 400)
            val original = view.board.snapshot()
            val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply { x = 300f; y = 200f; setAxisValue(MotionEvent.AXIS_VSCROLL, 3f) })
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
            view.dispatchGenericMotionEvent(event); event.recycle()
            assertTrue(view.zoom > 1f)
            assertArrayEquals(original, view.board.snapshot())
            assertFalse(view.board.canUndo)
            view.resetViewport(); assertEquals(1f, view.zoom)
        }
    }
}
