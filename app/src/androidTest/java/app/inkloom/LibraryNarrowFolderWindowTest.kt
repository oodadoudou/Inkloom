package app.inkloom

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkFolder
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

class LibraryNarrowFolderWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private val narrow = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().putString("language", "ENGLISH").commit()
            shell("wm size 720x1280")
            try { base.evaluate() } finally { shell("wm size reset") }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(narrow).around(compose)

    @Test fun folderManagementRemainsReachableOnNarrowScreens() {
        compose.onNodeWithContentDescription("Manage folders").performClick()
        compose.onNodeWithContentDescription("New folder").performClick()
        val name = "Narrow folder ${android.os.SystemClock.uptimeMillis()}"
        val inputs = compose.onAllNodes(hasSetTextAction())
        inputs[inputs.fetchSemanticsNodes().lastIndex].performTextReplacement(name)
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) { folders().any { it.name == name } }
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithContentDescription("Manage folders").assertExists()
        compose.onNodeWithContentDescription("New Note").assertExists()
        compose.onNodeWithContentDescription("Manage folders").performClick()
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("Done").performClick()
    }
    private fun folders(): List<InkFolder> = Json.decodeFromString(File(context.filesDir, "library.json").readText())
}
