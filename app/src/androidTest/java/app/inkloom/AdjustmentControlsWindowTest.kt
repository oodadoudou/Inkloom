package app.inkloom

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.*
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class AdjustmentControlsWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = TestRule { base, _ -> object : Statement() {
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
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private lateinit var model: InkloomViewModel
    private fun createNote() {
        compose.onNodeWithText("New Note").performClick(); compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { model = androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java] }
        compose.onNodeWithContentDescription("Layers").performClick()
    }
    private fun add(label: String) {
        compose.onNodeWithContentDescription("Add adjustment layer").performClick()
        compose.onNodeWithText(label).performClick()
    }
    private fun adjustment(kind: LayerAdjustmentKind): InkLayer = requireNotNull(model.currentPage()).layers.last { it.adjustment?.kind == kind }
    private fun progress(label: String, value: Float) = compose.onNodeWithContentDescription("$label adjustment")
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

    @Test fun correctionMenuCreatesRealKindsAndPersistsParameters() {
        createNote()
        add("Invert"); compose.onNodeWithText("Apply").performClick()
        assertNotNull(adjustment(LayerAdjustmentKind.INVERT))
        add("Posterize"); progress("Levels per channel", 5.6f); compose.onNodeWithText("Apply").performClick()
        assertEquals(6, adjustment(LayerAdjustmentKind.POSTERIZE).adjustment!!.posterizeLevels)
        add("Threshold"); progress("Threshold", .7f); compose.onNodeWithText("Apply").performClick()
        assertEquals(.7f, adjustment(LayerAdjustmentKind.THRESHOLD).adjustment!!.threshold, .001f)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        assertEquals(6, adjustment(LayerAdjustmentKind.POSTERIZE).adjustment!!.posterizeLevels)
        assertEquals(.7f, adjustment(LayerAdjustmentKind.THRESHOLD).adjustment!!.threshold, .001f)
        assertNotNull(adjustment(LayerAdjustmentKind.INVERT))
    }

    @Test fun luminosityPreviewsCanCancelAndCommitAsOneUndoThenReopen() {
        createNote(); add("Hue / Saturation / Luminosity")
        progress("Luminosity", .6f)
        assertEquals("Preview must leave saved metadata untouched", 0f,
            adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, 0f)
        compose.onNodeWithText("Apply").performClick()
        val id = adjustment(LayerAdjustmentKind.HUE_SATURATION).id
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        assertEquals(0f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, 0f)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        val name = adjustment(LayerAdjustmentKind.HUE_SATURATION).name
        compose.onNodeWithContentDescription("Edit adjustment $name").performScrollTo().performClick()
        progress("Luminosity", -.4f); compose.onNodeWithText("Cancel").performClick()
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(id, adjustment(LayerAdjustmentKind.HUE_SATURATION).id)
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
    }
}
