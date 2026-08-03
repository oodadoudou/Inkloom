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
        assertEquals(ShortcutAction.SHAPE_TOOL, resolve(KeyEvent.KEYCODE_U))
        assertEquals(ShortcutAction.TEXT_TOOL, resolve(KeyEvent.KEYCODE_T))
        assertEquals(ShortcutAction.HAND_TOOL, resolve(KeyEvent.KEYCODE_H))
        assertEquals(ShortcutAction.HAND_TOOL, resolve(KeyEvent.KEYCODE_SPACE))
        assertEquals(ShortcutAction.ROTATE_VIEW_TOOL, resolve(KeyEvent.KEYCODE_R))
        assertEquals(ShortcutAction.ROTATE_VIEW_TOOL, resolve(KeyEvent.KEYCODE_SPACE, shift = true))
        assertEquals(ShortcutAction.EYEDROPPER_TOOL, resolve(KeyEvent.KEYCODE_I))
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
        assertEquals(ShortcutAction.ACTUAL_SIZE, resolve(KeyEvent.KEYCODE_0, ctrl = true, alt = true))
        assertEquals(ShortcutAction.CUT_SELECTION, resolve(KeyEvent.KEYCODE_X, ctrl = true))
        assertEquals(ShortcutAction.COPY_SELECTION, resolve(KeyEvent.KEYCODE_C, ctrl = true))
        assertEquals(ShortcutAction.PASTE_SELECTION, resolve(KeyEvent.KEYCODE_V, ctrl = true))
        assertEquals(ShortcutAction.DELETE_SELECTION, resolve(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(ShortcutAction.TOGGLE_PANELS, resolve(KeyEvent.KEYCODE_TAB))
        assertNull(resolve(KeyEvent.KEYCODE_P, ctrl = true))
    }

    @Test fun mapsAdditionalOfficialCspAliasesForSupportedCommands() {
        assertEquals(ShortcutAction.ZOOM_OUT, resolve(KeyEvent.KEYCODE_MINUS, ctrl = true))
        assertEquals(ShortcutAction.ZOOM_IN, resolve(KeyEvent.KEYCODE_SEMICOLON, ctrl = true))
        assertEquals(ShortcutAction.CUT_SELECTION, resolve(KeyEvent.KEYCODE_F2))
        assertEquals(ShortcutAction.COPY_SELECTION, resolve(KeyEvent.KEYCODE_F3))
        assertEquals(ShortcutAction.PASTE_SELECTION, resolve(KeyEvent.KEYCODE_F4))
        assertEquals(ShortcutAction.DELETE_SELECTION, resolve(KeyEvent.KEYCODE_DEL))
        assertEquals(ShortcutAction.DELETE_SELECTION, resolve(KeyEvent.KEYCODE_FORWARD_DEL, ctrl = true))
        assertEquals(ShortcutAction.DELETE_SELECTION, resolve(KeyEvent.KEYCODE_DEL, ctrl = true))
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

    @Test fun deviceSpecificBindingOverridesTheGlobalChordOnlyOnItsOwnDevice() {
        val bindings = CspShortcutResolver.defaultBindings + ShortcutBinding(
            ShortcutAction.TOGGLE_ERASER,
            ShortcutChord(KeyEvent.KEYCODE_B),
            deviceDescriptor = "toolbox-1",
        )

        assertEquals(ShortcutAction.TOGGLE_ERASER, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_B), bindings, "toolbox-1"))
        assertEquals(ShortcutAction.BRUSH_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_B), bindings, "keyboard-2"))
    }

    @Test fun recordingADeviceBindingKeepsTheGlobalCspBindingForTheSameAction() {
        val updated = CspShortcutResolver.replace(
            CspShortcutResolver.defaultBindings,
            ShortcutAction.BRUSH_GROUP,
            ShortcutChord(KeyEvent.KEYCODE_F3, scanCode = 61, matchMode = ShortcutMatchMode.SCAN_CODE),
            deviceDescriptor = "toolbox-1",
            deviceName = "Toolbox",
        )

        assertEquals(ShortcutAction.BRUSH_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_B), updated, "keyboard-2"))
        assertEquals(ShortcutAction.BRUSH_GROUP, CspShortcutResolver.resolve(ShortcutChord(KeyEvent.KEYCODE_UNKNOWN, scanCode = 61), updated, "toolbox-1"))
        assertEquals(2, updated.count { it.action == ShortcutAction.BRUSH_GROUP })
    }

    @Test fun everyShortcutActionHasOneEditorCategory() {
        assertEquals(ShortcutAction.entries.size, ShortcutAction.entries.map(ShortcutAction::category).size)
        assertEquals(ShortcutCategory.BRUSH_GROUPS, ShortcutAction.PEN_GROUP.category())
        assertEquals(ShortcutCategory.BRUSH_ADJUSTMENTS, ShortcutAction.BRUSH_SIZE_INCREASE.category())
        assertEquals(ShortcutCategory.PANELS, ShortcutAction.TOGGLE_LAYER_PANEL.category())
        assertEquals(ShortcutCategory.QUICK_ACCESS, ShortcutAction.QUICK_SLOT_8.category())
        assertEquals(7, ToolboxQuickSlots.indexOf(ShortcutAction.QUICK_SLOT_8))
    }

    @Test fun restoringOneActionKeepsOtherDeviceMappingsAndAllOfItsCspDefaults() {
        val customized = CspShortcutResolver.defaultBindings
            .filterNot { it.action == ShortcutAction.REDO }
            .plus(ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_F4), deviceDescriptor = "toolbox"))
            .plus(ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_F3), deviceDescriptor = "toolbox"))

        val restored = CspShortcutResolver.restoreActionDefaults(customized, ShortcutAction.REDO)

        assertEquals(CspShortcutResolver.defaultBindings.filter { it.action == ShortcutAction.REDO }, restored.filter { it.action == ShortcutAction.REDO })
        assertTrue(restored.any { it.action == ShortcutAction.BRUSH_GROUP && it.deviceDescriptor == "toolbox" })
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
            ShortcutAction.SHAPE_TOOL,
            ShortcutAction.TEXT_TOOL,
            ShortcutAction.HAND_TOOL,
            ShortcutAction.ROTATE_VIEW_TOOL,
            ShortcutAction.ACTUAL_SIZE,
            ShortcutAction.EYEDROPPER_TOOL,
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
        val actionsAddedLater = setOf(ShortcutAction.CUT_SELECTION, ShortcutAction.COPY_SELECTION, ShortcutAction.PASTE_SELECTION, ShortcutAction.DELETE_SELECTION, ShortcutAction.SHAPE_TOOL, ShortcutAction.TEXT_TOOL, ShortcutAction.HAND_TOOL, ShortcutAction.ROTATE_VIEW_TOOL, ShortcutAction.ACTUAL_SIZE, ShortcutAction.EYEDROPPER_TOOL)
        val formerDefaults = defaultsBeforeAdditionalAliases().filterNot { it.action in actionsAddedLater }
        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(formerDefaults))

        val customized = formerDefaults.map { if (it.action == ShortcutAction.TOGGLE_ERASER) it.copy(chord = ShortcutChord(KeyEvent.KEYCODE_R)) else it }
        assertEquals(customized, CspShortcutResolver.migrateLegacyDefaults(customized))
    }

    @Test fun migratesTheFormerClipboardDefaultsToTheExpandedToolDefaults() {
        val toolActions = setOf(ShortcutAction.SHAPE_TOOL, ShortcutAction.TEXT_TOOL, ShortcutAction.HAND_TOOL, ShortcutAction.ROTATE_VIEW_TOOL, ShortcutAction.ACTUAL_SIZE, ShortcutAction.EYEDROPPER_TOOL)
        val formerDefaults = defaultsBeforeAdditionalAliases().filterNot { it.action in toolActions }

        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(formerDefaults))

        val customized = formerDefaults.map { if (it.action == ShortcutAction.TOGGLE_ERASER) it.copy(chord = ShortcutChord(KeyEvent.KEYCODE_R)) else it }
        assertEquals(customized, CspShortcutResolver.migrateLegacyDefaults(customized))
    }

    @Test fun migratesThePreviousCompleteDefaultsToIncludeActualSize() {
        val formerDefaults = defaultsBeforeAdditionalAliases().filterNot { it.action in setOf(ShortcutAction.ACTUAL_SIZE, ShortcutAction.EYEDROPPER_TOOL) }

        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(formerDefaults))
    }

    @Test fun migratesThePreviousCompleteDefaultsToIncludeTheEyedropper() {
        val formerDefaults = defaultsBeforeAdditionalAliases().filterNot { it.action == ShortcutAction.EYEDROPPER_TOOL }

        assertEquals(CspShortcutResolver.defaultBindings, CspShortcutResolver.migrateLegacyDefaults(formerDefaults))
    }

    private fun resolve(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        CspShortcutResolver.resolve(ShortcutChord(keyCode, ctrl, alt, shift))

    private fun defaultsBeforeAdditionalAliases(): List<ShortcutBinding> = CspShortcutResolver.defaultBindings.filterNot { binding ->
        binding.action to binding.chord.keyCode in setOf(
            ShortcutAction.ZOOM_OUT to KeyEvent.KEYCODE_MINUS,
            ShortcutAction.ZOOM_IN to KeyEvent.KEYCODE_SEMICOLON,
            ShortcutAction.CUT_SELECTION to KeyEvent.KEYCODE_F2,
            ShortcutAction.COPY_SELECTION to KeyEvent.KEYCODE_F3,
            ShortcutAction.PASTE_SELECTION to KeyEvent.KEYCODE_F4,
            ShortcutAction.DELETE_SELECTION to KeyEvent.KEYCODE_DEL,
        ) || binding.action == ShortcutAction.DELETE_SELECTION && binding.chord.ctrl
    }
}
