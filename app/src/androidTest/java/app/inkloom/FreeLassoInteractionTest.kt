package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.settings.SelectionShape
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeLassoInteractionTest {
    @Test fun oneFingerDrawsFreeLassoInsteadOfMovingCanvasWhenFingerInkIsDisabled() {
        var panDistance = 0f
        var selectionMask: Bitmap? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 400, 400)
            pageModelWidth = 400f
            pageModelHeight = 400f
            tool = EditorTool.SELECT
            selectionShape = SelectionShape.FREEFORM
            fingerDrawingEnabled = false
            onViewportPan = { dx, dy -> panDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy) }
            onFillCommitted = { mask, _, _, _, _, _, _ -> selectionMask = mask }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            finger(view, MotionEvent.ACTION_DOWN, 100f, 100f, 0L)
            finger(view, MotionEvent.ACTION_MOVE, 300f, 100f, 10L)
            finger(view, MotionEvent.ACTION_MOVE, 300f, 300f, 20L)
            finger(view, MotionEvent.ACTION_MOVE, 100f, 300f, 30L)
            finger(view, MotionEvent.ACTION_UP, 100f, 100f, 40L)
            assertTrue(view.fillSelection())
        }

        assertEquals("Free lasso must own the one-finger contact", 0f, panDistance, 0f)
        val mask = requireNotNull(selectionMask)
        try {
            assertTrue(Color.alpha(mask.getPixel(mask.width / 2, mask.height / 2)) > 0)
            assertEquals(0, Color.alpha(mask.getPixel(mask.width / 20, mask.height / 20)))
        } finally {
            mask.recycle()
        }
    }

    @Test fun lassoStillOwnsFingerImmediatelyAfterStylusContact() {
        var panDistance = 0f
        var selectionMask: Bitmap? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 400, 400)
            pageModelWidth = 400f
            pageModelHeight = 400f
            tool = EditorTool.SELECT
            selectionShape = SelectionShape.FREEFORM
            fingerDrawingEnabled = false
            onViewportPan = { dx, dy -> panDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy) }
            onFillCommitted = { mask, _, _, _, _, _, _ -> selectionMask = mask }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stylus(view, MotionEvent.ACTION_DOWN, 40f, 40f, 1_000L)
            stylus(view, MotionEvent.ACTION_UP, 40f, 40f, 1_010L)
            finger(view, MotionEvent.ACTION_DOWN, 100f, 100f, 1_020L)
            finger(view, MotionEvent.ACTION_MOVE, 300f, 100f, 1_030L)
            finger(view, MotionEvent.ACTION_MOVE, 300f, 300f, 1_040L)
            finger(view, MotionEvent.ACTION_MOVE, 100f, 300f, 1_050L)
            finger(view, MotionEvent.ACTION_UP, 100f, 100f, 1_060L)
            assertTrue(view.fillSelection())
        }

        assertEquals(0f, panDistance, 0f)
        requireNotNull(selectionMask).recycle()
    }

    private fun finger(view: InkCanvasView, action: Int, x: Float, y: Float, eventTime: Long) {
        pointer(view, action, x, y, eventTime, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)
    }

    private fun stylus(view: InkCanvasView, action: Int, x: Float, y: Float, eventTime: Long) {
        pointer(view, action, x, y, eventTime, MotionEvent.TOOL_TYPE_STYLUS, InputDevice.SOURCE_STYLUS)
    }

    private fun pointer(view: InkCanvasView, action: Int, x: Float, y: Float, eventTime: Long, toolType: Int, source: Int) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = 1f; size = .05f; touchMajor = 8f
        })
        MotionEvent.obtain(
            0L, eventTime, action, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, source, 0,
        ).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }
}
