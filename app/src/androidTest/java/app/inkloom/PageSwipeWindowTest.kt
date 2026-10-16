package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** One-finger flicks turn pages at fit zoom; pens, slow drags, zoomed views and the off switch never do. */
class PageSwipeWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var swipeEnabled = true
    private val fixture = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            swipeEnabled = !description.methodName.contains("Off")
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false)
                .putBoolean("swipeToTurnPages", swipeEnabled).commit()
            resetNotebookTestLibrary(context)
            // Its own notebook ID per test: the app reopens a known notebook at its last page.
            FileDocumentStore(context.filesDir).save(InkDocument(id = "swipe-doc-${description.methodName}", title = "Swipe fixture", updatedAt = 9_000,
                pages = (1..3).map { InkPage(id = "swipe-page-$it", title = "Swipe page $it") }))
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun open() {
        compose.onNodeWithContentDescription("Notebook Swipe fixture. Long press to move.").performClick()
        compose.onNodeWithText("1/3").assertExists()
    }

    @Test fun quickFingerFlicksTurnPagesBothWaysAndStopAtTheEnds() {
        open()
        flick(-1); compose.onNodeWithText("2/3").assertExists()
        flick(-1); compose.onNodeWithText("3/3").assertExists()
        flick(-1); compose.onNodeWithText("3/3").assertExists()
        flick(+1); compose.onNodeWithText("2/3").assertExists()
        flick(+1); flick(+1); compose.onNodeWithText("1/3").assertExists()
    }

    @Test fun slowDragsPensRecentPenContactAndZoomedViewsDoNotTurnPages() {
        open()
        flick(-1, durationMillis = 1_400); compose.onNodeWithText("1/3").assertExists()
        flick(-1, stylus = true); compose.onNodeWithText("1/3").assertExists()
        // A hand touching the screen right after the pen is treated like a resting palm.
        flick(-1); compose.onNodeWithText("1/3").assertExists()
        SystemClock.sleep(900)
        flick(-1); compose.onNodeWithText("2/3").assertExists()
        compose.onNodeWithContentDescription("Quick zoom").performClick()
        compose.onNodeWithText("125%").performClick()
        compose.waitForIdle()
        flick(-1); compose.onNodeWithText("2/3").assertExists()
    }

    @Test fun swipeOffKeepsFlicksAsCanvasPans() {
        open()
        flick(-1); compose.onNodeWithText("1/3").assertExists()
        assertTrue(!swipeEnabled)
    }

    /** A horizontal one-pointer flick across the middle of the canvas, [direction] -1 = leftwards. */
    private fun flick(direction: Int, durationMillis: Long = 160, stylus: Boolean = false) {
        val root = compose.onRoot().fetchSemanticsNode()
        val width = root.size.width.toFloat(); val height = root.size.height.toFloat()
        val origin = root.positionOnScreen
        val y = origin.y + height * .55f
        val startX = origin.x + width * (if (direction < 0) .7f else .4f)
        val endX = startX + direction * width * .25f
        val steps = 12
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, Offset(startX, y), down, down, stylus)
        for (i in 1..steps) {
            SystemClock.sleep(durationMillis / steps)
            motion(MotionEvent.ACTION_MOVE, Offset(startX + (endX - startX) * i / steps, y), down, SystemClock.uptimeMillis(), stylus)
        }
        motion(MotionEvent.ACTION_UP, Offset(endX, y), down, SystemClock.uptimeMillis(), stylus)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long, time: Long, stylus: Boolean) {
        val event = MotionEvent.obtain(down, time, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .6f; size = .01f; touchMajor = 8f }),
            0, 0, 1f, 1f, 0, 0, if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
}
