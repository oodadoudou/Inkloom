package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.PdfGotoLink
import app.inkloom.document.PdfTextBounds
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfGotoLinkCanvasTest {
    @Test fun cleanFingerTapActivatesLinkButDragDoesNot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var activations = 0
        instrumentation.runOnMainSync {
            val view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 1000, 1000)
                pdfGotoLinks = listOf(PdfGotoLink(listOf(PdfTextBounds(.1f, .1f, .3f, .3f)), "target"))
                onPdfGotoLink = { activations += 1 }
            }

            view.dispatchFinger(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
            view.dispatchFinger(MotionEvent.ACTION_UP, 200f, 200f, 40L)
            view.dispatchFinger(MotionEvent.ACTION_DOWN, 200f, 200f, 100L)
            view.dispatchFinger(MotionEvent.ACTION_MOVE, 260f, 260f, 130L)
            view.dispatchFinger(MotionEvent.ACTION_UP, 260f, 260f, 160L)
        }

        assertEquals(1, activations)
    }

    private fun InkCanvasView.dispatchFinger(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 10f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0).also {
            dispatchTouchEvent(it)
            it.recycle()
        }
    }
}
