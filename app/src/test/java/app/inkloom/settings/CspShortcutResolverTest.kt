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
        assertEquals(ShortcutAction.BRUSH_SIZE_DECREASE, resolve(KeyEvent.KEYCODE_RIGHT_BRACKET))
        assertEquals(ShortcutAction.BRUSH_SIZE_INCREASE, resolve(KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(ShortcutAction.OPACITY_DECREASE, resolve(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true))
        assertEquals(ShortcutAction.OPACITY_INCREASE, resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true))
    }

    @Test fun dedicatedToolboxBrushGroupBindingsCanBeConfiguredIndependently() {
        val bindings = listOf(
            ShortcutBinding(ShortcutAction.PEN_GROUP, ShortcutChord(KeyEvent.KEYCODE_F1)),
            ShortcutBinding(ShortcutAction.PENCIL_GROUP, ShortcutChord(KeyEvent.KEYCODE_F2)),
            ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_F3)),
        )

        assertEquals(ShortcutAction.PEN_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_F1), bindings))
        assertEquals(ShortcutAction.PENCIL_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_F2), bindings))
        assertEquals(ShortcutAction.BRUSH_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_F3), bindings))
    }

    @Test fun mapsEditingAndWorkspaceCommandsWithoutStealingModifiedToolKeys() {
        assertEquals(ShortcutAction.UNDO, resolve(KeyEvent.KEYCODE_Z, ctrl = true))
        assertEquals(ShortcutAction.REDO, resolve(KeyEvent.KEYCODE_Z, ctrl = true, shift = true))
        assertEquals(ShortcutAction.REDO, resolve(KeyEvent.KEYCODE_Y, ctrl = true))
        assertEquals(ShortcutAction.NEW_LAYER, resolve(KeyEvent.KEYCODE_N, ctrl = true, shift = true))
        assertEquals(ShortcutAction.MERGE_LAYER_DOWN, resolve(KeyEvent.KEYCODE_E, ctrl = true))
        assertEquals(ShortcutAction.ZOOM_OUT, resolve(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true))
        assertEquals(ShortcutAction.ZOOM_IN, resolve(KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true))
        assertEquals(ShortcutAction.FIT_PAGE, resolve(KeyEvent.KEYCODE_0, ctrl = true))
        assertEquals(ShortcutAction.CUT_SELECTION, resolve(KeyEvent.KEYCODE_X, ctrl = true))
        assertEquals(ShortcutAction.COPY_SELECTION, resolve(KeyEvent.KEYCODE_C, ctrl = true))
        assertEquals(ShortcutAction.PASTE_SELECTION, resolve(KeyEvent.KEYCODE_V, ctrl = true))
        assertEquals(ShortcutAction.DELETE_SELECTION, resolve(KeyEvent.KEYCODE_FORWARD_DEL))
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

    @Test fun migratesOnlyTheFormerReversedDefaultBrushSizePair() {
        val addedActions = setOf(
            ShortcutAction.NEW_LAYER,
            ShortcutAction.MERGE_LAYER_DOWN,
            ShortcutAction.ZOOM_OUT,
            ShortcutAction.ZOOM_IN,
            ShortcutAction.CUT_SELECTION,
            ShortcutAction.COPY_SELECTION,
            ShortcutAction.PASTE_SELECTION,
            ShortcutAction.DELETE_SELECTION,
        )
        val oldDefaults = CspShortcutResolver.defaultBindings.filterNot { it.action in addedActions }.map { binding ->
            when (binding.action) {
                ShortcutAction.BRUSH_SIZE_DECREASE -> binding.copy(chord = ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET))
                ShortcutAction.BRUSH_SIZE_INCREASE -> binding.copy(chord = ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET))
                else -> binding
            }
        }
        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(oldDefaults))

        val customized = oldDefaults.map { binding ->
            if (binding.action == ShortcutAction.TOGGLE_ERASER) binding.copy(chord = ShortcutChord(KeyEvent.KEYCODE_R)) else binding
        }
        assertEquals(customized, CspShortcutResolver.migrateLegacyDefaults(customized))
    }

    @Test fun migratesExactWorkspaceDefaultsToClipboardDefaultsWithoutChangingCustomBindings() {
        val clipboardActions = setOf(ShortcutAction.CUT_SELECTION, ShortcutAction.COPY_SELECTION, ShortcutAction.PASTE_SELECTION, ShortcutAction.DELETE_SELECTION)
        val formerDefaults = CspShortcutResolver.defaultBindings.filterNot { it.action in clipboardActions }
        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(formerDefaults))

        val customized = formerDefaults.map { if (it.action == ShortcutAction.TOGGLE_ERASER) it.copy(chord = ShortcutChord(KeyEvent.KEYCODE_R)) else it }
        assertEquals(customized, CspShortcutResolver.migrateLegacyDefaults(customized))
    }

    private fun resolve(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        CspShortcutResolver.resolve(ShortcutChord(keyCode, ctrl, alt, shift))
}
