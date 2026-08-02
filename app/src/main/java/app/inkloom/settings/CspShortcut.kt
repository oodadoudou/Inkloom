package app.inkloom.settings

import android.view.KeyEvent

enum class ShortcutAction {
    PEN_OR_PENCIL_GROUP,
    PEN_GROUP,
    PENCIL_GROUP,
    BRUSH_GROUP,
    TOGGLE_ERASER,
    HIGHLIGHTER_TOOL,
    SELECT_TOOL,
    HAND_TOOL,
    SHAPE_TOOL,
    TEXT_TOOL,
    NOTE_TOOL,
    LINK_TOOL,
    IMAGE_TOOL,
    AUDIO_TOOL,
    BRUSH_SIZE_DECREASE,
    BRUSH_SIZE_INCREASE,
    OPACITY_DECREASE,
    OPACITY_INCREASE,
    PREVIOUS_BRUSH,
    NEXT_BRUSH,
    CUT_SELECTION,
    COPY_SELECTION,
    PASTE_SELECTION,
    DELETE_SELECTION,
    UNDO,
    REDO,
    NEW_LAYER,
    MERGE_LAYER_DOWN,
    ZOOM_OUT,
    ZOOM_IN,
    FIT_PAGE,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_BRUSH_PANEL,
    TOGGLE_LAYER_PANEL,
    TOGGLE_PANELS,
    SAVE_CHECKPOINT,
}

enum class ShortcutMatchMode { KEY_CODE, SCAN_CODE }
enum class ShortcutTrigger { PRESS, HOLD }
enum class ShortcutPhase { PRESS, RELEASE }

data class ShortcutChord(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val scanCode: Int = 0,
    val matchMode: ShortcutMatchMode = ShortcutMatchMode.KEY_CODE,
)

data class ShortcutBinding(
    val action: ShortcutAction,
    val chord: ShortcutChord,
    val trigger: ShortcutTrigger = ShortcutTrigger.PRESS,
    val deviceDescriptor: String? = null,
    val deviceName: String? = null,
)

data class ShortcutActivation(
    val action: ShortcutAction,
    val phase: ShortcutPhase,
    val trigger: ShortcutTrigger,
)

