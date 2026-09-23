package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EditorPanel
import app.inkloom.ui.EditorPanelState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class NamedPanelLayoutWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun workspaceBackupRestoresPersistentDockTreeAndNamedLayouts() {
        val state = EditorPanelState(compose.activity, AppSettings())
        val start = state.layout.copy(open = setOf(EditorPanel.LAYERS))
        val dock = app.inkloom.settings.EditorPanelLayouts.move(start, "layers", null,
            app.inkloom.settings.PanelDropTarget("canvas", app.inkloom.settings.PanelEdge.RIGHT), start.floating[2].rect)
        compose.runOnIdle {
            state.commit(dock.copy(locked = true))
            state.saveNamedLayout("Docked drawing", AppSettings())
        }
        val bytes = app.inkloom.settings.AppSettingsBackupCodec.encode(AppSettings(), panelWorkspace = state.backup())
        compose.runOnIdle {
            state.commit(app.inkloom.settings.EditorPanelLayout())
            state.deleteNamedLayout("Docked drawing")
            state.restoreBackup(requireNotNull(app.inkloom.settings.AppSettingsBackupCodec.decode(bytes).getOrThrow().panelWorkspace))
        }
        val reopened = EditorPanelState(compose.activity, AppSettings())
        org.junit.Assert.assertEquals(dock.copy(locked = true), reopened.layout)
        org.junit.Assert.assertEquals(state.namedLayouts, reopened.namedLayouts)
        assertTrue(reopened.namedLayouts.entries.any { it.name == "Docked drawing" })
    }

    @Test fun layoutLockCanBeSavedRestoredAndUnlockedWithoutClosingPanels() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Lock layout").performClick()
        assertTrue(EditorPanelState(compose.activity, AppSettings()).layout.locked)
        compose.onNodeWithContentDescription("Resize layers panel · bottom edge").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close layers panel").assertExists()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Save panel layout…").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Locked drawing")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Unlock layout").performClick()
        org.junit.Assert.assertFalse(EditorPanelState(compose.activity, AppSettings()).layout.locked)
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Locked drawing").performScrollTo().performClick()
        assertTrue(EditorPanelState(compose.activity, AppSettings()).layout.locked)
    }

    @Test fun saveCloseAndRestoreNamedLayoutThroughActionsMenu() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertExists()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Save panel layout…").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Drawing layers")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Close layers panel").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Drawing layers").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertExists()
        val restored = EditorPanelState(compose.activity, AppSettings())
        assertTrue(EditorPanel.LAYERS in restored.layout.open)
        assertTrue(restored.namedLayouts.entries.any { it.name == "Drawing layers" })
    }

    @Test fun windowManagerRestoresNearbyWithoutResettingItsTabGroup() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val before = EditorPanelState(compose.activity, AppSettings()).layout
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Nearby view").performScrollTo().performClick()
        assertTrue(EditorPanel.NEARBY in EditorPanelState(compose.activity, AppSettings()).layout.open)
        compose.onNodeWithContentDescription("Hide Nearby view").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/inkloom-window-manager.png").close()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Close nearby panel").assertExists()
        compose.onNodeWithContentDescription("Close nearby panel").performClick()
        assertTrue(EditorPanel.NEARBY !in EditorPanelState(compose.activity, AppSettings()).layout.open)
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Nearby view").performScrollTo().performClick()
        val restored = EditorPanelState(compose.activity, AppSettings()).layout
        assertTrue(EditorPanel.NEARBY in restored.open)
        org.junit.Assert.assertEquals(before.floating.associate { it.node.id to it.rect }, restored.floating.associate { it.node.id to it.rect })
        org.junit.Assert.assertEquals(before.floating.flatMap { app.inkloom.settings.EditorPanelLayouts.members(it.node) },
            restored.floating.flatMap { app.inkloom.settings.EditorPanelLayouts.members(it.node) })
    }

    @Test fun windowManagerKeepsNotebookAndLayerSettingsInSyncEvenWhenLayoutIsLocked() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Lock layout").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Layers view").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Pages view").performScrollTo().performClick()
        compose.waitForIdle()
        val settings = app.inkloom.settings.AppSettingsStore(compose.activity).load()
        assertTrue(settings.showLayerPanel)
        assertTrue(settings.showPageRail)
        assertTrue(EditorPanelState(compose.activity, AppSettings()).layout.locked)
        compose.onNodeWithContentDescription("Hide Layers view").performScrollTo().performClick()
        compose.waitForIdle()
        org.junit.Assert.assertFalse(app.inkloom.settings.AppSettingsStore(compose.activity).load().showLayerPanel)
    }
}
