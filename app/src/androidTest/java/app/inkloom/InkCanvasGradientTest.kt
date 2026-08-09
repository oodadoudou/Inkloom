package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasGradientTest {
    @Test fun stylusDragCommitsGradientDirectionAndColors() {
        var result: FloatArray? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.GRADIENT
            inkColor = android.graphics.Color.RED
            gradientEndColor = android.graphics.Color.BLUE
            onGradientCommitted = { sx, sy, ex, ey, start, end ->
                result = floatArrayOf(sx, sy, ex, ey, start.toFloat(), end.toFloat())
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatch(view, MotionEvent.ACTION_DOWN, 100f, 200f, 10L)
            dispatch(view, MotionEvent.ACTION_MOVE, 800f, 700f, 20L)
            dispatch(view, MotionEvent.ACTION_UP, 800f, 700f, 30L)
        }

        val gradient = requireNotNull(result)
        assertEquals(.1f, gradient[0], .001f)
        assertEquals(.2f, gradient[1], .001f)
        assertEquals(.8f, gradient[2], .001f)
        assertEquals(.7f, gradient[3], .001f)
        assertTrue(gradient[4] != gradient[5])
    }

    private fun dispatch(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f }
        MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }
}
