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
import app.inkloom.model.EditorTool
import app.inkloom.settings.InterfaceScale
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Pen taps on the tool rail and top bar at the interface size the MovinkPad Pro 14 gets automatically
 * (about 75%). A pen hovers before it touches; showing a control's name must never cost the tap.
 */
class PenTapWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val scaled = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
            check(preferences.edit().putFloat(InterfaceScale.PREFERENCE_KEY, .75f).commit())
            try { base.evaluate() } finally { preferences.edit().remove(InterfaceScale.PREFERENCE_KEY).commit() }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(scaled).around(compose)

    /**
     * A pen that hovers over one tool until its name shows and then taps the next one must select that
     * tool, as a finger does. The name used to be a focusable popup that swallowed the tap.
     */
    @Test fun aPenTapWhileANeighbouringToolsNameShowsSelectsTheTappedTool() {
        openNote()
        for ((hovered, tapped, expected) in listOf(Triple("Eyedropper", "Select", EditorTool.SELECT), Triple("Paint bucket", "Eyedropper", EditorTool.EYEDROPPER),
                Triple("Marker", "Brush", EditorTool.PEN))) {
            hoverThenTap(hovered, tapped)
            assertEquals("a pen tap on $tapped after hovering $hovered", expected, canvas().tool)
        }
    }

    @Test fun aPenTapOnTheTopBarWhileAnotherButtonsNameShowsStillPressesIt() {
        openNote()
        hoverThenTap("Settings", "Selection")
        assertEquals(EditorTool.SELECT, canvas().tool)
    }

    /** A finger has no hover: holding a top-bar button names it without pressing it; a tap still presses. */
    @Test fun aFingerHoldNamesAButtonWithoutPressingItAndATapStillPresses() {
        openNote()
        val settings = center("Settings")
        touch(MotionEvent.ACTION_DOWN, settings)
        waitForName("Settings")
        touch(MotionEvent.ACTION_UP, settings)
        compose.waitUntil(3_000) { compose.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty() }
        assertTrue("the hold must not open Settings", compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty())
        touch(MotionEvent.ACTION_DOWN, settings); SystemClock.sleep(40); touch(MotionEvent.ACTION_UP, settings)
        compose.waitUntil(3_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openNote() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitForIdle()
    }

    private fun waitForName(label: String) =
        compose.waitUntil(3_000) { compose.onAllNodes(isPopup() and hasAnyDescendant(hasText(label))).fetchSemanticsNodes().isNotEmpty() }

    private fun center(label: String): Offset {
        val node = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
        return node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow
    }

    private var down = 0L
    private fun pen(action: Int, point: Offset) = inject(action, point, MotionEvent.TOOL_TYPE_STYLUS, InputDevice.SOURCE_STYLUS)
    private fun touch(action: Int, point: Offset) = inject(action, point, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)
    private fun inject(action: Int, point: Offset, tool: Int, source: Int) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) down = now
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) .6f else 0f })
        val hover = action in setOf(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT)
        val event = MotionEvent.obtain(if (hover) now else down, now, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0,
            source, 0)
        try { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    /** Rests the pen over [hovered] until its name shows, moves to [tapped] and taps it straight away, as a hand does. */
    private fun hoverThenTap(hovered: String, tapped: String) {
        val from = center(hovered); val to = center(tapped)
        pen(MotionEvent.ACTION_HOVER_ENTER, from)
        repeat(30) { pen(MotionEvent.ACTION_HOVER_MOVE, from + Offset(0f, (it % 3).toFloat())); SystemClock.sleep(25) }
        waitForName(hovered)
        repeat(6) { pen(MotionEvent.ACTION_HOVER_MOVE, from + (to - from) * ((it + 1) / 6f)); SystemClock.sleep(16) }
        pen(MotionEvent.ACTION_HOVER_EXIT, to)
        pen(MotionEvent.ACTION_DOWN, to); SystemClock.sleep(40); pen(MotionEvent.ACTION_UP, to)
        compose.waitForIdle(); SystemClock.sleep(300); compose.waitForIdle()
    }

    private fun canvas(): InkCanvasView {
        fun find(view: View): InkCanvasView? {
            if (view is InkCanvasView) return view
            if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it))?.let { result -> return result } }
            return null
        }
        return requireNotNull(find(compose.activity.window.decorView))
    }
}
