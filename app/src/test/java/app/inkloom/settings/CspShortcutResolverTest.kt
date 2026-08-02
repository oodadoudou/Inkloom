package app.inkloom.settings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CspShortcutResolverTest {
    @Test fun mapsCspToolGroupsAndBrushAdjustments() {
        assertEquals(ShortcutAction.PEN_OR_PENCIL_GROUP, resolve(KeyEvent.KEYCODE_P))
        assertEquals(ShortcutAction.BRUSH_GROUP, resolve(KeyEvent.KEYCODE_B))
        assertEquals(ShortcutAction.TOGGLE_ERASER, resolve(KeyEvent.KEYCODE_E))
        assertEquals(ShortcutAction.BRUSH_SIZE_DECREASE, resolve(KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(ShortcutAction.BRUSH_SIZE_INCREASE, resolve(KeyEvent.KEYCODE_RIGHT_BRACKET))
        assertEquals(ShortcutAction.OPACITY_DECREASE, resolve(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true))
        assertEquals(ShortcutAction.OPACITY_INCREASE, resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true))
    }

    @Test fun mapsEditingAndWorkspaceCommandsWithoutStealingModifiedToolKeys() {
        assertEquals(ShortcutAction.UNDO, resolve(KeyEvent.KEYCODE_Z, ctrl = true))
        assertEquals(ShortcutAction.REDO, resolve(KeyEvent.KEYCODE_Z, ctrl = true, shift = true))
        assertEquals(ShortcutAction.REDO, resolve(KeyEvent.KEYCODE_Y, ctrl = true))
        assertEquals(ShortcutAction.FIT_PAGE, resolve(KeyEvent.KEYCODE_0, ctrl = true))
        assertEquals(ShortcutAction.TOGGLE_PANELS, resolve(KeyEvent.KEYCODE_TAB))
        assertNull(resolve(KeyEvent.KEYCODE_P, ctrl = true))
    }

    @Test fun onlyContinuousAdjustmentsRepeatWhenAKeyIsHeld() {
        assertTrue(CspShortcutResolver.isRepeatable(ShortcutAction.BRUSH_SIZE_INCREASE))
        assertTrue(CspShortcutResolver.isRepeatable(ShortcutAction.OPACITY_DECREASE))
        assertEquals(false, CspShortcutResolver.isRepeatable(ShortcutAction.TOGGLE_ERASER))
    }

    @Test fun physicalBindingsMatchScanCodeAcrossKeyboardLayouts() {
        val binding = ShortcutBinding(
            ShortcutAction.TOGGLE_ERASER,
            ShortcutChord(KeyEvent.KEYCODE_E, scanCode = 18, matchMode = ShortcutMatchMode.SCAN_CODE),
        )

        assertEquals(ShortcutAction.TOGGLE_ERASER, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_UNKNOWN, scanCode = 18), listOf(binding)))
        assertNull(CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_E, scanCode = 19), listOf(binding)))
    }

    @Test fun deviceSpecificBindingsIgnoreOtherDevices() {
        val binding = ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B), deviceDescriptor = "toolbox-1")

        assertEquals(ShortcutAction.BRUSH_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_B), listOf(binding), "toolbox-1"))
        assertNull(CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_B), listOf(binding), "keyboard-2"))
    }

    private fun resolve(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        CspShortcutResolver.resolve(ShortcutChord(keyCode, ctrl, alt, shift))
}
