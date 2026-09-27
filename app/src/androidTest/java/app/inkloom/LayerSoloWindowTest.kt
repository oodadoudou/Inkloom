package app.inkloom

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class LayerSoloWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun longPressEyeSolosRestoresAndUndoesThroughTheWindow() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        lateinit var model: InkloomViewModel
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java]
        }
        val before = requireNotNull(model.currentPage()).layers.associate { it.id to it.visible }
        val eye = { compose.onAllNodesWithContentDescription("Toggle visibility; long press to solo")[0] }
        eye().performTouchInput { longClick() }
        compose.waitUntil(10_000) { model.currentPage()?.soloLayerId != null }
        val solo = requireNotNull(model.currentPage())
        assertNotNull(solo.soloLayerId)
        assertEquals(before, solo.soloVisibilityBefore)
        compose.onAllNodesWithContentDescription("Undo")[0].performClick()
        compose.waitUntil(10_000) { model.currentPage()?.soloLayerId == null }
        assertEquals(before, requireNotNull(model.currentPage()).layers.associate { it.id to it.visible })
        eye().performTouchInput { longClick() }
        compose.waitUntil(10_000) { model.currentPage()?.soloLayerId != null }
        eye().performTouchInput { longClick() }
        compose.waitUntil(10_000) { model.currentPage()?.soloLayerId == null }
        assertEquals(before, requireNotNull(model.currentPage()).layers.associate { it.id to it.visible })
    }
}
