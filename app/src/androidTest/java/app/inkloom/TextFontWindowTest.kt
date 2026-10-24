package app.inkloom

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.PageElement
import app.inkloom.document.TextFonts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** T16: a text object's properties choose its font and letter spacing. */
class TextFontWindowTest {
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

    @Test fun textPropertiesChooseTheFont() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Tools").performClick()
        compose.onNodeWithText("Text").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Inkloom fonts")
        compose.onNodeWithText("Insert").performClick()
        compose.onNodeWithText("Inkloom fonts").performTouchInput { doubleClick() }
        compose.onNodeWithText("Object properties").assertIsDisplayed()
        compose.onNodeWithText("Default (sans-serif)").performScrollTo().performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { shot ->
            java.io.File(compose.activity.getExternalFilesDir(null), "font-menu.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Serif").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { text()?.fontFamily == TextFonts.SYSTEM + "serif" }
        assertEquals(0f, text()!!.letterSpacing, 0f)
    }

    private fun text() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]
        .currentPage()?.elements?.filterIsInstance<PageElement.Text>()?.firstOrNull()
}
