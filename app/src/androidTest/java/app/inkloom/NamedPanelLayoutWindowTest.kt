package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EditorPanel
import app.inkloom.ui.EditorPanelState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class NamedPanelLayoutWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun saveCloseAndRestoreNamedLayoutThroughActionsMenu() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertExists()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Save panel layout…").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Drawing layers")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Close layers panel").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Drawing layers").performClick()
        compose.onNodeWithContentDescription("Close layers panel").assertExists()
        val restored = EditorPanelState(compose.activity, AppSettings())
        assertTrue(EditorPanel.LAYERS in restored.layout.open)
        assertTrue(restored.namedLayouts.entries.any { it.name == "Drawing layers" })
    }
}
