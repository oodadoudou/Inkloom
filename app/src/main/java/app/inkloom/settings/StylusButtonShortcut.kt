package app.inkloom.settings

object StylusButtonShortcut {
    fun actionFor(action: StylusButtonAction): ShortcutAction? = when (action) {
        StylusButtonAction.ERASER -> ShortcutAction.TOGGLE_ERASER
        StylusButtonAction.LASSO -> ShortcutAction.SELECT_TOOL
        StylusButtonAction.HAND -> ShortcutAction.HAND_TOOL
        StylusButtonAction.EYEDROPPER -> ShortcutAction.EYEDROPPER_TOOL
        StylusButtonAction.UNDO, StylusButtonAction.REDO, StylusButtonAction.NONE -> null
    }

    fun activation(action: StylusButtonAction, trigger: ShortcutTrigger, phase: ShortcutPhase): ShortcutActivation? {
        val shortcutAction = actionFor(action) ?: return null
        if (phase == ShortcutPhase.RELEASE && trigger != ShortcutTrigger.HOLD) return null
        return ShortcutActivation(shortcutAction, phase, trigger)
    }
}
