package app.inkloom

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.geometry.Offset
import app.inkloom.importer.BrushGroupStore
import app.inkloom.document.PageTemplateStore
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.WorkspacePanelDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkloomLaunchTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val forceEnglishRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                ApplicationProvider.getApplicationContext<android.content.Context>()
                    .getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("language", "ENGLISH")
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showPageRail", true)
                    .putString("pagePanelDock", "LEFT")
                    .putString("brushPanelDock", "RIGHT")
                    .putString("layerPanelDock", "RIGHT")
                    .remove("shortcutPenGroup")
                    .remove("shortcutPencilGroup")
                    .remove("shortcutBrushGroup")
                    .remove("toolboxQuickSlots")
                    .remove("workspaceLayouts")
                    .commit()
                BrushGroupStore(ApplicationProvider.getApplicationContext()).save(
                    listOf("Toolbox/Pens", "Toolbox/Pencils", "Toolbox/Brushes"),
                )
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(forceEnglishRule).around(composeRule)

    private fun createNotebookAndOpenEditor() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create notebook").assertIsDisplayed()
        composeRule.onNodeWithText("Create").performClick()
    }

    @Test fun libraryScreenLaunches() {
        composeRule.onNodeWithText("Inkloom").assertIsDisplayed()
    }

    @Test fun rotationKeepsEditorAndRestoresOrientationSpecificWorkspace() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Toggle pages").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodes(hasContentDescription("Add page")).fetchSemanticsNodes().isEmpty())

        try {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            composeRule.waitUntil(10_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            composeRule.onNodeWithContentDescription("Search this note").assertIsDisplayed()
            assertEquals(true, AppSettingsStore(composeRule.activity).load().showPageRail)

            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            composeRule.waitUntil(10_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.onNodeWithContentDescription("Search this note").assertIsDisplayed()
            assertEquals(false, AppSettingsStore(composeRule.activity).load().showPageRail)
        } finally {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    @Test fun pagesBrushesAndLayersCanShareTheBottomDock() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onNodeWithContentDescription("Change layers panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()

        composeRule.onNodeWithContentDescription("Bottom workspace panels").assertIsDisplayed()
        composeRule.onNodeWithText("Brush Library").assertIsDisplayed()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
        val restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(WorkspacePanelDock.BOTTOM, restored.pagePanelDock)
        assertEquals(WorkspacePanelDock.BOTTOM, restored.brushPanelDock)
        assertEquals(WorkspacePanelDock.BOTTOM, restored.layerPanelDock)
    }

    @Test fun builtInWorkspacesSwitchTheCompleteEditorLayout() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Drawing workspace").performClick()
        composeRule.onNodeWithText("Brush Library").assertIsDisplayed()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
        var restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(false, restored.showPageRail)
        assertEquals(true, restored.showBrushPanel)
        assertEquals(true, restored.showLayerPanel)

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Reading workspace").performClick()
        restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(true, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        assertEquals(false, restored.showToolbar)

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Minimal workspace").performClick()
        restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(false, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        assertEquals(true, restored.showToolbar)

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Reset current workspace").performClick()
        restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(true, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        assertEquals(true, restored.showToolbar)
    }

    @Test fun settingsExposeInputAndToolControls() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Draw with finger").assertIsDisplayed()
    }

    @Test fun settingsExposeConflictSafeFullLibraryBackup() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(32)

        composeRule.onNodeWithText("Export full library backup").assertIsDisplayed()
        composeRule.onNodeWithText("Restore library backup").assertIsDisplayed()
    }

    @Test fun shortcutEditorExposesPortableToolboxProfiles() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNodeWithText("Search actions or keys").assertIsDisplayed()
        composeRule.onNodeWithText("Brush groups").assertIsDisplayed()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(65)

        composeRule.onNodeWithText("Import profile").assertIsDisplayed()
        composeRule.onNodeWithText("Export profile").assertIsDisplayed()
    }

    @Test fun shortcutEditorMapsCspRolesToRealBrushGroups() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(4)

        composeRule.onNodeWithText("BRUSH GROUP TARGETS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Automatic")[0].performClick()
        composeRule.onNodeWithText("Toolbox/Pens").performClick()

        assertEquals("Toolbox/Pens", AppSettingsStore(composeRule.activity).load().shortcutBrushGroupTargets.pen)
    }

    @Test fun shortcutEditorConfiguresAnArbitraryGroupQuickSlot() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(4)
        composeRule.onNodeWithText("Configure 8 quick slots").performClick()

        composeRule.onNodeWithText("Toolbox quick slots").assertIsDisplayed()
        composeRule.onNodeWithText("Group").performClick()
        composeRule.onNodeWithContentDescription("Brush group quick slot value").performClick()
        composeRule.onNodeWithText("Toolbox/Pens").performClick()

        val slot = AppSettingsStore(composeRule.activity).load().toolboxQuickSlots.first()
        assertEquals(ToolboxQuickSlotKind.BRUSH_GROUP, slot.kind)
        assertEquals("Toolbox/Pens", slot.groupPath)
    }

    @Test fun stickyNoteDoubleTapOpensItsInkSubcanvas() {
        val noteText = "Sticky canvas test"
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Note").performClick()
        composeRule.onNodeWithText("New sticky note").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput(noteText)
        composeRule.onNodeWithText("Insert").performClick()

        composeRule.onNodeWithText(noteText).performTouchInput { doubleClick() }

        composeRule.onNodeWithText("Write inside sticky note").assertIsDisplayed()
        composeRule.onNodeWithText("System pen").assertIsDisplayed()
    }

    @Test fun editorExposesUnifiedPageAndPdfSearch() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Search this note").performClick()

        composeRule.onNodeWithText("Page, note, or PDF text").assertIsDisplayed()
    }

    @Test fun newPageChooserSupportsFavoritesAndNotebookDefaults() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Add page").performClick()

        composeRule.onNodeWithText("Choose template for new page").assertIsDisplayed()
        composeRule.onNodeWithText("Import image/PDF").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Favorite Grid template").performClick()
        composeRule.onNodeWithContentDescription("Set Grid as notebook default").performClick()

        val library = PageTemplateStore(composeRule.activity.filesDir).load()
        assertTrue("builtin-grid" in library.favoriteIds)
        assertTrue(library.notebookDefaults.values.any { it == "builtin-grid" })

        composeRule.onNodeWithContentDescription("Use Grid template").performClick()
        composeRule.onNodeWithText("Page 2", substring = true).assertIsDisplayed()
    }

    @Test fun editorInsertsAValidatedLinkCard() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Link").performClick()
        composeRule.onNodeWithText("Insert link").assertIsDisplayed()

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Inkloom docs")
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextInput("example.com/inkloom")
        composeRule.onNodeWithText("Insert").performClick()

        composeRule.onNodeWithText("Inkloom docs").assertIsDisplayed()
        composeRule.onNodeWithText("example.com/inkloom").assertIsDisplayed()
    }

    @Test fun floatingToolbarDragPersistsItsFreeCanvasPosition() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        val beforeX = preferences.getFloat("toolbarOffsetXDp", 16f)
        val beforeY = preferences.getFloat("toolbarOffsetYDp", 16f)

        composeRule.onNodeWithContentDescription("Drag toolbar").performTouchInput {
            swipe(center, center + Offset(180f, 140f), durationMillis = 600)
        }
        composeRule.waitForIdle()

        val afterX = preferences.getFloat("toolbarOffsetXDp", 16f)
        val afterY = preferences.getFloat("toolbarOffsetYDp", 16f)
        assertTrue("Toolbar drag should persist a new free position", afterX != beforeX || afterY != beforeY)
        assertTrue(preferences.getString("toolbarDock", null) == "FREE")
    }

    @Test fun compactToolbarKeepsBrushAndDockControlsInAContextPanel() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Tool controls").performClick()

        composeRule.onNodeWithText("Pen controls").assertIsDisplayed()
        composeRule.onNodeWithText("Palette position").assertIsDisplayed()
        composeRule.onNodeWithText("Opacity", substring = true).assertIsDisplayed()
    }

    @Test fun brushPanelCanFloatMoveResizeAndReopenInPlace() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        val beforeX = preferences.getFloat("brushPanelXDp", 720f)
        val beforeWidth = preferences.getFloat("brushPanelWidthDp", 330f)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Float").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(-120f, 90f), durationMillis = 500)
        }
        composeRule.onNodeWithContentDescription("Resize brushes panel").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(70f, -60f), durationMillis = 500)
        }
        composeRule.waitForIdle()

        assertTrue(preferences.getString("brushPanelDock", null) == "FREE")
        assertTrue(preferences.getFloat("brushPanelXDp", beforeX) != beforeX)
        assertTrue(preferences.getFloat("brushPanelWidthDp", beforeWidth) != beforeWidth)

        composeRule.onNodeWithContentDescription("Close brushes panel").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Resize brushes panel").assertIsDisplayed()
    }

    @Test fun selectionControlsExposePersistentFreeformAndRectangleModes() {
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        val original = preferences.getString("selectionShape", "FREEFORM")
        try {
            createNotebookAndOpenEditor()
            composeRule.onNodeWithContentDescription("Select").performClick()
            composeRule.onNodeWithContentDescription("Tool controls").performClick()

            composeRule.onNodeWithText("Freeform").assertIsDisplayed()
            composeRule.onNodeWithText("Rectangle").performClick()
            composeRule.waitForIdle()

            assertTrue(preferences.getString("selectionShape", null) == "RECTANGLE")
        } finally {
            preferences.edit().putString("selectionShape", original).commit()
        }
    }

    @Test fun layerPanelDuplicatesAndMergesAContentLayer() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onAllNodes(hasContentDescription("Layer actions")).onFirst().performClick()
        composeRule.onNodeWithText("Duplicate layer").performClick()

        composeRule.onNodeWithText("Objects copy").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Layer actions")).onFirst().performClick()
        composeRule.onNodeWithText("Merge down").performClick()

        assertTrue(composeRule.onAllNodes(hasText("Objects copy")).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Objects").assertIsDisplayed()
    }

    @Test fun layerPanelCreatesFoldersMovesLayersAndCollapsesChildren() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onNodeWithContentDescription("Add layer folder").performClick()
        composeRule.onNodeWithContentDescription("Add layer").performClick()

        composeRule.onNodeWithText("Folder 1").assertIsDisplayed()
        composeRule.onNodeWithText("Layer 1").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Layer actions")).onFirst().performClick()
        composeRule.onNodeWithText("Move into Folder 1").performClick()
        composeRule.onNodeWithContentDescription("Collapse folder").performClick()

        assertTrue(composeRule.onAllNodes(hasText("Layer 1")).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Folder 1").assertIsDisplayed()
    }
}
