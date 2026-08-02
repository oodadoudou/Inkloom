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
            ShortcutBinding(ShortcutAction.UNDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true)),
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
}
