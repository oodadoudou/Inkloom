package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionLauncherLayoutTest {
    @Test fun restoresMissingActionsAndMovesWithoutDroppingEssentialCommands() {
        val decoded = SelectionLauncherLayout.decode("TRANSFORM,INVERT,TRANSFORM,unknown")
        assertEquals(SelectionLauncherAction.TRANSFORM, decoded.first())
        assertEquals(SelectionLauncherAction.INVERT, decoded[1])
        assertEquals(SelectionLauncherAction.entries.size, decoded.size)
        val moved = SelectionLauncherLayout.move(decoded, SelectionLauncherAction.INVERT, -1)
        assertEquals(SelectionLauncherAction.INVERT, moved.first())
        assertEquals(SelectionLauncherLayout.default.toSet(), moved.toSet())
        assertEquals(moved, SelectionLauncherLayout.decode(SelectionLauncherLayout.encode(moved)))
    }
}
