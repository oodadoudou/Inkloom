package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.MotionInputDiagnostic
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolboxMotionInputTest {
    @Test fun highResolutionRotaryEventsAccumulateAndDispatchBrushSize() {
        val view = canvas().apply {
            shortcutAxisBindings = listOf(AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.BRUSH_SIZE))
        }
        val activations = mutableListOf<ShortcutActivation>()
        val diagnostics = mutableListOf<MotionInputDiagnostic>()
        view.onShortcutAction = activations::add
        view.onMotionInputDiagnostic = diagnostics::add

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(4) {
                val event = rotary(.25f)
                try {
                    assertTrue(view.onGenericMotionEvent(event))
                } finally {
                    event.recycle()
                }
            }
        }

        assertEquals(
            listOf(ShortcutActivation(ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutPhase.PRESS, ShortcutTrigger.PRESS)),
            activations,
        )
        assertEquals(4, diagnostics.size)
        assertEquals(MotionEvent.AXIS_SCROLL, diagnostics.last().axisCode)
    }

    @Test fun heldToolboxButtonRestoresOnReleaseAndFocusLoss() {
        val view = canvas().apply {
            shortcutButtonBindings = listOf(ButtonShortcutBinding(MotionEvent.BUTTON_BACK, ShortcutAction.HAND_TOOL, ShortcutTrigger.HOLD))
        }
        val activations = mutableListOf<ShortcutActivation>()
        view.onShortcutAction = activations::add

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatch(view, button(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_BACK))
            dispatch(view, button(MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.BUTTON_BACK))
            dispatch(view, button(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_BACK))
            view.onWindowFocusChanged(false)
        }

        assertEquals(
            listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE, ShortcutPhase.PRESS, ShortcutPhase.RELEASE),
            activations.map(ShortcutActivation::phase),
        )
        assertTrue(activations.all { it.action == ShortcutAction.HAND_TOOL && it.trigger == ShortcutTrigger.HOLD })
    }

    private fun canvas(): InkCanvasView {
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext())
        }
        return view
    }

    private fun rotary(value: Float): MotionEvent = motionEvent(
        action = MotionEvent.ACTION_SCROLL,
        buttonState = 0,
        configure = { setAxisValue(MotionEvent.AXIS_SCROLL, value) },
    )

    private fun button(action: Int, actionButton: Int): MotionEvent = motionEvent(
        action = action,
        buttonState = if (action == MotionEvent.ACTION_BUTTON_PRESS) actionButton else 0,
    )

    private fun motionEvent(
        action: Int,
        buttonState: Int,
        configure: MotionEvent.PointerCoords.() -> Unit = {},
    ): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = 100f
            y = 100f
            configure()
        }
        return MotionEvent.obtain(
            1_000L,
            1_000L,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
    }

    private fun dispatch(view: InkCanvasView, event: MotionEvent) {
        try {
            assertTrue(view.onGenericMotionEvent(event))
        } finally {
            event.recycle()
        }
    }
}
