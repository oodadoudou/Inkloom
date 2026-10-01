package app.inkloom.settings

import android.view.KeyEvent

/** Historical defaults are recognized once at the settings import boundary. */
object LegacyShortcutImport {
    private val canvasSnapActionsAddedAfterPreviousDefaults = setOf(
        ShortcutAction.TOGGLE_RULER,
        ShortcutAction.TOGGLE_ALIGNMENT_GUIDES,
        ShortcutAction.TOGGLE_GRID_SNAP,
    )

    private val defaultsBeforeCanvasSnapCommands: List<ShortcutBinding> by lazy {
        CspShortcutResolver.defaultBindings.filterNot { it.action in canvasSnapActionsAddedAfterPreviousDefaults }
    }

    private val cspCommonActionsAddedAfterPreviousDefaults = setOf(
        ShortcutAction.SELECT_ALL,
        ShortcutAction.DESELECT,
        ShortcutAction.SELECT_LAYER_ABOVE,
        ShortcutAction.SELECT_LAYER_BELOW,
        ShortcutAction.ROTATE_VIEW_LEFT,
        ShortcutAction.ROTATE_VIEW_RIGHT,
    )

    private val cspCommonAliasesAddedAfterPreviousDefaults = setOf(
        ShortcutBinding(ShortcutAction.FIT_PAGE, ShortcutChord(KeyEvent.KEYCODE_AT, ctrl = true)),
    )

    private val defaultsBeforeCspCommonCommands: List<ShortcutBinding> by lazy {
        defaultsBeforeCanvasSnapCommands.filterNot { it.action in cspCommonActionsAddedAfterPreviousDefaults || it in cspCommonAliasesAddedAfterPreviousDefaults }
    }

    private val additionalCspAliases: Set<ShortcutBinding> by lazy {
        setOf(
            ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutChord(KeyEvent.KEYCODE_MINUS, ctrl = true)),
            ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutChord(KeyEvent.KEYCODE_SEMICOLON, ctrl = true)),
            ShortcutBinding(ShortcutAction.CUT_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F2)),
            ShortcutBinding(ShortcutAction.COPY_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F3)),
            ShortcutBinding(ShortcutAction.PASTE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F4)),
            ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_DEL)),
            ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_FORWARD_DEL, ctrl = true)),
            ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_DEL, ctrl = true)),
        )
    }

    private val defaultsBeforeAdditionalCspAliases: List<ShortcutBinding> by lazy {
        defaultsBeforeCspCommonCommands.filterNot { it in additionalCspAliases }
    }

    private val toolCommandsAddedAfterClipboardDefaults = setOf(
        ShortcutAction.SHAPE_TOOL,
        ShortcutAction.TEXT_TOOL,
        ShortcutAction.HAND_TOOL,
        ShortcutAction.ROTATE_VIEW_TOOL,
    )

    private val defaultsBeforeEyedropper: List<ShortcutBinding> by lazy {
        defaultsBeforeAdditionalCspAliases.filterNot { it.action == ShortcutAction.EYEDROPPER_TOOL }
    }

    private val defaultsBeforeActualSize: List<ShortcutBinding> by lazy {
        defaultsBeforeEyedropper.filterNot { it.action == ShortcutAction.ACTUAL_SIZE }
    }

    private val clipboardDefaultsBeforeToolCommands: List<ShortcutBinding> by lazy {
        defaultsBeforeActualSize.filterNot { it.action in toolCommandsAddedAfterClipboardDefaults }
    }

    private val commandsAddedAfterInitialDefaults = setOf(
        ShortcutAction.NEW_LAYER,
        ShortcutAction.MERGE_LAYER_DOWN,
        ShortcutAction.ZOOM_OUT,
        ShortcutAction.ZOOM_IN,
    )

    private val clipboardCommandsAddedAfterWorkspaceDefaults = setOf(
        ShortcutAction.CUT_SELECTION,
        ShortcutAction.COPY_SELECTION,
        ShortcutAction.PASTE_SELECTION,
        ShortcutAction.DELETE_SELECTION,
    )

    private val workspaceDefaultsBeforeClipboard: List<ShortcutBinding> by lazy {
        clipboardDefaultsBeforeToolCommands.filterNot { it.action in clipboardCommandsAddedAfterWorkspaceDefaults }
    }

    private val previousDefaultBindings: List<ShortcutBinding> by lazy {
        workspaceDefaultsBeforeClipboard.filterNot { it.action in commandsAddedAfterInitialDefaults }
    }

    private val legacyReversedBrushSizeDefaults: List<ShortcutBinding> by lazy {
        previousDefaultBindings.map { binding ->
            when (binding.action) {
                ShortcutAction.BRUSH_SIZE_DECREASE -> binding.copy(chord = ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET))
                ShortcutAction.BRUSH_SIZE_INCREASE -> binding.copy(chord = ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET))
                else -> binding
            }
        }
    }

    fun migrateLegacyDefaults(bindings: List<ShortcutBinding>): List<ShortcutBinding> =
        if (bindings == defaultsBeforeCanvasSnapCommands || bindings == defaultsBeforeCspCommonCommands || bindings == defaultsBeforeAdditionalCspAliases || bindings == defaultsBeforeEyedropper || bindings == defaultsBeforeActualSize || bindings == clipboardDefaultsBeforeToolCommands || bindings == workspaceDefaultsBeforeClipboard || bindings == legacyReversedBrushSizeDefaults || bindings == previousDefaultBindings) CspShortcutResolver.defaultBindings else bindings

}
