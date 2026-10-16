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
    ROTATE_VIEW_TOOL,
    EYEDROPPER_TOOL,
    SHAPE_TOOL,
    TEXT_TOOL,
    NOTE_TOOL,
    LINK_TOOL,
    IMAGE_TOOL,
    LASER_TOOL,
    BRUSH_SIZE_DECREASE,
    BRUSH_SIZE_INCREASE,
    OPACITY_DECREASE,
    OPACITY_INCREASE,
    PREVIOUS_BRUSH,
    NEXT_BRUSH,
    CUT_SELECTION,
    COPY_SELECTION,
    PASTE_SELECTION,
    PASTE_SELECTION_AT_VIEW,
    DELETE_SELECTION,
    SELECT_ALL,
    DESELECT,
    UNDO,
    REDO,
    NEW_LAYER,
    MERGE_LAYER_DOWN,
    SELECT_LAYER_ABOVE,
    SELECT_LAYER_BELOW,
    ZOOM_OUT,
    ZOOM_IN,
    ROTATE_VIEW_LEFT,
    ROTATE_VIEW_RIGHT,
    FIT_PAGE,
    ACTUAL_SIZE,
    FIT_WIDTH,
    RESET_VIEW_ROTATION,
    TOGGLE_RULER,
    TOGGLE_GRID_SNAP,
    TOGGLE_ALIGNMENT_GUIDES,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    PAGE_BACK,
    PAGE_FORWARD,
    TOGGLE_BRUSH_PANEL,
    TOGGLE_LAYER_PANEL,
    TOGGLE_PANELS,
    SAVE_CHECKPOINT,
    QUICK_SLOT_1,
    QUICK_SLOT_2,
    QUICK_SLOT_3,
    QUICK_SLOT_4,
    QUICK_SLOT_5,
    QUICK_SLOT_6,
    QUICK_SLOT_7,
    QUICK_SLOT_8,
}

enum class ShortcutCategory { QUICK_ACCESS, TOOLS, BRUSH_GROUPS, BRUSH_ADJUSTMENTS, EDITING, CANVAS, PAGES, LAYERS, PANELS, FILE }

