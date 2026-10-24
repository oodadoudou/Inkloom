package app.inkloom

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import app.inkloom.settings.BuiltInWorkspace
import app.inkloom.settings.BuiltInWorkspaces
import app.inkloom.settings.DockDisplay
import app.inkloom.settings.EditorPanel
import app.inkloom.settings.EditorPanelLayout
import app.inkloom.settings.EditorPanelLayouts
import app.inkloom.ui.EditorPanelState
import app.inkloom.ui.EditorPanels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** CSP's palette dock display on a real window: icons with pop-up palettes, a hidden dock, minimized windows. */
class PaletteDockWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun host(layout: EditorPanelLayout): EditorPanelState {
        lateinit var state: EditorPanelState
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity)
            state.commit(layout)
            compose.activity.setContent { MaterialTheme {
                EditorPanels(state, {}, canvas = { Text("Canvas") }, content = { Text("Body ${it.name}") })
            } }
        }
        compose.waitForIdle()
        return state
    }
    private fun click(description: String) { compose.onNodeWithContentDescription(description).performClick(); compose.waitForIdle() }

    @Test fun iconDockOpensItsPalettesAsPopUpsThatCloseOnAnOutsideTapOrBack() {
        val state = host(BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout)
        compose.onNodeWithText("Body WHEEL").assertExists()
        click("Show palette dock as icons")
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        assertEquals(1, state.layout.iconDocks.size)
        // The palettes stay open: they show as icons, and one at a time as a pop-up.
        assertEquals(setOf(EditorPanel.WHEEL, EditorPanel.LAYERS), state.layout.open)
        click("Wheel palette")
        compose.onNodeWithText("Body WHEEL").assertExists()
        assertEquals(EditorPanel.WHEEL, state.popup)
        compose.onRoot().performTouchInput { click(Offset(40f, 300f)) }
        compose.waitForIdle()
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        assertNull(state.popup)

        // Opening a palette from a toolbar (focus) shows its pop-up; the toolbar closing it keeps the icon.
        compose.runOnUiThread { state.focus(EditorPanel.LAYERS) }
        compose.waitForIdle()
        compose.onNodeWithText("Body LAYERS").assertExists()
        assertTrue(state.isActive(EditorPanel.LAYERS))
        assertTrue(state.shown(EditorPanel.LAYERS))
        compose.runOnUiThread { state.hide(EditorPanel.LAYERS) }
        compose.waitForIdle()
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        assertTrue(EditorPanel.LAYERS in state.layout.open)
        assertFalse(state.shown(EditorPanel.LAYERS))

        click("Layers palette")
        compose.onNodeWithText("Body LAYERS").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        assertTrue(EditorPanel.LAYERS in state.layout.open)

        click("Expand palette dock")
        compose.onNodeWithText("Body WHEEL").assertExists()
        compose.onNodeWithText("Body LAYERS").assertExists()
        assertTrue(state.layout.iconDocks.isEmpty())
    }

    @Test fun hiddenDockLeavesAStripThatShowsItAgainAndStillPopsUpAPalette() {
        val painting = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout
        val state = host(painting)
        click("Hide palette dock")
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        assertEquals(DockDisplay.HIDDEN, EditorPanelLayouts.dockDisplay(state.layout, "colors"))
        compose.runOnUiThread { state.focus(EditorPanel.WHEEL) }
        compose.waitForIdle()
        compose.onNodeWithText("Body WHEEL").assertExists()
        click("Close Wheel pop-up")
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        click("Show palette dock")
        compose.onNodeWithText("Body WHEEL").assertExists()
        assertEquals(EditorPanelLayouts.slots(painting, 1000f, 700f), EditorPanelLayouts.slots(state.layout, 1000f, 700f))
    }

    @Test fun aFloatingWindowMinimizesToItsTitleAndRestores() {
        val state = host(EditorPanelLayout(open = setOf(EditorPanel.LAYERS)))
        val stored = state.layout.floating.first { it.node.id == "layers" }.rect
        click("Minimize layers panel")
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        assertEquals(setOf("layers"), state.layout.minimized)
        compose.onNodeWithContentDescription("Resize layers panel · bottom edge").assertDoesNotExist()
        click("Restore layers panel")
        compose.onNodeWithText("Body LAYERS").assertExists()
        assertEquals(stored, state.layout.floating.first { it.node.id == "layers" }.rect)
        assertTrue(state.layout.minimized.isEmpty())
    }

    @Test fun aPaletteSetToDisplayAsATabExpandsTheIconDockUntilItGoesBackToIcons() {
        val state = host(BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout)
        click("Show palette dock as icons")
        compose.onNodeWithContentDescription("Wheel palette").performTouchInput { longClick() }
        compose.onNodeWithText("Display as tab").performClick()
        compose.waitForIdle()
        assertEquals(setOf(EditorPanel.WHEEL), state.layout.tabPalettes)
        click("Wheel palette")
        // The whole dock shows, the wheel in front; nothing pops up and the saved display is still icons.
        compose.onNodeWithText("Body WHEEL").assertExists()
        compose.onNodeWithText("Body LAYERS").assertExists()
        assertNull(state.popup)
        assertEquals(1, state.layout.iconDocks.size)
        click("Show palette dock as icons")
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        assertNull(state.expandedDock)
        // Opening it from a toolbar expands it too; Back folds it again. Layers still pops up.
        compose.runOnUiThread { state.focus(EditorPanel.WHEEL) }
        compose.waitForIdle()
        compose.onNodeWithText("Body LAYERS").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Body WHEEL").assertDoesNotExist()
        click("Layers palette")
        assertEquals(EditorPanel.LAYERS, state.popup)
    }
}