object CspShortcutResolver {
    val defaultBindings: List<ShortcutBinding> = listOf(
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.UNDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true)),
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Y, ctrl = true)),
        ShortcutBinding(ShortcutAction.NEW_LAYER, ShortcutChord(KeyEvent.KEYCODE_N, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.MERGE_LAYER_DOWN, ShortcutChord(KeyEvent.KEYCODE_E, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutChord(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutChord(KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true)),
        ShortcutBinding(ShortcutAction.FIT_PAGE, ShortcutChord(KeyEvent.KEYCODE_0, ctrl = true)),
        ShortcutBinding(ShortcutAction.SAVE_CHECKPOINT, ShortcutChord(KeyEvent.KEYCODE_S, ctrl = true)),
        ShortcutBinding(ShortcutAction.CUT_SELECTION, ShortcutChord(KeyEvent.KEYCODE_X, ctrl = true)),
        ShortcutBinding(ShortcutAction.COPY_SELECTION, ShortcutChord(KeyEvent.KEYCODE_C, ctrl = true)),
        ShortcutBinding(ShortcutAction.PASTE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_V, ctrl = true)),
        ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_FORWARD_DEL)),
        ShortcutBinding(ShortcutAction.OPACITY_DECREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.OPACITY_INCREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutChord(KeyEvent.KEYCODE_P)),
        ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B)),
        ShortcutBinding(ShortcutAction.TOGGLE_ERASER, ShortcutChord(KeyEvent.KEYCODE_E)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_M)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_O)),
        ShortcutBinding(ShortcutAction.HAND_TOOL, ShortcutChord(KeyEvent.KEYCODE_H)),
        ShortcutBinding(ShortcutAction.HAND_TOOL, ShortcutChord(KeyEvent.KEYCODE_SPACE), ShortcutTrigger.HOLD),
        ShortcutBinding(ShortcutAction.SHAPE_TOOL, ShortcutChord(KeyEvent.KEYCODE_U)),
        ShortcutBinding(ShortcutAction.TEXT_TOOL, ShortcutChord(KeyEvent.KEYCODE_T)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_DECREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET)),
        ShortcutBinding(ShortcutAction.PREVIOUS_BRUSH, ShortcutChord(KeyEvent.KEYCODE_COMMA)),
        ShortcutBinding(ShortcutAction.NEXT_BRUSH, ShortcutChord(KeyEvent.KEYCODE_PERIOD)),
        ShortcutBinding(ShortcutAction.TOGGLE_PANELS, ShortcutChord(KeyEvent.KEYCODE_TAB)),
    )

    private val toolCommandsAddedAfterClipboardDefaults = setOf(
        ShortcutAction.SHAPE_TOOL,
        ShortcutAction.TEXT_TOOL,
        ShortcutAction.HAND_TOOL,
    )

    private val clipboardDefaultsBeforeToolCommands: List<ShortcutBinding> by lazy {
        defaultBindings.filterNot { it.action in toolCommandsAddedAfterClipboardDefaults }
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
        if (bindings == clipboardDefaultsBeforeToolCommands || bindings == workspaceDefaultsBeforeClipboard || bindings == legacyReversedBrushSizeDefaults || bindings == previousDefaultBindings) defaultBindings else bindings

    fun resolve(chord: ShortcutChord, bindings: List<ShortcutBinding> = defaultBindings, deviceDescriptor: String? = null): ShortcutAction? =
        resolveBinding(chord, bindings, deviceDescriptor)?.action

    fun resolveBinding(chord: ShortcutChord, bindings: List<ShortcutBinding> = defaultBindings, deviceDescriptor: String? = null): ShortcutBinding? {
        val matching = bindings.filter { binding ->
            val configured = binding.chord
            val codeMatches = when (configured.matchMode) {
                ShortcutMatchMode.KEY_CODE -> configured.keyCode == chord.keyCode
                ShortcutMatchMode.SCAN_CODE -> configured.scanCode > 0 && configured.scanCode == chord.scanCode
            }
            codeMatches && configured.ctrl == chord.ctrl && configured.alt == chord.alt && configured.shift == chord.shift &&
                (binding.deviceDescriptor == null || binding.deviceDescriptor == deviceDescriptor)
        }
        return matching.firstOrNull { it.deviceDescriptor == deviceDescriptor && deviceDescriptor != null }
            ?: matching.firstOrNull { it.deviceDescriptor == null }
    }

    fun replace(
        bindings: List<ShortcutBinding>,
        action: ShortcutAction,
        chord: ShortcutChord,
        trigger: ShortcutTrigger = ShortcutTrigger.PRESS,
        deviceDescriptor: String? = null,
        deviceName: String? = null,
    ): List<ShortcutBinding> = bindings.filterNot { existing ->
        val sameScope = existing.deviceDescriptor == deviceDescriptor
        sameScope && (existing.action == action || sameInput(existing.chord, chord))
    } + ShortcutBinding(action, chord, trigger, deviceDescriptor, deviceName)

    private fun sameInput(left: ShortcutChord, right: ShortcutChord): Boolean =
        left.ctrl == right.ctrl && left.alt == right.alt && left.shift == right.shift && when {
            left.matchMode == ShortcutMatchMode.SCAN_CODE || right.matchMode == ShortcutMatchMode.SCAN_CODE -> left.scanCode > 0 && left.scanCode == right.scanCode
            else -> left.keyCode == right.keyCode
        }

    fun display(chord: ShortcutChord): String = buildList {
        if (chord.ctrl) add("Ctrl")
        if (chord.alt) add("Alt")
        if (chord.shift) add("Shift")
        add(if (chord.matchMode == ShortcutMatchMode.SCAN_CODE) "Scan ${chord.scanCode}" else KeyEvent.keyCodeToString(chord.keyCode).removePrefix("KEYCODE_").replace('_', ' '))
    }.joinToString("+")

    fun isRepeatable(action: ShortcutAction): Boolean = action in setOf(
        ShortcutAction.BRUSH_SIZE_DECREASE,
        ShortcutAction.BRUSH_SIZE_INCREASE,
        ShortcutAction.OPACITY_DECREASE,
        ShortcutAction.OPACITY_INCREASE,
        ShortcutAction.ZOOM_OUT,
        ShortcutAction.ZOOM_IN,
    )
}
