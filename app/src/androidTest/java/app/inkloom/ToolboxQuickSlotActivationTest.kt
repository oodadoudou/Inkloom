package app.inkloom

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutBinding
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.settings.captureToolboxWorkspace
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ToolboxQuickSlotActivationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val presetColor = 0xFFAA2200.toInt()

    private val settingsRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val targetWorkspace = AppSettings(
                    showPageRail = false,
                    showToolbar = false,
                    showLayerPanel = true,
                    layerPanelDock = WorkspacePanelDock.LEFT,
                ).captureToolboxWorkspace()
                val bindings = CspShortcutResolver.defaultBindings + listOf(
                    ShortcutBinding(ShortcutAction.QUICK_SLOT_1, ShortcutChord(KeyEvent.KEYCODE_F12)),
                    ShortcutBinding(ShortcutAction.QUICK_SLOT_2, ShortcutChord(KeyEvent.KEYCODE_F11)),
                )
                AppSettingsStore(context).save(
                    AppSettings(
                        language = UiLanguage.ENGLISH,
                        shortcutBindings = bindings,
                        toolboxQuickSlots = listOf(
                            ToolboxQuickSlot(ToolboxQuickSlotKind.TOOL_PRESET, "Red 23", size = 23f, opacity = .55f, colorArgb = presetColor),
                            ToolboxQuickSlot(ToolboxQuickSlotKind.WORKSPACE, "Focus", workspace = targetWorkspace),
                        ),
                    ),
                )
                try {
                    base.evaluate()
                } finally {
                    context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear().commit()
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(settingsRule).around(composeRule)

    @Test fun toolPresetSlotAppliesSizeOpacityAndColor() {
        openEditor()
        val canvas = findCanvas(composeRule.activity.window.decorView) ?: error("Ink canvas not found")

        composeRule.activity.runOnUiThread {
            canvas.onKeyDown(KeyEvent.KEYCODE_F12, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12))
        }
        composeRule.waitForIdle()

        assertEquals(23f, canvas.baseWidth, .001f)
        assertEquals(.55f, canvas.inkOpacity, .001f)
        assertEquals(presetColor, canvas.inkColor)
    }

    @Test fun workspaceSlotAppliesPersistedPanelLayout() {
        openEditor()
        val canvas = findCanvas(composeRule.activity.window.decorView) ?: error("Ink canvas not found")

        composeRule.activity.runOnUiThread {
            canvas.onKeyDown(KeyEvent.KEYCODE_F11, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F11))
        }
        composeRule.waitForIdle()
        val restored = AppSettingsStore(composeRule.activity).load()

        assertEquals(false, restored.showPageRail)
        assertEquals(false, restored.showToolbar)
        assertEquals(true, restored.showLayerPanel)
        assertEquals(WorkspacePanelDock.LEFT, restored.layerPanelDock)
    }

    private fun openEditor() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
    }

    private fun findCanvas(view: View): InkCanvasView? {
        if (view is InkCanvasView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findCanvas(view.getChildAt(index))?.let { return it }
        return null
    }
}
