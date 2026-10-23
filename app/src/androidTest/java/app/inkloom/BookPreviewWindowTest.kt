package app.inkloom

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** The notebook as a 3D book: opens at the current page, turns both ways, closes, jumps and rebinds. */
class BookPreviewWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun bookOpensAtTheCurrentPageAndTurnsClosesJumpsAndRebinds() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        repeat(2) { count ->
            compose.onNodeWithContentDescription("Add page using current template").performClick()
            compose.waitUntil(5_000) { model().currentDocument()?.pages?.size == count + 2 }
        }
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("3D book preview…").performScrollTo().performClick()
        shows("Page 2–3 of 3")

        compose.onNodeWithContentDescription("Turn the left page").performClick(); shows("Page 1 of 3")
        compose.onNodeWithContentDescription("Turn the left page").performClick(); shows("Front cover")
        compose.onNodeWithText("Right binding").performClick()
        // Right-bound books open from the left.
        compose.onNodeWithContentDescription("Turn the left page").performClick(); shows("Page 1 of 3")
        book().performTouchInput { doubleClick(center) }; shows("Front cover")
        book().performTouchInput { doubleClick(center) }; shows("Page 1 of 3")
        compose.onNodeWithContentDescription("Go to page").performTextInput("3"); shows("Page 2–3 of 3")
        compose.onNodeWithText("Cover").performClick(); shows("Page 2–3 of 3")
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("3D book").fetchSemanticsNodes().isEmpty() }
    }

    private fun book() = compose.onNodeWithContentDescription("3D book")

    private fun shows(spread: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithContentDescription("3D book").fetchSemanticsNodes().firstOrNull()
            ?.config?.let { if (SemanticsProperties.StateDescription in it) it[SemanticsProperties.StateDescription] else null } == spread
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]
}
