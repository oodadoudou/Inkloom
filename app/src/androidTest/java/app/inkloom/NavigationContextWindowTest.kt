package app.inkloom

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Large synthetic library and hierarchy fixtures exercise actual window disposal and reopening. */
class NavigationContextWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", true).commit()
            context.filesDir.resolve("library.json").delete()
            val store = FileDocumentStore(context.filesDir)
            val layers = listOf(defaultInkLayers().first(), InkLayer(id = "nav-folder", name = "Nested folder", role = LayerRole.FOLDER, collapsed = true),
                defaultInkLayers().last().copy(parentId = "nav-folder")) + (0..29).map { InkLayer(id = "nav-layer-$it", name = "Extra layer $it") }
            val pages = (1..32).map { n -> InkPage(id = "nav-page-$n", title = "Review page $n", layers = layers,
                pdfTextBlocks = listOf(PdfTextBlock("needle needle", listOf(PdfTextBounds(.1f,.2f,.7f,.3f)))),
                bookmarked = n == 30, tags = if (n in listOf(1, 30)) listOf("Study") else if (n == 28) listOf("Work") else emptyList(),
                outlineLevel = when (n) { 29 -> 1; 30 -> 2; else -> 0 }, outlineCollapsed = n in 28..29) }
            store.save(InkDocument(id = "nav-main", title = "Navigation fixture", pages = pages, updatedAt = 1000))
            (0..23).forEach { n -> store.save(InkDocument(id = "nav-library-$n", title = "Context note $n", favorite = true, updatedAt = 2000L + n)) }
            store.save(InkDocument(id = "nav-distractor", title = "Context distractor", updatedAt = 4000))
            try { base.evaluate() } finally { store.list().filter { it.id.startsWith("nav-") }.forEach { store.deletePermanently(it.id) } }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun libraryReturnPreservesSearchFilterAndScrollInBothLayouts() {
        compose.onAllNodesWithText("Favorites").onLast().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Context")
        fun roundTrip(list: String) {
            compose.onNodeWithContentDescription(list).performScrollToIndex(15)
            val card = compose.onAllNodes(hasContentDescription("Notebook Context note", substring = true) and hasClickAction()).onFirst()
            card.performClick()
            compose.onNodeWithText("Gallery").performClick()
            compose.onNode(hasSetTextAction() and hasText("Context")).assertExists()
            val range = compose.onNodeWithContentDescription(list).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("Library must keep the user's browsing position", range.value() > 0f)
            compose.onNodeWithContentDescription(list).performScrollToIndex(0)
            compose.onNodeWithText("Context distractor").assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("List layout").performClick()
        roundTrip("Notebook list")
        compose.onNodeWithContentDescription("Grid layout").performClick()
        roundTrip("Notebook grid")
    }

    private fun openFixtureAtPage30() {
        compose.onNode(hasSetTextAction()).performTextInput("Navigation fixture")
        compose.onNodeWithContentDescription("Notebook Navigation fixture. Long press to move.").performClick()
        compose.onNodeWithContentDescription("Page list").performScrollToIndex(29)
        compose.onNodeWithContentDescription("Page 30. Long press to manage.").performClick()
    }

    @Test fun pageTabsFilterSavedPagesAndTagGroupsAndKeepCurrentPageInView() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Show bookmarked pages").performClick()
        compose.onNode(hasContentDescription("Page 30. Long press to manage.") and isSelected()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1. Long press to manage.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show page tag categories").performClick()
        compose.onNodeWithContentDescription("Page tag Study").performClick()
        compose.onNode(hasContentDescription("Page 30. Long press to manage.") and isSelected()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1. Long press to manage.").performClick()
        compose.onNodeWithText("1/32").assertExists()
        compose.onNodeWithContentDescription("Back to tag categories").performClick()
        compose.onNodeWithContentDescription("Page tag Work").performClick()
        compose.onNodeWithContentDescription("Page 28. Long press to manage.").performClick()
        compose.onNodeWithText("28/32").assertExists()
        compose.onNodeWithContentDescription("Show all pages").performClick()
        compose.onNode(hasContentDescription("Page 28. Long press to manage.") and isSelected()).assertIsDisplayed()
    }

    @Test fun jumpPopupIsAboveNavigatorAndInitiallyHighlightsPage30() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Jump to page").performClick()
        compose.onNodeWithContentDescription("Go to page 30").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Go to page 1").assertDoesNotExist()
        val popup = compose.onNodeWithContentDescription("Page jump panel").fetchSemanticsNode()
        val navigator = compose.onNodeWithContentDescription("Drag page navigator").fetchSemanticsNode()
        assertTrue("The popup stays just above the bottom navigator", popup.positionOnScreen.y + popup.size.height <= navigator.positionOnScreen.y)
        compose.onNodeWithContentDescription("Go to page 31").performClick()
        compose.onNodeWithText("31/32").assertExists()
        compose.onNodeWithContentDescription("Jump to page").performClick()
        compose.onNodeWithContentDescription("Go to page 31").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close page jump").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithContentDescription("Go to page 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actions Share").assertIsSelected()
    }

    @Test fun categorizedActionsKeepPreferencesAndSelectionConfigurationOutOfOtherMenus() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("Search this notebook").assertIsDisplayed()
        compose.onNodeWithText("Notebook settings").assertDoesNotExist()
        compose.onNodeWithText("Export shortcuts…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Canvas & resolution").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search this notebook").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").assertIsDisplayed()
        compose.onNodeWithText("Export shortcuts…").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Interface gray MEDIUM").assertIsDisplayed()
    }

    @Test fun outlineOpensAtCurrentPageAndExpandsItsCollapsedParents() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("Page outline").performScrollTo().performClick()
        compose.onNode(hasText("Review page 30") and isSelected()).assertIsDisplayed()
        val document = FileDocumentStore(compose.activity.filesDir).get("nav-main")!!
        assertFalse(document.pages[27].outlineCollapsed)
        assertFalse(document.pages[28].outlineCollapsed)
        compose.onNodeWithContentDescription("Page outline list").performScrollToIndex(0)
        compose.onNodeWithText("Review page 1").assertIsDisplayed()
    }

    @Test fun searchNavigationVisitsEveryOccurrenceAndWrapsAcrossPages() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("Search this notebook").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("needle")
        compose.onAllNodesWithText("Review page 1").onFirst().performClick()
        compose.onNodeWithText("1 / 64").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next search match").performClick()
        compose.onNodeWithText("2 / 64").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next search match").performClick()
        compose.onNodeWithText("3 / 64").assertIsDisplayed()
        repeat(3) { compose.onNodeWithContentDescription("Previous search match").performClick() }
        compose.onNodeWithText("64 / 64").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search navigation").performClick()
        compose.onNodeWithContentDescription("Next search match").assertDoesNotExist()
    }

    @Test fun layerPanelRevealsActiveLayerInCollapsedFolderOnReopen() {
        openFixtureAtPage30()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Select layer Notes").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Layer list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Select layer Extra layer 29").assertIsDisplayed()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Select layer Notes").assertIsSelected().assertIsDisplayed()
    }

    @Test fun linkTargetPickerReopensAtLastChosenPage() {
        openFixtureAtPage30()
        fun openLink() {
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithContentDescription("Actions Tools").performClick()
            compose.onNodeWithText("Link").performClick()
            compose.onNodeWithText("Notebook page").performClick()
        }
        openLink()
        compose.onNodeWithContentDescription("Link target pages").performScrollToIndex(29)
        compose.onNode(hasText("Review page 30") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Cancel").performClick()
        openLink()
        compose.onNode(hasText("Review page 30") and isSelected() and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }
}
