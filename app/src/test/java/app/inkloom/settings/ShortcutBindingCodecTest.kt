package app.inkloom.settings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShortcutBindingCodecTest {
    @Test
    fun `bindings round trip through preferences format`() {
        val bindings = listOf(
            ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B)),
            ShortcutBinding(
                ShortcutAction.TOGGLE_ERASER,
                ShortcutChord(KeyEvent.KEYCODE_E, ctrl = true, scanCode = 18, matchMode = ShortcutMatchMode.SCAN_CODE),
                trigger = ShortcutTrigger.HOLD,
                deviceDescriptor = "usb:1234,abcd|toolbox",
                deviceName = "Wacom Toolbox",
            ),
        )

        assertEquals(bindings, ShortcutBindingCodec.decode(ShortcutBindingCodec.encode(bindings)))
    }

    @Test
    fun `invalid stored value falls back to CSP defaults`() {
        assertEquals(CspShortcutResolver.defaultBindings, ShortcutBindingCodec.decode("broken"))
    }

    @Test
    fun `replacement removes old action and chord conflicts`() {
        val replacement = ShortcutChord(KeyEvent.KEYCODE_B)
        val updated = CspShortcutResolver.replace(
            CspShortcutResolver.defaultBindings,
            ShortcutAction.TOGGLE_ERASER,
            replacement,
        )

        assertEquals(ShortcutAction.TOGGLE_ERASER, CspShortcutResolver.resolve(replacement, updated))
        assertFalse(updated.any { it.action == ShortcutAction.BRUSH_GROUP })
        assertFalse(updated.any { it.action == ShortcutAction.TOGGLE_ERASER && it.chord != replacement })
    }

    @Test
    fun `replacement is scoped to one input device`() {
        val global = ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B))
        val toolbox = ShortcutBinding(
            ShortcutAction.BRUSH_GROUP,
            ShortcutChord(KeyEvent.KEYCODE_F1, scanCode = 59, matchMode = ShortcutMatchMode.SCAN_CODE),
            deviceDescriptor = "toolbox-1",
        )

        val updated = CspShortcutResolver.replace(
            listOf(global, toolbox),
            ShortcutAction.BRUSH_GROUP,
            ShortcutChord(KeyEvent.KEYCODE_F2, scanCode = 60, matchMode = ShortcutMatchMode.SCAN_CODE),
            deviceDescriptor = "toolbox-1",
        )

        assertEquals(global, updated.single { it.deviceDescriptor == null })
        assertEquals(60, updated.single { it.deviceDescriptor == "toolbox-1" }.chord.scanCode)
    }

    @Test
    fun `legacy five field bindings remain readable`() {
        assertEquals(
            listOf(ShortcutBinding(ShortcutAction.UNDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true))),
            ShortcutBindingCodec.decode("UNDO,54,true,false,false"),
        )
    }
}
