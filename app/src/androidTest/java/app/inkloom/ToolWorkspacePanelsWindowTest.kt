package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
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
    @Test fun liquifyToolPropertyShowsRealForceDistortionAndMomentumControls() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Tool property view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Liquify").performClick()
        compose.onNodeWithText("Force").assertExists()
        compose.onNodeWithText("Distortion").assertExists()
        compose.onNodeWithText("Momentum").assertExists()
    }

    @Test fun fillToolPropertyTogglesLockedAndEditingLayerReferenceExclusions() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Tool property view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Paint bucket").performClick()
        compose.onNodeWithText("Exclude draft layers").assertExists()
        compose.onNodeWithText("Exclude locked layers").performScrollTo().performClick()
        compose.onNodeWithText("Exclude editing layer").performScrollTo().performClick()
        compose.waitUntil(5_000) { AppSettingsStore(compose.activity).load().let {
            it.excludeLockedLayersFromReference && it.excludeActiveLayerFromReference } }
    }

    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun actionsOpenDockableSubtoolsAndSelectionChoiceChangesTheRealTool() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Sub tools view").performClick()
        compose.onNodeWithText("Done").performClick()
        assertTrue(EditorPanel.SUBTOOLS in EditorPanelState(compose.activity).layout.open)
        compose.onNodeWithContentDescription("Selection").performClick()
        compose.onNodeWithText("Magic wand").performClick()
        compose.waitForIdle()
        assertEquals(SelectionShape.MAGIC_WAND, AppSettingsStore(compose.activity).load().selectionShape)
    }

    @Test fun gradientPresetSavedFromToolControlsSurvivesReopeningThePanel() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Gradient").performClick()
        compose.onNodeWithContentDescription("Gradient controls").performClick()
        compose.onNodeWithTag("gradient-preset-save").performClick()
        compose.onNodeWithTag("gradient-preset-name").performTextInput("Comic shade")
        compose.onNodeWithTag("gradient-preset-confirm").performClick()
        compose.waitForIdle()
        assertEquals("Comic shade", AppSettingsStore(compose.activity).load().gradientPresets.presets.single().name)
        compose.onNodeWithContentDescription("Gradient controls").performClick()
        compose.onNodeWithContentDescription("Gradient controls").performClick()
        compose.onNodeWithTag("gradient-preset-choose").performClick()
        compose.onNodeWithText("Comic shade").performClick()
        compose.waitForIdle()
        assertEquals(1, AppSettingsStore(compose.activity).load().gradientPresets.presets.size)
    }

    @Test fun toolbarReactivatesBrushTabBeforeClosingItAndLeavesSiblingOpen() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Sub tools view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Brushes").performClick()
        val focused = EditorPanelState(compose.activity).layout
        assertTrue(EditorPanel.BRUSHES in focused.open)
        assertTrue(EditorPanel.SUBTOOLS in focused.open)
        compose.onNodeWithContentDescription("Brushes").performClick()
        val closed = EditorPanelState(compose.activity).layout
        assertTrue(EditorPanel.BRUSHES !in closed.open)
        assertTrue(EditorPanel.SUBTOOLS in closed.open)
    }

    @Test fun subtoolPaletteChangesGradientAndLineCorrectionModes() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Sub tools view").performClick()
        compose.onNodeWithText("Done").performClick()
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
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Tool property view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Gradient").performClick()
        compose.onNodeWithTag("workspace-gradient-dither").performClick()
        compose.waitForIdle()
        assertEquals(false, AppSettingsStore(compose.activity).load().gradientDither)
    }

    @Test fun longPressDragMovesToolAcrossPaletteGroupSeparatorAndPersistsIt() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Tool property view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Customize tool rail").performClick()
        val brush = compose.onNodeWithContentDescription("Reorder tool Brush")
        val selection = compose.onNodeWithContentDescription("Reorder tool Selection")
        val distance = selection.fetchSemanticsNode().boundsInRoot.center.y - brush.fetchSemanticsNode().boundsInRoot.center.y
        brush.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance + 35f), 700)
            up()
        }
        compose.waitForIdle()
        val entries = app.inkloom.settings.ToolPaletteLayout.decode(AppSettingsStore(compose.activity).load().toolPaletteLayout)
        val brushIndex = entries.indexOf(app.inkloom.settings.ToolPaletteEntry.Tool(app.inkloom.model.EditorTool.PEN))
        val firstSeparator = entries.indexOf(app.inkloom.settings.ToolPaletteEntry.Separator)
        assertTrue("Brush must cross the first separator", brushIndex > firstSeparator)
    }
}
