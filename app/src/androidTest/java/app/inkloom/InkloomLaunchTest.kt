package app.inkloom

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.geometry.Offset
import app.inkloom.importer.BrushGroupStore
import app.inkloom.document.PageTemplateStore
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutCategory
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.settings.WorkspacePanelKind
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
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                java.io.File(context.filesDir, "page-templates.json").delete()
                context
                    .getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("language", "ENGLISH")
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showPageRail", true)
                    .putBoolean("rulerEnabled", false)
                    .putBoolean("gridSnapEnabled", false)
                    .putBoolean("alignmentGuidesEnabled", false)
                    .putString("pagePanelDock", "LEFT")
                    .putString("brushPanelDock", "RIGHT")
                    .putString("layerPanelDock", "RIGHT")
                    .putFloat("pagePanelXDp", 16f)
                    .putFloat("pagePanelYDp", 16f)
                    .putFloat("pagePanelWidthDp", 160f)
                    .putFloat("pagePanelHeightDp", 640f)
                    .putFloat("brushPanelXDp", 720f)
                    .putFloat("brushPanelYDp", 16f)
                    .putFloat("brushPanelWidthDp", 330f)
                    .putFloat("brushPanelHeightDp", 680f)
                    .putFloat("layerPanelXDp", 720f)
                    .putFloat("layerPanelYDp", 16f)
                    .putFloat("layerPanelWidthDp", 300f)
                    .putFloat("layerPanelHeightDp", 620f)
                    .remove("shortcutPenGroup")
                    .remove("shortcutPencilGroup")
                    .remove("shortcutBrushGroup")
                    .remove("toolboxQuickSlots")
                    .remove("workspaceLayouts")
                    .remove("customWorkspaces")
                    .remove("pagePanelMode")
                    .remove("brushPanelMode")
                    .remove("layerPanelMode")
                    .remove("pagePanelLocked")
                    .remove("brushPanelLocked")
                    .remove("layerPanelLocked")
                    .remove("leftDockStacked")
                    .remove("rightDockStacked")
                    .remove("bottomDockStacked")
                    .remove("freeDockStacked")
                    .remove("leftDockActivePanel")
                    .remove("rightDockActivePanel")
                    .remove("bottomDockActivePanel")
                    .remove("freeDockActivePanel")
                    .remove("panelTabOrder")
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

    @Test fun rulerIsReachableFromCompactToolbarAndPersists() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Show ruler").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            AppSettingsStore(composeRule.activity).load().rulerEnabled
        }

        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Hide ruler").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tool controls").performClick()
        composeRule.onNodeWithText("Ruler angle snap").assertIsDisplayed()
        composeRule.onNodeWithText("5°").performClick()
        composeRule.waitUntil(5_000) {
            AppSettingsStore(composeRule.activity).load().rulerSnapDegrees == 5f
        }
    }

    @Test fun snapGridAndAlignmentGuidesAreReachableFromCompactToolbar() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Show snap grid").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().gridSnapEnabled }

        composeRule.onNodeWithContentDescription("More tools").performClick()
        composeRule.onNodeWithText("Show alignment guides").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().alignmentGuidesEnabled }

        composeRule.onNodeWithContentDescription("Tool controls").performClick()
        composeRule.onNodeWithText("Snap grid spacing").assertIsDisplayed()
        composeRule.onNodeWithText("Alignment guides").assertIsDisplayed()
        composeRule.onNodeWithText("96").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().gridSpacing == 96f }
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

        composeRule.onNodeWithContentDescription("Stack panels in bottom dock").performClick()
        composeRule.onNodeWithContentDescription("bottom panel tabs").assertIsDisplayed()
        assertTrue(AppSettingsStore(composeRule.activity).load().bottomDockStacked)
        composeRule.onNodeWithContentDescription("Brushes tab. Drag to reorder.").performClick()
        composeRule.onNodeWithText("Brush Library").assertIsDisplayed()
        assertEquals(WorkspacePanelKind.BRUSHES, AppSettingsStore(composeRule.activity).load().bottomDockActivePanel)
        composeRule.onNodeWithContentDescription("Layers tab. Drag to reorder.").performClick()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
        assertEquals(WorkspacePanelKind.LAYERS, AppSettingsStore(composeRule.activity).load().bottomDockActivePanel)
        composeRule.onNodeWithContentDescription("Separate bottom panel tabs").performClick()
        assertEquals(false, AppSettingsStore(composeRule.activity).load().bottomDockStacked)
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

    @Test fun customWorkspaceCanBeSavedAppliedDuplicatedAndRenamed() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Drawing workspace").performClick()
        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Save current workspace…").performClick()
        composeRule.onNodeWithText("Save workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().customWorkspaces.size == 1 }

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Reading workspace").performClick()
        assertTrue(AppSettingsStore(composeRule.activity).load().showBrushPanel.not())
        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("My workspace").performClick()
        val applied = AppSettingsStore(composeRule.activity).load()
        assertTrue(applied.showBrushPanel)
        assertTrue(applied.showLayerPanel)

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Manage custom workspaces…").performClick()
        composeRule.onNodeWithText("Custom workspaces").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Manage My workspace").performClick()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().customWorkspaces.size == 2 }
        composeRule.onNodeWithText("My workspace copy").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Manage My workspace copy").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("Workspace name").performTextClearance()
        composeRule.onNodeWithText("Workspace name").performTextInput("Studio")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().customWorkspaces.any { it.name == "Studio" } }
        composeRule.onNodeWithText("Studio").assertIsDisplayed()
    }

    @Test fun workspacePanelsCollapseToIconsAndLockTheirPlacement() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Collapsed").performClick()
        composeRule.onNodeWithContentDescription("Expand Pages panel").assertIsDisplayed()
        assertEquals("COLLAPSED", AppSettingsStore(composeRule.activity).load().pagePanelMode.name)

        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Icon only").performClick()
        assertEquals("ICON_ONLY", AppSettingsStore(composeRule.activity).load().pagePanelMode.name)
        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Lock position and size").performClick()
        assertTrue(AppSettingsStore(composeRule.activity).load().pagePanelLocked)
        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Dock right").assertIsNotEnabled()
        composeRule.onNodeWithText("Expanded").performClick()

        composeRule.onNodeWithContentDescription("Workspaces").performClick()
        composeRule.onNodeWithText("Drawing workspace").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Icon only").performClick()
        composeRule.onNodeWithContentDescription("Expand Brush Library panel").assertIsDisplayed()
        assertEquals("ICON_ONLY", AppSettingsStore(composeRule.activity).load().brushPanelMode.name)

        composeRule.onNodeWithContentDescription("Change layers panel docking").performClick()
        composeRule.onNodeWithText("Collapsed").performClick()
        composeRule.onNodeWithContentDescription("Expand Layers panel").assertIsDisplayed()
        assertEquals("COLLAPSED", AppSettingsStore(composeRule.activity).load().layerPanelMode.name)
    }

    @Test fun settingsExposeInputAndToolControls() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Draw with finger").assertIsDisplayed()
        composeRule.onNodeWithText("Single-finger double tap to undo").assertIsDisplayed()
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

        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true)))
            .performScrollToIndex(16 + ShortcutCategory.entries.size + ShortcutAction.entries.size)
        composeRule.onNodeWithText("Import profile").assertIsDisplayed()
        composeRule.onNodeWithText("Export profile").assertIsDisplayed()
    }

    @Test fun shortcutEditorMapsCspRolesToRealBrushGroups() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(14)

        composeRule.onNodeWithText("BRUSH GROUP TARGETS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Automatic")[0].performClick()
        composeRule.onNodeWithText("Toolbox/Pens").performClick()

        assertEquals("Toolbox/Pens", AppSettingsStore(composeRule.activity).load().shortcutBrushGroupTargets.pen)
    }

    @Test fun shortcutEditorConfiguresAnArbitraryGroupQuickSlot() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(14)
        composeRule.onNodeWithText("Configure 8 quick slots").performClick()

        composeRule.onNodeWithText("Toolbox quick slots").assertIsDisplayed()
        composeRule.onNodeWithText("Group").performClick()
        composeRule.onNodeWithContentDescription("Brush group quick slot value").performClick()
        composeRule.onNodeWithText("Toolbox/Pens").performClick()

        val slot = AppSettingsStore(composeRule.activity).load().toolboxQuickSlots.first()
        assertEquals(ToolboxQuickSlotKind.BRUSH_GROUP, slot.kind)
        assertEquals("Toolbox/Pens", slot.groupPath)
    }

    @Test fun shortcutEditorConfiguresRotaryBrushSizeControl() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Draw with finger"))).performScrollToIndex(29)
        composeRule.onNodeWithText("Customize shortcuts").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tap Change", substring = true))).performScrollToIndex(5)
        composeRule.onNodeWithContentDescription("Rotary / touch ring quick slot value").performClick()
        composeRule.onNodeWithText("Brush size").performClick()

        val binding = AppSettingsStore(composeRule.activity).load().shortcutAxisBindings.single { it.axis == ToolboxAxis.ROTARY }
        assertEquals(ToolboxAxisMode.BRUSH_SIZE, binding.mode)
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

        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Icon only").performClick()
        val compactBeforeX = preferences.getFloat("brushPanelXDp", beforeX)
        composeRule.onNodeWithContentDescription("Drag Brush Library panel").performTouchInput {
            swipe(Offset(width * .8f, center.y), Offset(width * .2f, center.y), durationMillis = 400)
        }
        composeRule.waitUntil(5_000) { preferences.getFloat("brushPanelXDp", compactBeforeX) != compactBeforeX }
        assertEquals("FREE", preferences.getString("brushPanelDock", null))
    }

    @Test fun floatingBrushPanelSnapsToLeftAndBottomDockTargets() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Float").performClick()
        repeat(12) {
            composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
                swipe(Offset(width * .8f, center.y), Offset(width * .2f, center.y), durationMillis = 350)
            }
        }
        composeRule.waitUntil(5_000) { preferences.getString("brushPanelDock", null) == "LEFT" }
        composeRule.onNodeWithText("Brush Library").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Float").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(Offset(center.x, height * .35f), Offset(center.x, height * .65f), durationMillis = 500)
        }
        composeRule.waitUntil(5_000) { preferences.getString("brushPanelDock", null) == "BOTTOM" }
        composeRule.onNodeWithContentDescription("Bottom workspace panels").assertIsDisplayed()
    }

    @Test fun dockedBrushPanelCanDetachDirectlyFromEveryDockAndLockPreventsIt() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(center, center + Offset(-180f, 0f), durationMillis = 500)
        }
        composeRule.waitUntil(5_000) { preferences.getString("brushPanelDock", null) == "FREE" }
        composeRule.onNodeWithContentDescription("Resize brushes panel").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Dock left").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(center, center + Offset(180f, 0f), durationMillis = 500)
        }
        composeRule.waitUntil(5_000) { preferences.getString("brushPanelDock", null) == "FREE" }

        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(center, center + Offset(0f, -180f), durationMillis = 500)
        }
        composeRule.waitUntil(5_000) { preferences.getString("brushPanelDock", null) == "FREE" }

        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Dock right").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Lock position and size").performClick()
        composeRule.onNodeWithText("Brush Library").performTouchInput {
            swipe(center, center + Offset(-180f, 0f), durationMillis = 500)
        }
        composeRule.waitForIdle()
        assertEquals("RIGHT", preferences.getString("brushPanelDock", null))
    }

    @Test fun floatingBrushAndLayerPanelsCanGroupMoveResizeSwitchAndSeparate() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Change brushes panel docking").performClick()
        composeRule.onNodeWithText("Float").performClick()
        repeat(2) {
            composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
                swipe(center, center + Offset(-300f, 0f), durationMillis = 400)
            }
        }

        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onNodeWithContentDescription("Change layers panel docking").performClick()
        composeRule.onNodeWithText("Float").performClick()
        composeRule.onNodeWithContentDescription("Drag layers panel").performTouchInput {
            swipe(center, center + Offset(-400f, 0f), durationMillis = 600)
        }
        composeRule.waitUntil(5_000) { preferences.getBoolean("freeDockStacked", false) }

        composeRule.onNodeWithContentDescription("Floating panel tabs").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Brushes floating tab. Drag to reorder.").performClick()
        composeRule.onNodeWithText("Brush Library").assertIsDisplayed()

        val beforeX = preferences.getFloat("brushPanelXDp", 0f)
        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(center, center + Offset(120f, 80f), durationMillis = 500)
        }
        composeRule.waitUntil(5_000) { preferences.getFloat("brushPanelXDp", beforeX) != beforeX }
        assertEquals(preferences.getFloat("brushPanelXDp", 0f), preferences.getFloat("layerPanelXDp", 1f), .01f)
        assertEquals(preferences.getFloat("brushPanelYDp", 0f), preferences.getFloat("layerPanelYDp", 1f), .01f)

        composeRule.onNodeWithContentDescription("Resize brushes panel").performTouchInput {
            swipe(center, center + Offset(80f, -40f), durationMillis = 400)
        }
        composeRule.waitForIdle()
        assertEquals(preferences.getFloat("brushPanelWidthDp", 0f), preferences.getFloat("layerPanelWidthDp", 1f), .01f)
        assertEquals(preferences.getFloat("brushPanelHeightDp", 0f), preferences.getFloat("layerPanelHeightDp", 1f), .01f)

        composeRule.onNodeWithContentDescription("Separate floating panel tabs").performClick()
        composeRule.waitUntil(5_000) { !preferences.getBoolean("freeDockStacked", true) }
        assertTrue(composeRule.onAllNodes(hasContentDescription("Floating panel tabs")).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("Drag brushes panel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Drag layers panel").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
            swipe(center, center + Offset(30f, 20f), durationMillis = 350)
        }
        composeRule.waitUntil(5_000) { preferences.getBoolean("freeDockStacked", false) }
        repeat(8) {
            if (preferences.getString("brushPanelDock", null) != "RIGHT") {
                composeRule.onNodeWithContentDescription("Drag brushes panel").performTouchInput {
                    swipe(center, center + Offset(240f, 0f), durationMillis = 350)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            preferences.getString("brushPanelDock", null) == "RIGHT" && preferences.getString("layerPanelDock", null) == "RIGHT"
        }
        assertTrue(preferences.getBoolean("rightDockStacked", false))
        composeRule.onNodeWithContentDescription("right panel tabs").assertIsDisplayed()
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