fun ShortcutAction.category(): ShortcutCategory = when (this) {
    ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutAction.PEN_GROUP, ShortcutAction.PENCIL_GROUP, ShortcutAction.BRUSH_GROUP,
    ShortcutAction.PREVIOUS_BRUSH, ShortcutAction.NEXT_BRUSH -> ShortcutCategory.BRUSH_GROUPS
    ShortcutAction.TOGGLE_ERASER, ShortcutAction.HIGHLIGHTER_TOOL, ShortcutAction.SELECT_TOOL, ShortcutAction.HAND_TOOL,
    ShortcutAction.ROTATE_VIEW_TOOL, ShortcutAction.EYEDROPPER_TOOL, ShortcutAction.SHAPE_TOOL, ShortcutAction.TEXT_TOOL,
    ShortcutAction.NOTE_TOOL, ShortcutAction.LINK_TOOL, ShortcutAction.IMAGE_TOOL,
    ShortcutAction.LASER_TOOL -> ShortcutCategory.TOOLS
    ShortcutAction.BRUSH_SIZE_DECREASE, ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutAction.OPACITY_DECREASE,
    ShortcutAction.OPACITY_INCREASE -> ShortcutCategory.BRUSH_ADJUSTMENTS
    ShortcutAction.CUT_SELECTION, ShortcutAction.COPY_SELECTION, ShortcutAction.PASTE_SELECTION, ShortcutAction.PASTE_SELECTION_AT_VIEW, ShortcutAction.DELETE_SELECTION,
    ShortcutAction.SELECT_ALL, ShortcutAction.DESELECT,
    ShortcutAction.UNDO, ShortcutAction.REDO -> ShortcutCategory.EDITING
    ShortcutAction.ZOOM_OUT, ShortcutAction.ZOOM_IN, ShortcutAction.ROTATE_VIEW_LEFT, ShortcutAction.ROTATE_VIEW_RIGHT,
    ShortcutAction.FIT_PAGE, ShortcutAction.ACTUAL_SIZE,
    ShortcutAction.FIT_WIDTH, ShortcutAction.RESET_VIEW_ROTATION, ShortcutAction.TOGGLE_RULER,
    ShortcutAction.TOGGLE_GRID_SNAP, ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> ShortcutCategory.CANVAS
    ShortcutAction.PREVIOUS_PAGE, ShortcutAction.NEXT_PAGE,
    ShortcutAction.PAGE_BACK, ShortcutAction.PAGE_FORWARD -> ShortcutCategory.PAGES
    ShortcutAction.NEW_LAYER, ShortcutAction.MERGE_LAYER_DOWN, ShortcutAction.SELECT_LAYER_ABOVE,
    ShortcutAction.SELECT_LAYER_BELOW -> ShortcutCategory.LAYERS
    ShortcutAction.TOGGLE_BRUSH_PANEL, ShortcutAction.TOGGLE_LAYER_PANEL, ShortcutAction.TOGGLE_PANELS -> ShortcutCategory.PANELS
    ShortcutAction.SAVE_CHECKPOINT -> ShortcutCategory.FILE
    ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
    ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> ShortcutCategory.QUICK_ACCESS
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

data class KeyInputDiagnostic(
    val phase: ShortcutPhase,
    val keyCode: Int,
    val scanCode: Int,
    val metaState: Int,
    val deviceId: Int,
    val deviceName: String?,
    val deviceDescriptor: String?,
    val source: Int,
    val matchedAction: ShortcutAction?,
)

object CspShortcutResolver {
    val defaultBindings: List<ShortcutBinding> = listOf(
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.UNDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true)),
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Y, ctrl = true)),
        ShortcutBinding(ShortcutAction.NEW_LAYER, ShortcutChord(KeyEvent.KEYCODE_N, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.MERGE_LAYER_DOWN, ShortcutChord(KeyEvent.KEYCODE_E, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutChord(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutChord(KeyEvent.KEYCODE_MINUS, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutChord(KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true)),
        ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutChord(KeyEvent.KEYCODE_SEMICOLON, ctrl = true)),
        ShortcutBinding(ShortcutAction.FIT_PAGE, ShortcutChord(KeyEvent.KEYCODE_0, ctrl = true)),
        ShortcutBinding(ShortcutAction.ACTUAL_SIZE, ShortcutChord(KeyEvent.KEYCODE_0, ctrl = true, alt = true)),
        ShortcutBinding(ShortcutAction.TOGGLE_RULER, ShortcutChord(KeyEvent.KEYCODE_1, ctrl = true)),
        ShortcutBinding(ShortcutAction.TOGGLE_ALIGNMENT_GUIDES, ShortcutChord(KeyEvent.KEYCODE_2, ctrl = true)),
        ShortcutBinding(ShortcutAction.TOGGLE_GRID_SNAP, ShortcutChord(KeyEvent.KEYCODE_3, ctrl = true)),
        ShortcutBinding(ShortcutAction.SAVE_CHECKPOINT, ShortcutChord(KeyEvent.KEYCODE_S, ctrl = true)),
        ShortcutBinding(ShortcutAction.CUT_SELECTION, ShortcutChord(KeyEvent.KEYCODE_X, ctrl = true)),
        ShortcutBinding(ShortcutAction.CUT_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F2)),
        ShortcutBinding(ShortcutAction.COPY_SELECTION, ShortcutChord(KeyEvent.KEYCODE_C, ctrl = true)),
        ShortcutBinding(ShortcutAction.COPY_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F3)),
        ShortcutBinding(ShortcutAction.PASTE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_V, ctrl = true)),
        ShortcutBinding(ShortcutAction.PASTE_SELECTION_AT_VIEW, ShortcutChord(KeyEvent.KEYCODE_V, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.PASTE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_F4)),
        ShortcutBinding(ShortcutAction.SELECT_ALL, ShortcutChord(KeyEvent.KEYCODE_A, ctrl = true)),
        ShortcutBinding(ShortcutAction.DESELECT, ShortcutChord(KeyEvent.KEYCODE_D, ctrl = true)),
        ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_FORWARD_DEL)),
        ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_DEL)),
        ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_FORWARD_DEL, ctrl = true)),
        ShortcutBinding(ShortcutAction.DELETE_SELECTION, ShortcutChord(KeyEvent.KEYCODE_DEL, ctrl = true)),
        ShortcutBinding(ShortcutAction.OPACITY_DECREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.OPACITY_INCREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.SELECT_LAYER_ABOVE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET, alt = true)),
        ShortcutBinding(ShortcutAction.SELECT_LAYER_BELOW, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET, alt = true)),
        // Alt+Left/Right step through page jumps like a PDF reader's previous/next view.
        ShortcutBinding(ShortcutAction.PAGE_BACK, ShortcutChord(KeyEvent.KEYCODE_DPAD_LEFT, alt = true)),
        ShortcutBinding(ShortcutAction.PAGE_FORWARD, ShortcutChord(KeyEvent.KEYCODE_DPAD_RIGHT, alt = true)),
        ShortcutBinding(ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutChord(KeyEvent.KEYCODE_P)),
        ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B)),
        ShortcutBinding(ShortcutAction.TOGGLE_ERASER, ShortcutChord(KeyEvent.KEYCODE_E)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_M)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_O)),
        ShortcutBinding(ShortcutAction.HAND_TOOL, ShortcutChord(KeyEvent.KEYCODE_H)),
        ShortcutBinding(ShortcutAction.HAND_TOOL, ShortcutChord(KeyEvent.KEYCODE_SPACE), ShortcutTrigger.HOLD),
        ShortcutBinding(ShortcutAction.ROTATE_VIEW_TOOL, ShortcutChord(KeyEvent.KEYCODE_R)),
        ShortcutBinding(ShortcutAction.ROTATE_VIEW_TOOL, ShortcutChord(KeyEvent.KEYCODE_SPACE, shift = true), ShortcutTrigger.HOLD),
        ShortcutBinding(ShortcutAction.ROTATE_VIEW_LEFT, ShortcutChord(KeyEvent.KEYCODE_MINUS)),
        ShortcutBinding(ShortcutAction.ROTATE_VIEW_RIGHT, ShortcutChord(KeyEvent.KEYCODE_6, shift = true)),
        ShortcutBinding(ShortcutAction.EYEDROPPER_TOOL, ShortcutChord(KeyEvent.KEYCODE_I)),
        ShortcutBinding(ShortcutAction.SHAPE_TOOL, ShortcutChord(KeyEvent.KEYCODE_U)),
        ShortcutBinding(ShortcutAction.TEXT_TOOL, ShortcutChord(KeyEvent.KEYCODE_T)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_DECREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET)),
        ShortcutBinding(ShortcutAction.PREVIOUS_BRUSH, ShortcutChord(KeyEvent.KEYCODE_COMMA)),
        ShortcutBinding(ShortcutAction.NEXT_BRUSH, ShortcutChord(KeyEvent.KEYCODE_PERIOD)),
        ShortcutBinding(ShortcutAction.FIT_PAGE, ShortcutChord(KeyEvent.KEYCODE_AT, ctrl = true)),
        ShortcutBinding(ShortcutAction.TOGGLE_PANELS, ShortcutChord(KeyEvent.KEYCODE_TAB)),
    )

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

    fun restoreActionDefaults(bindings: List<ShortcutBinding>, action: ShortcutAction): List<ShortcutBinding> {
        val defaults = defaultBindings.filter { it.action == action }
        val withoutAction = bindings.filterNot { it.action == action }
        return withoutAction.filterNot { existing ->
            defaults.any { default -> existing.deviceDescriptor == default.deviceDescriptor && sameInput(existing.chord, default.chord) }
        } + defaults
    }

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
