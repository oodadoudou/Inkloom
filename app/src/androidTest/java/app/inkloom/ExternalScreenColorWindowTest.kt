package app.inkloom

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.capture.ScreenColorCapture
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** ED-34: a captured screen frame, now or after hiding Inkloom, gives the drawing colour. */
class ExternalScreenColorWindowTest {
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
            // The emulator test grants screen capture up front instead of tapping the system dialog.
            shell("appops set ${context.packageName} PROJECT_MEDIA allow")
            shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
        SystemClock.sleep(300)
    }

    @Test fun pickingFromTheScreenNowUsesTheCapturedFrame() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        open("Pick from the screen now")
        // The system consent screen pauses Inkloom; wait for the frame before looking at the UI.
        compose.waitUntil(15_000) { ScreenColorCapture.frame.value != null }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Captured screen").fetchSemanticsNodes().isNotEmpty() }
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val target = Offset(location[0] + view.width * .5f, location[1] + view.height * .5f)
        val frame = requireNotNull(ScreenColorCapture.frame.value)
        val expected = frame.getPixel(target.x.toInt(), target.y.toInt()) or 0xFF000000.toInt()
        tapCaptured(target)
        try { compose.waitUntil(5_000) { view.inkColor == 0xFFFFFFFF.toInt() } }
        catch (e: Throwable) { throw AssertionError("captured ${Integer.toHexString(expected)}, picked ${Integer.toHexString(view.inkColor)}", e) }
        assertTrue("the frame is released after picking", ScreenColorCapture.frame.value == null)
    }

    @Test fun hidingInkloomWaitsForTheNotificationThenPicks() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        open("Hide Inkloom first")
        compose.waitUntil(10_000) { ScreenColorCapture.waiting.value }
        SystemClock.sleep(800)
        // Stands in for tapping the notification.
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, app.inkloom.capture.ScreenColorCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (ScreenColorCapture.frame.value == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Captured screen").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(!ScreenColorCapture.waiting.value)
        val frame = requireNotNull(ScreenColorCapture.frame.value)
        assertTrue("a whole display frame", frame.width > 1000 && frame.height > 1000)
        tapCaptured(Offset(40f, 600f))
        compose.waitUntil(5_000) { ScreenColorCapture.frame.value == null }
        assertEquals("the picked colour is opaque", 0xFF, view.inkColor ushr 24)
    }

    private fun open(mode: String) {
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Obtain screen color from other apps…").performScrollTo().performClick()
        compose.onNodeWithText(mode).performClick()
        compose.onNodeWithText("Continue").performClick()
    }

    private fun tapCaptured(onScreen: Offset) {
        val overlay = compose.onNodeWithContentDescription("Captured screen")
        val origin = overlay.fetchSemanticsNode().positionOnScreen
        overlay.performTouchInput { click(onScreen - origin) }
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
