package app.inkloom.settings

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolboxMotionShortcutTest {
    @Test fun `fractional high resolution axis input accumulates without losing steps`() {
        val accumulator = ShortcutAxisAccumulator()
        val binding = AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.BRUSH_SIZE)

        assertEquals(emptyList<ShortcutActivation>(), accumulator.activations(binding, .25f))
        assertEquals(emptyList<ShortcutActivation>(), accumulator.activations(binding, .25f))
        assertEquals(emptyList<ShortcutActivation>(), accumulator.activations(binding, .25f))
        assertEquals(
            listOf(ShortcutActivation(ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutPhase.PRESS, ShortcutTrigger.PRESS)),
            accumulator.activations(binding, .25f),
        )
    }

    @Test fun `sensitivity reverse direction and per event cap are deterministic`() {
        val accumulator = ShortcutAxisAccumulator()
        val binding = AxisShortcutBinding(ToolboxAxis.VERTICAL_SCROLL, ToolboxAxisMode.OPACITY, reversed = true, sensitivity = 2f)

        assertEquals(List(2) { ShortcutActivation(ShortcutAction.OPACITY_DECREASE, ShortcutPhase.PRESS, ShortcutTrigger.PRESS) }, accumulator.activations(binding, 1f))
        assertEquals(ShortcutAxisAccumulator.MAX_STEPS_PER_EVENT, accumulator.activations(binding, 100f).size)
    }

    @Test fun `global axis binding keeps fractional remainder separate for each physical device`() {
        val accumulator = ShortcutAxisAccumulator()
        val binding = AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.ZOOM)

        repeat(3) { assertEquals(emptyList<ShortcutActivation>(), accumulator.activations(binding, .25f, "dial-a")) }
        assertEquals(emptyList<ShortcutActivation>(), accumulator.activations(binding, .25f, "dial-b"))
        assertEquals(1, accumulator.activations(binding, .25f, "dial-a").size)
    }

    @Test fun `device specific axis and button bindings override global mappings`() {
        val globalAxis = AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.ZOOM)
        val deviceAxis = AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.BRUSH_SIZE, deviceDescriptor = "toolbox")
        val globalButton = ButtonShortcutBinding(MotionEvent.BUTTON_BACK, ShortcutAction.UNDO)
        val deviceButton = ButtonShortcutBinding(MotionEvent.BUTTON_BACK, ShortcutAction.TOGGLE_ERASER, deviceDescriptor = "toolbox")

        assertEquals(deviceAxis, ToolboxMotionResolver.resolveAxis(ToolboxAxis.ROTARY, listOf(globalAxis, deviceAxis), "toolbox"))
        assertEquals(globalAxis, ToolboxMotionResolver.resolveAxis(ToolboxAxis.ROTARY, listOf(globalAxis, deviceAxis), "mouse"))
        assertEquals(deviceButton, ToolboxMotionResolver.resolveButton(MotionEvent.BUTTON_BACK, listOf(globalButton, deviceButton), "toolbox"))
        assertEquals(globalButton, ToolboxMotionResolver.resolveButton(MotionEvent.BUTTON_BACK, listOf(globalButton, deviceButton), null))
        assertNull(ToolboxMotionResolver.resolveButton(MotionEvent.BUTTON_FORWARD, listOf(globalButton), null))
    }

    @Test fun `tap buttons ignore release while hold buttons restore on release`() {
        val tap = ButtonShortcutBinding(MotionEvent.BUTTON_SECONDARY, ShortcutAction.UNDO)
        val hold = ButtonShortcutBinding(MotionEvent.BUTTON_SECONDARY, ShortcutAction.HAND_TOOL, ShortcutTrigger.HOLD)

        assertEquals(ShortcutActivation(ShortcutAction.UNDO, ShortcutPhase.PRESS, ShortcutTrigger.PRESS), ToolboxMotionResolver.buttonActivation(tap, ShortcutPhase.PRESS))
        assertNull(ToolboxMotionResolver.buttonActivation(tap, ShortcutPhase.RELEASE))
        assertEquals(ShortcutActivation(ShortcutAction.HAND_TOOL, ShortcutPhase.PRESS, ShortcutTrigger.HOLD), ToolboxMotionResolver.buttonActivation(hold, ShortcutPhase.PRESS))
        assertEquals(ShortcutActivation(ShortcutAction.HAND_TOOL, ShortcutPhase.RELEASE, ShortcutTrigger.HOLD), ToolboxMotionResolver.buttonActivation(hold, ShortcutPhase.RELEASE))
    }

    @Test fun `motion preference codecs preserve device identity and tuning`() {
        val axes = listOf(AxisShortcutBinding(ToolboxAxis.HORIZONTAL_SCROLL, ToolboxAxisMode.OPACITY, true, 2.5f, .1f, "usb:axis", "Dial"))
        val buttons = listOf(ButtonShortcutBinding(MotionEvent.BUTTON_FORWARD, ShortcutAction.PENCIL_GROUP, ShortcutTrigger.HOLD, "usb:button", "Button box"))

        assertEquals(axes, ToolboxMotionBindingCodec.decodeAxes(ToolboxMotionBindingCodec.encodeAxes(axes)))
        assertEquals(buttons, ToolboxMotionBindingCodec.decodeButtons(ToolboxMotionBindingCodec.encodeButtons(buttons)))
    }

    @Test fun `motion preference codec rejects invalid multi-button masks`() {
        val invalid = "3,UNDO,PRESS,,"

        assertEquals(emptyList<ButtonShortcutBinding>(), ToolboxMotionBindingCodec.decodeButtons(invalid))
    }
}
