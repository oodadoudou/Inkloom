package app.inkloom

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.isPaintSurfaceLayer
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class LayerColorWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun colorReplacementIsASeparateUndoableLayerProperty() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        lateinit var model: InkloomViewModel
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java]
        }
        val layerId = requireNotNull(model.currentPage()).layers.first { it.isPaintSurfaceLayer() }.id
        assertNull(requireNotNull(model.currentPage()).layers.first { it.id == layerId }.colorReplacement)
        compose.onAllNodesWithContentDescription("Layer actions")[0].performClick()
        compose.onNodeWithText("Layer color replacement").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(10_000) { model.currentPage()?.layers?.firstOrNull { it.id == layerId }?.colorReplacement != null }
        assertNotNull(requireNotNull(model.currentPage()).layers.first { it.id == layerId }.colorReplacement)
        compose.onAllNodesWithContentDescription("Undo")[0].performClick()
        compose.waitUntil(10_000) { model.currentPage()?.layers?.firstOrNull { it.id == layerId }?.colorReplacement == null }
    }
}
