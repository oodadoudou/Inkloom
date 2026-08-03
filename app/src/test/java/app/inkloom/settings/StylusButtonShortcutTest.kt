package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StylusButtonShortcutTest {
    @Test fun `temporary eraser emits matching press and release activations`() {
        assertEquals(
            ShortcutActivation(ShortcutAction.TOGGLE_ERASER, ShortcutPhase.PRESS, ShortcutTrigger.HOLD),
            StylusButtonShortcut.activation(StylusButtonAction.ERASER, ShortcutTrigger.HOLD, ShortcutPhase.PRESS),
        )
        assertEquals(
            ShortcutActivation(ShortcutAction.TOGGLE_ERASER, ShortcutPhase.RELEASE, ShortcutTrigger.HOLD),
            StylusButtonShortcut.activation(StylusButtonAction.ERASER, ShortcutTrigger.HOLD, ShortcutPhase.RELEASE),
        )
    }

    @Test fun `tap actions do not emit release and commands remain locally handled`() {
        assertEquals(
            ShortcutActivation(ShortcutAction.SELECT_TOOL, ShortcutPhase.PRESS, ShortcutTrigger.PRESS),
            StylusButtonShortcut.activation(StylusButtonAction.LASSO, ShortcutTrigger.PRESS, ShortcutPhase.PRESS),
        )
        assertNull(StylusButtonShortcut.activation(StylusButtonAction.LASSO, ShortcutTrigger.PRESS, ShortcutPhase.RELEASE))
        assertNull(StylusButtonShortcut.activation(StylusButtonAction.UNDO, ShortcutTrigger.HOLD, ShortcutPhase.PRESS))
        assertNull(StylusButtonShortcut.activation(StylusButtonAction.NONE, ShortcutTrigger.HOLD, ShortcutPhase.PRESS))
    }

    @Test fun `all temporary tools map into the shared shortcut registry`() {
        assertEquals(ShortcutAction.TOGGLE_ERASER, StylusButtonShortcut.actionFor(StylusButtonAction.ERASER))
        assertEquals(ShortcutAction.SELECT_TOOL, StylusButtonShortcut.actionFor(StylusButtonAction.LASSO))
        assertEquals(ShortcutAction.HAND_TOOL, StylusButtonShortcut.actionFor(StylusButtonAction.HAND))
        assertEquals(ShortcutAction.EYEDROPPER_TOOL, StylusButtonShortcut.actionFor(StylusButtonAction.EYEDROPPER))
    }
}
