package app.inkloom

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkLayer
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** ED-22: an animation folder's timeline, then the folder registered as a material and placed again. */
class AnimationWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
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

    @Test fun timelineEditsCelsAndTheFolderBecomesAMaterial() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("Animation folder").performClick()
        compose.waitUntil(10_000) { folders().size == 1 }
        val folder = folders().single()
        val first = folder.animation!!.cels.single().layerId
        compose.waitUntil(5_000) { view.activeLayerId == first }
        compose.onNodeWithText("Animation timeline — ${folder.name}").assertIsDisplayed()

        inDialog("New cel").performClick()
        compose.waitUntil(5_000) { folders().single().animation!!.cels.size == 2 }
        val second = folders().single().animation!!.cels[1].layerId
        assertEquals(second, folders().single().animation!!.current)
        assertFalse("the other cel is hidden", layer(first).visible)
        assertTrue(layer(second).visible)

        compose.onAllNodes(hasText("+") and hasAnyAncestor(isDialog()))[1].performClick()
        compose.waitUntil(5_000) { folders().single().animation!!.cels[1].frames == 2 }
        inDialog(layer(first).name).performTouchInput { click(centerLeft + androidx.compose.ui.geometry.Offset(24f, 0f)) }
        compose.waitUntil(5_000) { folders().single().animation!!.current == first }
        assertTrue(layer(first).visible); assertFalse(layer(second).visible)
        compose.waitUntil(5_000) { view.activeLayerId == first }

        inDialog("Play").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Animation preview").fetchSemanticsNodes().isNotEmpty() }
        inDialog("Stop").performClick()
        inDialog("Done").performClick()

        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(5_000) { folders().single().animation!!.current == second }

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("Materials…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Material name").performTextReplacement("Walk cycle")
        inDialog("Animation").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Animation · 2 cels").fetchSemanticsNodes().isNotEmpty() }
        inDialog("Place").performClick()
        compose.waitUntil(10_000) { folders().size == 2 }
        val placed = folders().first { it.id != folder.id }
        assertEquals(listOf(1, 2), placed.animation!!.cels.map { it.frames })
        val placedCels = model().currentPage()!!.layers.filter { it.parentId == placed.id }.map { it.id }
        assertEquals(placedCels.toSet(), placed.animation!!.cels.map { it.layerId }.toSet())
    }

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))
    private fun folders(): List<InkLayer> = model().currentPage()?.layers?.filter { it.animation != null }.orEmpty()
    private fun layer(id: String) = model().currentPage()!!.layers.first { it.id == id }
    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                fun visit(view: View) {
                    if (view is InkCanvasView && view.isShown) result = view
                    else if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(compose.activity.window.decorView)
            }
            result != null
        }
        return requireNotNull(result)
    }
}
