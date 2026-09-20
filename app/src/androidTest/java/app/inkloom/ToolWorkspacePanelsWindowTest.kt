package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.EditorPanel
import app.inkloom.settings.SelectionShape
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.GradientKind
import app.inkloom.ui.EditorPanelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class ToolWorkspacePanelsWindowTest {
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

    @Test fun actionsOpenDockableSubtoolsAndSelectionChoiceChangesTheRealTool() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Sub tools").performScrollTo().performClick()
        assertTrue(EditorPanel.SUBTOOLS in EditorPanelState(compose.activity, AppSettings()).layout.open)
        compose.onNodeWithContentDescription("Selection").performClick()
        compose.onNodeWithText("Magic wand").performClick()
        compose.waitForIdle()
        assertEquals(SelectionShape.MAGIC_WAND, AppSettingsStore(compose.activity).load().selectionShape)
    }

    @Test fun toolbarReactivatesBrushTabBeforeClosingItAndLeavesSiblingOpen() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Sub tools").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Brushes").performClick()
        val focused = EditorPanelState(compose.activity, AppSettings()).layout
        assertTrue(EditorPanel.BRUSHES in focused.open)
        assertTrue(EditorPanel.SUBTOOLS in focused.open)
        compose.onNodeWithContentDescription("Brushes").performClick()
        val closed = EditorPanelState(compose.activity, AppSettings()).layout
        assertTrue(EditorPanel.BRUSHES !in closed.open)
        assertTrue(EditorPanel.SUBTOOLS in closed.open)
    }

    @Test fun subtoolPaletteChangesGradientAndLineCorrectionModes() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Sub tools").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gradient").performClick()
        compose.onNodeWithText("Diamond").performClick()
        compose.waitForIdle()
        assertEquals(GradientKind.DIAMOND, AppSettingsStore(compose.activity).load().gradientKind)
        compose.onNodeWithContentDescription("Correct line").performClick()
        compose.onNodeWithText("Simplify").performClick()
        compose.waitForIdle()
        assertEquals(CorrectLineMode.SIMPLIFY, AppSettingsStore(compose.activity).load().correctLineMode)
    }

    @Test fun toolPropertiesPaletteChangesActiveGradientSetting() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Tool property").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gradient").performClick()
        compose.onNodeWithTag("workspace-gradient-dither").performClick()
        compose.waitForIdle()
        assertEquals(false, AppSettingsStore(compose.activity).load().gradientDither)
    }
}
