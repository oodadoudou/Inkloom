package app.inkloom

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Obtain screen color picks exactly what is shown under the tap, inside the app window. */
class ScreenColorPickerWindowTest {
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

    @Test fun pickingThePageAndThenTheWorkspaceSetsTheirColours() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val page = Offset(location[0] + view.width * .5f, location[1] + view.height * .5f)
        pick(page)
        compose.waitUntil(5_000) { view.inkColor == 0xFFFFFFFF.toInt() }
        // Just left of the page is the grey workspace.
        pick(Offset(location[0] - 40f, location[1] + view.height * .5f))
        compose.waitUntil(5_000) { view.inkColor != 0xFFFFFFFF.toInt() }
        val workspace = view.inkColor
        assertNotEquals(0xFFFFFFFF.toInt(), workspace)
        assertEquals("the picked colour is opaque", 0xFF, workspace ushr 24)
    }

    private fun pick(onScreen: Offset) {
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Obtain screen color").performScrollTo().performClick()
        val overlay = compose.onNodeWithContentDescription("Screen color picker")
        val origin = overlay.fetchSemanticsNode().positionOnScreen
        overlay.performTouchInput { click(onScreen - origin) }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Screen color picker").fetchSemanticsNodes().isEmpty() }
    }

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
