package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
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
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class PressureSettingsWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun procreateGesturesCanBeTurnedOffAndAboutListsNoticesAndPrivacy() {
        tap("Settings")
        tap("Gestures")
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasText("Three-finger scrub to clear layer"))
        // The switch on the same row as the label.
        val row = compose.onNode(hasText("Three-finger scrub to clear layer")).fetchSemanticsNode().boundsInRoot
        val switches = compose.onAllNodes(isToggleable())
        val index = switches.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.center.y in row.top..row.bottom }
        switches[index].performClick()
        switches[index].assertIsOff()
        compose.waitUntil(5_000) { !app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load().threeFingerScrubClear }
        compose.onNodeWithContentDescription("Settings sections").performScrollToNode(hasText("About"))
        tap("About")
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasText("ONNX Runtime"))
        compose.onNode(hasText("Inkloom has no internet access", substring = true)).assertExists()
    }

    @Test fun curveDragCommitsOnceAndScratchpadUsesProductionPressure() {
        tap("Settings")
        tap("Pressure")
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasContentDescription("Editable pressure curve"))
        val graph = center("Editable pressure curve")
        val preferences = instrumentation.targetContext.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "pressureResponseCurve") writes++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try { stroke(graph, graph + Offset(80f, -100f)) } finally { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        assertTrue("A drag must commit once after release", writes == 1)
        val settings = app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load()
        assertNotEquals(app.inkloom.document.PressureResponseCurve.LINEAR, settings.pressureResponseCurve)
        assertEquals(6, settings.pressureResponseCurve.size)
        assertTrue("Input coordinate must move horizontally", settings.pressureResponseCurve[2] > .5f)
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasText("Reset pressure settings"))
        val view = canvas()
        assertEquals(settings.pressureResponseCurve, view.pressureResponseCurve)
        stroke(pagePoint(view, .2f, .4f), pagePoint(view, .8f, .4f))
        compose.waitUntil(5_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        tap("Clear test pad")
        compose.waitUntil(5_000) { canvas() !== view }
        assertEquals(0, canvas().activeRasterSnapshotForTest()?.tileCount)
        tap("Reset pressure settings")
        assertEquals(app.inkloom.document.PressureResponseCurve.LINEAR,
            app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load().pressureResponseCurve)
    }

    @Test fun pressurePointCanBeAddedAndRemovedWithoutLeavingTheGraph() {
        tap("Settings")
        tap("Pressure")
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasContentDescription("Editable pressure curve"))
        compose.onNodeWithContentDescription("Editable pressure curve").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(6, app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load().pressureResponseCurve.size)
        compose.onNodeWithContentDescription("Editable pressure curve").performTouchInput {
            down(center)
            moveTo(Offset(center.x, -80f))
            up()
        }
        compose.waitForIdle()
        assertEquals(app.inkloom.document.PressureResponseCurve.LINEAR,
            app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load().pressureResponseCurve)
    }

    @Test fun globalStabilizationSliderPersistsAndReachesTheTestCanvas() {
        tap("Settings")
        tap("Pressure")
        val label = "Global stabilization (%) slider"
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasContentDescription(label))
        tap(label)
        val settings = app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load()
        assertTrue(settings.globalStrokeStabilization > 0f)
        compose.onNodeWithContentDescription("Settings content").performScrollToNode(hasText("Reset pressure settings"))
        assertEquals(settings.globalStrokeStabilization, canvas().globalStrokeStabilization, 0f)
        val view = canvas()
        stroke(pagePoint(view, .2f, .4f), pagePoint(view, .8f, .4f))
        compose.waitUntil(5_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
    }

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return Offset(location[0] + x * view.width, location[1] + y * view.height)
    }

    private fun hoverThenTap(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_HOVER_ENTER, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_HOVER_MOVE, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        SystemClock.sleep(180)
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_STYLUS)
    }

    private fun tap(node: String) {
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        var result: Offset? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    result = Offset(rect.exactCenterX(), rect.exactCenterY())
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result)
    }

    private fun tapAt(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun stroke(start: Offset, end: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(24) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 24f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, MotionEvent.TOOL_TYPE_STYLUS)
        }
        event(MotionEvent.ACTION_UP, end, time, MotionEvent.TOOL_TYPE_STYLUS)
    }

    private fun event(action: Int, point: Offset, down: Long, type: Int) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0,
            if (type == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Window rejected input", instrumentation.uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                fun visit(view: View) {
                    if (view is InkCanvasView && view.isShown) result = view
                    else if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
            }
            result != null
        }
        return requireNotNull(result)
    }
}
