package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** The continuous page view scrolls between pages, and only a deliberate contact changes the page drawn on. */
class ContinuousPagesWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false)
                .putBoolean("continuousPages", true).commit()
            resetNotebookTestLibrary(context)
            // Its own notebook ID per test: the app reopens a known notebook at its last page.
            FileDocumentStore(context.filesDir).save(InkDocument(id = "strip-${description.methodName}", title = "Strip fixture", updatedAt = 9_000,
                pages = (1..3).map { InkPage(id = "strip-page-$it", title = "Strip page $it") }))
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun open() {
        compose.onNodeWithContentDescription("Notebook Strip fixture. Long press to move.").performClick()
        compose.onNodeWithText("1/3").assertExists()
    }

    @Test fun scrollingHandsTheCanvasToWhicheverPageIsNearestTheCentre() {
        open()
        val view = canvas()
        drag(fractionOfHeight = -.75f)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { model().currentPage()?.id == "strip-page-2" }
        drag(fractionOfHeight = .75f)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1/3").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Scrolling never paints", empty(view))
    }

    @Test fun aPenPressOnANeighbourMakesItActiveWithoutInkThenItDraws() {
        open()
        // Less than half way to page 2: page 1 stays active and page 2 shows below it.
        drag(fractionOfHeight = -.3f)
        compose.onNodeWithText("1/3").assertExists()
        val neighbour = visibleCenter("Page 2")
        stylusStroke(neighbour, neighbour)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { model().currentPage()?.id == "strip-page-2" }
        SystemClock.sleep(300); compose.waitForIdle()
        assertTrue("The activating press leaves page 2 blank", empty(canvas()))
        stylusStroke(neighbour, neighbour + Offset(220f, 0f))
        compose.waitUntil(10_000) { !empty(canvas()) }
        compose.onNodeWithText("2/3").assertExists()
    }

    @Test fun horizontalFlicksDoNotTurnPagesInTheStrip() {
        open()
        val root = compose.onRoot().fetchSemanticsNode()
        val y = root.positionOnScreen.y + root.size.height * .55f
        val start = Offset(root.positionOnScreen.x + root.size.width * .7f, y)
        gesture(start, start + Offset(-root.size.width * .25f, 0f), durationMillis = 160, stylus = false)
        compose.onNodeWithText("1/3").assertExists()
    }

    private fun drag(fractionOfHeight: Float) {
        val root = compose.onRoot().fetchSemanticsNode()
        val start = Offset(root.positionOnScreen.x + root.size.width * .5f, root.positionOnScreen.y + root.size.height * (if (fractionOfHeight < 0) .85f else .15f))
        gesture(start, start + Offset(0f, root.size.height * fractionOfHeight), durationMillis = 700, stylus = false)
    }

    /** Centre of the part of a neighbour page that is inside the window. */
    private fun visibleCenter(label: String): Offset {
        val node = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
        val root = compose.onRoot().fetchSemanticsNode()
        val bounds = node.boundsInWindow
        val top = maxOf(bounds.top, root.boundsInWindow.top + 160f)
        val bottom = minOf(bounds.bottom, root.boundsInWindow.bottom - 160f)
        assertTrue("$label is visible", bottom > top)
        return Offset(bounds.center.x, (top + bottom) / 2f) + node.positionOnScreen - node.positionInWindow
    }

    private fun stylusStroke(start: Offset, end: Offset) = gesture(start, end, durationMillis = 240, stylus = true)

    private fun gesture(start: Offset, end: Offset, durationMillis: Long, stylus: Boolean) {
        val steps = 16
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, start, down, down, stylus)
        for (i in 1..steps) {
            SystemClock.sleep(durationMillis / steps)
            motion(MotionEvent.ACTION_MOVE, start + (end - start) * (i / steps.toFloat()), down, SystemClock.uptimeMillis(), stylus)
        }
        motion(MotionEvent.ACTION_UP, end, down, SystemClock.uptimeMillis(), stylus)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long, time: Long, stylus: Boolean) {
        val event = MotionEvent.obtain(down, time, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .6f; size = .01f; touchMajor = 8f }),
            0, 0, 1f, 1f, 0, 0, if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

    private fun empty(view: InkCanvasView): Boolean {
        val snapshot = view.activeRasterSnapshotForTest() ?: return true
        return snapshot.tileInfo.none { it.nonTransparentPixelCount > 0 }
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
