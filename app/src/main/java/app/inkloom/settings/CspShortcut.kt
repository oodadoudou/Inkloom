package app.inkloom.settings

import android.view.KeyEvent

enum class ShortcutAction {
    PEN_OR_PENCIL_GROUP,
    BRUSH_GROUP,
    TOGGLE_ERASER,
    SELECT_TOOL,
    BRUSH_SIZE_DECREASE,
    BRUSH_SIZE_INCREASE,
    OPACITY_DECREASE,
    OPACITY_INCREASE,
    PREVIOUS_BRUSH,
    NEXT_BRUSH,
    UNDO,
    REDO,
    FIT_PAGE,
    TOGGLE_PANELS,
    SAVE_CHECKPOINT,
}

data class ShortcutChord(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

data class ShortcutBinding(
    val action: ShortcutAction,
    val chord: ShortcutChord,
)

object CspShortcutResolver {
    val defaultBindings: List<ShortcutBinding> = listOf(
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)),
        ShortcutBinding(ShortcutAction.UNDO, ShortcutChord(KeyEvent.KEYCODE_Z, ctrl = true)),
        ShortcutBinding(ShortcutAction.REDO, ShortcutChord(KeyEvent.KEYCODE_Y, ctrl = true)),
        ShortcutBinding(ShortcutAction.FIT_PAGE, ShortcutChord(KeyEvent.KEYCODE_0, ctrl = true)),
        ShortcutBinding(ShortcutAction.SAVE_CHECKPOINT, ShortcutChord(KeyEvent.KEYCODE_S, ctrl = true)),
        ShortcutBinding(ShortcutAction.OPACITY_DECREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.OPACITY_INCREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true)),
        ShortcutBinding(ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutChord(KeyEvent.KEYCODE_P)),
        ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B)),
        ShortcutBinding(ShortcutAction.TOGGLE_ERASER, ShortcutChord(KeyEvent.KEYCODE_E)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_M)),
        ShortcutBinding(ShortcutAction.SELECT_TOOL, ShortcutChord(KeyEvent.KEYCODE_O)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_DECREASE, ShortcutChord(KeyEvent.KEYCODE_LEFT_BRACKET)),
        ShortcutBinding(ShortcutAction.BRUSH_SIZE_INCREASE, ShortcutChord(KeyEvent.KEYCODE_RIGHT_BRACKET)),
        ShortcutBinding(ShortcutAction.PREVIOUS_BRUSH, ShortcutChord(KeyEvent.KEYCODE_COMMA)),
        ShortcutBinding(ShortcutAction.NEXT_BRUSH, ShortcutChord(KeyEvent.KEYCODE_PERIOD)),
        ShortcutBinding(ShortcutAction.TOGGLE_PANELS, ShortcutChord(KeyEvent.KEYCODE_TAB)),
    )

    fun resolve(chord: ShortcutChord, bindings: List<ShortcutBinding> = defaultBindings): ShortcutAction? =
        bindings.firstOrNull { it.chord == chord }?.action

    fun replace(bindings: List<ShortcutBinding>, action: ShortcutAction, chord: ShortcutChord): List<ShortcutBinding> =
        bindings.filterNot { it.action == action || it.chord == chord } + ShortcutBinding(action, chord)

    fun display(chord: ShortcutChord): String = buildList {
        if (chord.ctrl) add("Ctrl")
        if (chord.alt) add("Alt")
        if (chord.shift) add("Shift")
        add(KeyEvent.keyCodeToString(chord.keyCode).removePrefix("KEYCODE_").replace('_', ' '))
    }.joinToString("+")

    fun isRepeatable(action: ShortcutAction): Boolean = action in setOf(
        ShortcutAction.BRUSH_SIZE_DECREASE,
        ShortcutAction.BRUSH_SIZE_INCREASE,
        ShortcutAction.OPACITY_DECREASE,
        ShortcutAction.OPACITY_INCREASE,
    )
}
