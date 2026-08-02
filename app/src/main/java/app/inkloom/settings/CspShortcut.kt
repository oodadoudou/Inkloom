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

object CspShortcutResolver {
    fun resolve(chord: ShortcutChord): ShortcutAction? = with(chord) {
        when {
            ctrl && keyCode == KeyEvent.KEYCODE_Z && shift -> ShortcutAction.REDO
            ctrl && keyCode == KeyEvent.KEYCODE_Z -> ShortcutAction.UNDO
            ctrl && keyCode == KeyEvent.KEYCODE_Y -> ShortcutAction.REDO
            ctrl && keyCode == KeyEvent.KEYCODE_0 -> ShortcutAction.FIT_PAGE
            ctrl && keyCode == KeyEvent.KEYCODE_S -> ShortcutAction.SAVE_CHECKPOINT
            ctrl && keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> ShortcutAction.OPACITY_DECREASE
            ctrl && keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ShortcutAction.OPACITY_INCREASE
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_P -> ShortcutAction.PEN_OR_PENCIL_GROUP
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_B -> ShortcutAction.BRUSH_GROUP
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_E -> ShortcutAction.TOGGLE_ERASER
            !ctrl && !alt && keyCode in setOf(KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_O) -> ShortcutAction.SELECT_TOOL
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> ShortcutAction.BRUSH_SIZE_DECREASE
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ShortcutAction.BRUSH_SIZE_INCREASE
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_COMMA -> ShortcutAction.PREVIOUS_BRUSH
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_PERIOD -> ShortcutAction.NEXT_BRUSH
            !ctrl && !alt && keyCode == KeyEvent.KEYCODE_TAB -> ShortcutAction.TOGGLE_PANELS
            else -> null
        }
    }

    fun isRepeatable(action: ShortcutAction): Boolean = action in setOf(
        ShortcutAction.BRUSH_SIZE_DECREASE,
        ShortcutAction.BRUSH_SIZE_INCREASE,
        ShortcutAction.OPACITY_DECREASE,
        ShortcutAction.OPACITY_INCREASE,
    )
}
