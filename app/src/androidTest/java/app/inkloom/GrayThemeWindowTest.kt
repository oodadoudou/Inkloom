package app.inkloom

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.UiGrayTheme
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class GrayThemeWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").commit()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules = RuleChain.outerRule(clean).around(compose)

    @Test fun grayChoicesApplyImmediatelyRetainDocumentsAndSurviveRecreation() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java]
        val before = requireNotNull(model.currentPage())
        val activity = compose.activity
        assertEquals(UiGrayTheme.MEDIUM, AppSettingsStore(context).load().uiGrayTheme)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").performClick()
        for (gray in UiGrayTheme.entries) {
            compose.onNodeWithContentDescription("Interface gray ${gray.name}").performClick()
            compose.waitUntil(5_000) { AppSettingsStore(context).load().uiGrayTheme == gray }
            compose.runOnIdle {
                assertEquals("Backdrop below transparent system bars for $gray", gray.argb(gray.workspace), (activity.window.decorView.background as android.graphics.drawable.ColorDrawable).color)
            }
            compose.onNodeWithContentDescription("Interface gray ${gray.name}").assertIsSelected()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(gray.argb(gray.surface), app.inkloom.ui.theme.Paper.toArgb())
            }
            assertSame("Theme changes must not recreate the editor", activity, compose.activity)
            assertEquals("Theme must not change document paper, layers or paint", before, model.currentPage())
        }
        compose.onNodeWithText("Done").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(UiGrayTheme.LIGHT, AppSettingsStore(context).load().uiGrayTheme)
        assertEquals(UiGrayTheme.LIGHT.argb(UiGrayTheme.LIGHT.workspace), (compose.activity.window.decorView.background as android.graphics.drawable.ColorDrawable).color)
        assertEquals(before, androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java].currentPage())
    }
}
