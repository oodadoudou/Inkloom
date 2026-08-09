package app.inkloom

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import app.inkloom.importer.BrushGroupStore
import app.inkloom.document.PageTemplateStore
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutCategory
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.ui.InkCanvasView
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
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showPageRail", true)
                    .putBoolean("showToolbar", true)
                    .putBoolean("verticalToolbar", true)
                    .putBoolean("rulerEnabled", false)
                    .putBoolean("gridSnapEnabled", false)
                    .putBoolean("alignmentGuidesEnabled", false)
                    .putString("pagePanelDock", "LEFT")
                    .putString("brushPanelDock", "RIGHT")
                    .putString("layerPanelDock", "RIGHT")
                    .putString("toolbarDock", "LEFT")
                    .putFloat("toolbarOffsetXDp", 16f)
                    .putFloat("toolbarOffsetYDp", 16f)
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

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Show ruler").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            AppSettingsStore(composeRule.activity).load().rulerEnabled
        }

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Hide ruler").assertIsDisplayed()

        assertTrue(AppSettingsStore(composeRule.activity).load().rulerEnabled)
    }

    @Test fun snapGridAndAlignmentGuidesAreReachableFromCompactToolbar() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Show snap grid").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().gridSnapEnabled }

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Show alignment guides").performClick()
        composeRule.waitUntil(5_000) { AppSettingsStore(composeRule.activity).load().alignmentGuidesEnabled }

        assertTrue(AppSettingsStore(composeRule.activity).load().gridSnapEnabled)
        assertTrue(AppSettingsStore(composeRule.activity).load().alignmentGuidesEnabled)
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
            assertEquals(false, AppSettingsStore(composeRule.activity).load().showPageRail)

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

    @Test fun pagesAndLayersCanShareTheBottomDockWhileBrushesStayAStablePopover() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Change pages panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onNodeWithContentDescription("Change layers panel docking").performClick()
        composeRule.onNodeWithText("Dock bottom").performClick()

        composeRule.onNodeWithContentDescription("Bottom workspace panels").assertIsDisplayed()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
        val restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(WorkspacePanelDock.BOTTOM, restored.pagePanelDock)
        assertEquals(WorkspacePanelDock.BOTTOM, restored.layerPanelDock)

        composeRule.onNodeWithContentDescription("Stack panels in bottom dock").performClick()
        composeRule.onNodeWithContentDescription("bottom panel tabs").assertIsDisplayed()
        assertTrue(AppSettingsStore(composeRule.activity).load().bottomDockStacked)
        composeRule.onNodeWithContentDescription("Layers tab. Drag to reorder.").performClick()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Separate bottom panel tabs").performClick()
        assertEquals(false, AppSettingsStore(composeRule.activity).load().bottomDockStacked)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithText("Studio Pen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Drag brushes panel").assertIsDisplayed()
        assertEquals(WorkspacePanelDock.FREE, AppSettingsStore(composeRule.activity).load().brushPanelDock)
    }

    @Test fun drawingWorkspaceKeepsNotebookToolsAsPopovers() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Drawing workspace").performClick()
        var restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(false, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithText("Studio Pen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Reading workspace").performClick()
        restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(true, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        assertEquals(false, restored.showToolbar)

        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Minimal workspace").performClick()
        restored = AppSettingsStore(composeRule.activity).load()
        assertEquals(false, restored.showPageRail)
        assertEquals(false, restored.showBrushPanel)
        assertEquals(false, restored.showLayerPanel)
        assertEquals(true, restored.showToolbar)

        composeRule.onNodeWithContentDescription("Actions").performClick()
        assertEquals(0, composeRule.onAllNodesWithText("Save current workspace…").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Manage custom workspaces…").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Import workspace…").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Reset current workspace").fetchSemanticsNodes().size)
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

        composeRule.onNodeWithContentDescription("Layers").performClick()
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
        composeRule.onNodeWithContentDescription("More").performClick()
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

        composeRule.onNodeWithText("Page, handwriting, image, or PDF text").assertIsDisplayed()
        composeRule.onNodeWithText("Recognize ink").assertIsDisplayed()
        composeRule.onNodeWithText("Recognize images").assertIsDisplayed()
        composeRule.onNodeWithText("Index the whole notebook").assertIsDisplayed()
    }

    @Test fun editorCreatesAnInternalNotebookPageLink() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Link").performClick()
        composeRule.onNodeWithText("Notebook page").performClick()

        composeRule.onNodeWithText("Notebook").assertIsDisplayed()
        composeRule.onNodeWithText("Insert").performClick()
        composeRule.onNodeWithText("Insert link").assertDoesNotExist()
    }

    @Test fun pageOutlineExposesEditingActions() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Page outline").performClick()
        composeRule.onNodeWithContentDescription("Edit outline item").performClick()

        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Move down").assertIsDisplayed()
        composeRule.onNodeWithText("Demote level").assertIsDisplayed()
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
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasContentDescription("Open layers for this page")).fetchSemanticsNodes().size == 2
        }
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous page").performClick()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next page").performClick()
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
    }

    @Test fun longPressPagesEntersMultiSelectManagement() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Add page").performClick()
        composeRule.onNodeWithContentDescription("Use Grid template").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasContentDescription("Open layers for this page")).fetchSemanticsNodes().size == 2 }

        composeRule.onNodeWithContentDescription("Page 1. Long press to manage.").performTouchInput {
            longClick()
        }
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Page 1. Selected for management.").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Page 2. Long press to manage.")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Page 2. Selected for management.").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Duplicate selected pages").performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasContentDescription("Open layers for this page")).fetchSemanticsNodes().size == 4 }
    }

    @Test fun longPressDragOnPageThumbnailReordersPages() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Add page").performClick()
        composeRule.onNodeWithContentDescription("Use Grid template").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasContentDescription("Open layers for this page")).fetchSemanticsNodes().size == 2 }
        composeRule.onNodeWithContentDescription("Page 1. Long press to manage.").performClick()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Page 1. Long press to manage.").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 180f))
            advanceEventTime(50)
            up()
        }

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("2/2").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
    }

    @Test fun editorInsertsAValidatedLinkCard() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("More").performClick()
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

    @Test fun procreateSidebarKeepsBrushSizeAndOpacityImmediatelyAvailable() {
        createNotebookAndOpenEditor()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("Brush size").assertIsDisplayed()
                composeRule.onNodeWithContentDescription("Brush opacity").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithContentDescription("Brush size").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Brush opacity").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Drag toolbar").assertIsDisplayed()
    }

    @Test fun brushPopoverNeverChangesTheUsableCanvasInPortrait() {
        createNotebookAndOpenEditor()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()

        val before = composeRule.onNodeWithContentDescription("Ink canvas").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        val after = composeRule.onNodeWithContentDescription("Ink canvas").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("A brush popover must not resize the canvas", before, after)
    }

    @Test fun brushPanelCanFloatMoveResizeAndReopenInPlace() {
        createNotebookAndOpenEditor()
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        val beforeX = preferences.getFloat("brushPanelXDp", 720f)
        val beforeWidth = preferences.getFloat("brushPanelWidthDp", 330f)

        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Drag brushes panel").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(-120f, 90f), durationMillis = 500)
        }
        composeRule.onNodeWithContentDescription("Resize brushes panel · left edge").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(-36f, 0f), durationMillis = 350)
        }
        composeRule.onNodeWithContentDescription("Resize brushes panel · top edge").assertIsDisplayed()
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
        assertEquals("FREE", preferences.getString("brushPanelDock", null))
    }

    @Test fun openingLayersAutomaticallyDismissesBrushes() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Close brushes panel").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Layers").performClick()

        assertEquals(0, composeRule.onAllNodesWithContentDescription("Close brushes panel").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Close layers panel").assertIsDisplayed()
    }

    @Test fun selectionControlsExposeLassoRectangleEllipseAndBrushModes() {
        val preferences = composeRule.activity.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        val original = preferences.getString("selectionShape", "FREEFORM")
        try {
            createNotebookAndOpenEditor()
            composeRule.onNodeWithContentDescription("Selection").performClick()
            composeRule.onNodeWithContentDescription("More").performClick()

            composeRule.onNodeWithText("Freeform").assertIsDisplayed()
            composeRule.onNodeWithText("Rectangle").assertIsDisplayed()
            composeRule.onNodeWithText("Ellipse").assertIsDisplayed()
            composeRule.onNodeWithText("Selection brush").performClick()
            composeRule.waitForIdle()

            assertTrue(preferences.getString("selectionShape", null) == "BRUSH")
            composeRule.onNodeWithContentDescription("More").performClick()
            assertTrue(composeRule.onAllNodesWithText("Selection brush size · 42").fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeRule.onAllNodesWithText("Magic wand").fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeRule.onAllNodesWithText("Smart border").fetchSemanticsNodes().isNotEmpty())
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

    @Test fun procreateTopBarOpensColorsAndThePageLayerStack() {
        createNotebookAndOpenEditor()

        composeRule.onNodeWithContentDescription("Colors").performClick()
        composeRule.onNodeWithText("Color wheel").assertIsDisplayed()
        composeRule.onNodeWithText("HLS").assertIsDisplayed()
        composeRule.onNodeWithText("RGB").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("HLS").performClick()
        composeRule.onNodeWithContentDescription("Colors").performClick()
        composeRule.onNodeWithContentDescription("Open layers for this page").performClick()
        composeRule.onAllNodesWithText("Layers").onLast().assertIsDisplayed()
    }

    @Test fun dockedBrushSelectionKeepsColorAndRapidPenContactsWritable() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Colors").performClick()
        composeRule.onNodeWithContentDescription("Saturation and brightness field").performTouchInput {
            swipe(Offset(width * .2f, height * .2f), Offset(width * .78f, height * .22f), durationMillis = 250)
        }
        composeRule.waitForIdle()
        val view = findInkCanvasView()!!
        val selectedColor = view.inkColor
        assertTrue("The test must select a non-default color", selectedColor != AndroidColor.rgb(17, 17, 17))
        val selectedHsv = FloatArray(3).also { AndroidColor.colorToHSV(selectedColor, it) }
        assertEquals("SV cursor X must match actual saturation", .78f, selectedHsv[1], .06f)
        assertEquals("SV cursor Y must match actual brightness", .78f, selectedHsv[2], .06f)

        composeRule.onNodeWithContentDescription("Colors").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithText("Pencil").performClick()
        composeRule.onNodeWithText("Technical Pencil").performClick()
        composeRule.waitUntil(5_000) { view.brushId == "system-technical-pencil" }
        assertEquals("Changing a docked brush must retain the shared foreground color", selectedColor, view.inkColor)

        var elapsedMs = Long.MAX_VALUE
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val started = SystemClock.elapsedRealtime()
            repeat(6) { index -> dispatchStylusStroke(view, index) }
            elapsedMs = SystemClock.elapsedRealtime() - started
        }
        val strokes = reflectedField(view, "strokes") as? List<*>
        assertEquals("Every rapid contact must commit immediately", 6, strokes?.size)
        assertTrue("Six short strokes must not block the input thread ($elapsedMs ms)", elapsedMs < 1_500L)
    }

    @Test fun layerPanelCreatesFoldersMovesLayersAndCollapsesChildren() {
        createNotebookAndOpenEditor()
        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.onNodeWithContentDescription("Add layer folder").performClick()
        composeRule.onNodeWithContentDescription("Add layer").performClick()

        composeRule.onNodeWithText("Folder 1").assertIsDisplayed()
        composeRule.onNodeWithText("Layer 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Move layer to folder Layer 1").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Collapse folder").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodes(hasText("Layer 1")).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Folder 1").assertIsDisplayed()
    }

    private fun findInkCanvasView(): InkCanvasView? {
        var found: InkCanvasView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun walk(view: android.view.View) {
                if (view is InkCanvasView) found = view
                else if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
            walk(composeRule.activity.window.decorView)
        }
        return found
    }

    private fun dispatchStylusStroke(view: InkCanvasView, index: Int) {
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, elapsed: Long): MotionEvent = MotionEvent.obtain(
            downTime,
            downTime + elapsed,
            action,
            1,
            properties,
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .7f; size = .05f }),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        val y = view.height * (.18f + index * .1f)
        listOf(
            event(MotionEvent.ACTION_DOWN, view.width * .25f, y, 0),
            event(MotionEvent.ACTION_MOVE, view.width * .55f, y + 8f, 4),
            event(MotionEvent.ACTION_UP, view.width * .7f, y + 12f, 8),
        ).forEach { motion -> try { view.dispatchTouchEvent(motion) } finally { motion.recycle() } }
    }

    private fun reflectedField(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { type.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field.get(target)
            }
            type = type.superclass
        }
        return null
    }
}
