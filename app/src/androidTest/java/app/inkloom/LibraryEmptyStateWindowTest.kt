package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.FileDocumentStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** A new Library is really empty: no sample notebooks, and every empty section explains itself. */
class LibraryEmptyStateWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val emptyLibrary = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            check(context.filesDir.resolve("documents").deleteRecursively())
            context.filesDir.resolve("journal.jsonl").let { check(!it.exists() || it.delete()) }
            context.filesDir.resolve("library.json").let { check(!it.exists() || it.delete()) }
            try { base.evaluate() } finally { resetNotebookTestLibrary(context) }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(emptyLibrary).around(compose)

    @Test fun emptyLibraryOffersAFirstNotebookWithoutAddingSamples() {
        val store = FileDocumentStore(compose.activity.filesDir)
        compose.onNodeWithContentDescription("No notebooks yet").assertIsDisplayed()
        assertEquals("Opening an empty Library must not add sample notebooks", 0, store.list().size)
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Gallery").performClick()
        compose.onNodeWithContentDescription("No notebooks yet").assertDoesNotExist()
        assertEquals(1, store.list().size)
    }

    @Test fun searchAndTrashExplainWhyTheyAreEmpty() {
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        // The on-screen keyboard may cover the results area while typing; the message replaces it.
        compose.onNodeWithContentDescription("No notebooks match “zzz”").assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Trash").performClick()
        compose.onNodeWithContentDescription("Trash is empty").assertExists()
    }
}
