package app.inkloom

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** T14: the Navigator view zooms, moves the window to a tapped spot, flips the view and fits the page. */
class NavigatorWindowTest {
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

    @Test fun navigatorZoomsMovesFlipsAndFits() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Navigator view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Navigator preview").assertIsDisplayed()
        repeat(3) { compose.onNodeWithContentDescription("Zoom in").performClick() }
        compose.waitUntil(5_000) { scale(view) > 1.9f }
        assertEquals(0f, view.viewportState().panY, .5f)
        // A tap near the bottom of the page brings that part to the middle of the window.
        compose.onNodeWithContentDescription("Navigator preview").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, height * .9f)) }
        compose.waitUntil(5_000) { view.viewportState().panY < -10f }
        compose.onNodeWithContentDescription("Flip view horizontally").performClick()
        compose.waitUntil(5_000) { view.viewportState().mirrored }
        compose.onNodeWithText("Fit").performClick()
        compose.waitUntil(5_000) { scale(view) == 1f && view.viewportState().panY == 0f }
        assertTrue("the flip is the view's own", view.viewportState().mirrored)
    }

    private fun scale(view: InkCanvasView): Float { var value = 0f; instrumentation.runOnMainSync { value = view.viewportState().scale }; return value }

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
