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

}
