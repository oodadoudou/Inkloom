package app.inkloom

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.InkFolder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

/** Exercises the actual tablet folder selection and destination picker, then reads durable state. */
class LibraryFolderBatchWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                .putString("language", "ENGLISH").commit()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun moveThenDeleteSelectedFolderThroughTheLibraryRail() {
        val suffix = SystemClock.uptimeMillis().toString()
        val sourceName = "Move source $suffix"
        val destinationName = "Move target $suffix"
        createFolder(sourceName)
        createFolder(destinationName)
        val before = folders()
        val source = before.single { it.name == sourceName }
        val destination = before.single { it.name == destinationName }

        compose.onNodeWithContentDescription("Select folders").performClick()
        compose.onNodeWithContentDescription("Select folder $sourceName").performClick()
        compose.onNodeWithText("Move", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Move folders to $destinationName").performClick()
        compose.waitUntil(5_000) { folders().singleOrNull { it.id == source.id }?.parentId == destination.id }
        assertEquals(destination.id, folders().single { it.id == source.id }.parentId)

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { folders().singleOrNull { it.id == source.id }?.parentId == destination.id }
        compose.onNodeWithContentDescription("Select folders").performClick()
        compose.onNodeWithContentDescription("Select folder $destinationName").performClick()
        compose.onNodeWithContentDescription("Delete selected folders").performClick()
        compose.onNodeWithText("Delete selected folders?").assertExists()
        compose.onNodeWithContentDescription("Confirm delete selected folders").performClick()
        compose.waitUntil(5_000) { folders().none { it.id == source.id || it.id == destination.id } }
        assertFalse(folders().any { it.id == source.id || it.id == destination.id })
        assertTrue(folders().none { it.name == sourceName || it.name == destinationName })
    }

    private fun createFolder(name: String) {
        compose.onNodeWithContentDescription("New folder").performClick()
        val inputs = compose.onAllNodes(hasSetTextAction())
        inputs[inputs.fetchSemanticsNodes().lastIndex].performTextReplacement(name)
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) { folders().any { it.name == name } }
    }

    private fun folders(): List<InkFolder> = Json.decodeFromString(File(context.filesDir, "library.json").readText())
}
