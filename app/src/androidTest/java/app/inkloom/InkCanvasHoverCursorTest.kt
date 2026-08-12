package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasHoverCursorTest {
    @Test
    fun freshCanvasDefaultsToTruthfulUncorrectedInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext)

        assertEquals(0f, view.stabilization, 0f)
        assertEquals(0f, view.streamLine, 0f)
        assertEquals(0f, view.procreateStabilization, 0f)
        assertEquals(0f, view.motionFiltering, 0f)
        assertEquals(0f, view.postCorrection, 0f)
    }

    @Test
    fun stylusHoverUsesCurrentBrushDiameterAndTracksZoomedCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            tool = EditorTool.PEN
            baseWidth = 160f
            viewportScale = 2f
            viewportPanX = 100f
            viewportPanY = -50f
            layout(0, 0, 1600, 1200)
        }

        instrumentation.runOnMainSync {
            val hover = stylusHover(900f, 550f)
            assertTrue(view.onHoverEvent(hover))
            hover.recycle()
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(1f, state[0], 0f)
        assertEquals(900f, state[1], .01f)
        assertEquals(550f, state[2], .01f)
        assertEquals(160f, state[3], .01f)

        view.baseWidth = 320f
        assertEquals(320f, view.hoverCursorSnapshot()[3], .01f)
    }

    @Test
    fun pressedStylusCursorFollowsEveryRawMoveAndReflectsPressureSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            tool = EditorTool.PEN
            baseWidth = 200f
            sizePressureEnabled = true
            sizePressureMinimum = .25f
            layout(0, 0, 1600, 1200)
        }

        instrumentation.runOnMainSync {
            stylusContact(MotionEvent.ACTION_DOWN, 300f, 240f, .2f, 1L, 1L).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
            stylusContact(MotionEvent.ACTION_MOVE, 920f, 710f, .8f, 1L, 12L).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(1f, state[0], 0f)
        assertEquals(920f, state[1], .01f)
        assertEquals(710f, state[2], .01f)
        assertEquals(170f, state[3], .75f)
    }

    @Test
    fun cursorKeepsFollowingStylusWhenPalmIsPointerZeroAndUsesGlobalPressureCurve() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            tool = EditorTool.PEN
            baseWidth = 200f
            sizePressureEnabled = true
            sizePressureMinimum = .25f
            pressureMinimumInput = .2f
            pressureMaximumInput = 1f
            layout(0, 0, 1600, 1200)
        }

        instrumentation.runOnMainSync {
            fingerAndStylusMove(fingerX = 30f, fingerY = 40f, stylusX = 980f, stylusY = 760f, stylusPressure = .2f).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(980f, state[1], .01f)
        assertEquals(760f, state[2], .01f)
        assertEquals(50f, state[3], .75f)
    }

    private fun stylusHover(x: Float, y: Float): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 0f
            size = 0f
        })
        return MotionEvent.obtain(
            1L, 2L, MotionEvent.ACTION_HOVER_MOVE, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }

    private fun stylusContact(action: Int, x: Float, y: Float, pressure: Float, downTime: Long, eventTime: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = 0f })
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }

    private fun fingerAndStylusMove(fingerX: Float, fingerY: Float, stylusX: Float, stylusY: Float, stylusPressure: Float): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 42; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = fingerX; y = fingerY; pressure = 1f },
            MotionEvent.PointerCoords().apply { x = stylusX; y = stylusY; pressure = stylusPressure },
        )
        return MotionEvent.obtain(
            1L, 2L, MotionEvent.ACTION_MOVE, 2, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS, 0,
        )
    }
}
