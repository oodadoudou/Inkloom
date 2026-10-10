package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Panel gestures crossing the real editor canvas must never become ink. */
class PanelCanvasIsolationWindowTest {
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
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun stylusPanelDragsAcrossTheCanvasAndASecondFingerLeaveNoInk() {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Layers")
        val grip = point("Move panel group")
        val across = pagePoint(view, .5f, .5f)
        // A held stylus on the group grip, dragged slowly over the canvas.
        stroke(grip, across, stylus = true, holdMillis = 650)
        SystemClock.sleep(300); compose.waitForIdle()
        assertNotEquals("the stylus actually moved the panel", grip, point("Move panel group"))
        assertTrue("panel moves leave the raster empty", empty(view))
        // A second finger landing on the canvas cancels the panel drag; neither contact paints.
        val start = point("Move panel group")
        val time = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, listOf(start), time)
        for (i in 1..6) { motion(MotionEvent.ACTION_MOVE, listOf(start + Offset(12f * i, 0f)), time); SystemClock.sleep(16) }
        val second = pagePoint(view, .3f, .7f)
        motion(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(start + Offset(72f, 0f), second), time)
        for (i in 1..6) { motion(MotionEvent.ACTION_MOVE, listOf(start + Offset(72f + 10f * i, 0f), second + Offset(20f * i, 0f)), time); SystemClock.sleep(16) }
        motion(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(start + Offset(132f, 0f), second + Offset(120f, 0f)), time)
        motion(MotionEvent.ACTION_UP, listOf(start + Offset(132f, 0f)), time)
        SystemClock.sleep(300); compose.waitForIdle()
        assertTrue("a cancelled panel gesture leaves the raster empty", empty(view))
        // Control: an ordinary stylus stroke on the canvas still paints.
        tap("Close layers panel")
        stroke(pagePoint(view, .2f, .5f), pagePoint(view, .6f, .5f), stylus = true, holdMillis = 0)
        compose.waitUntil(10_000) { !empty(view) }
        assertNotEquals(0, view.activeRasterSnapshotForTest()!!.tileCount)
    }

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

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val point = floatArrayOf(x * view.width, y * view.height)
        instrumentation.runOnMainSync { android.graphics.Matrix().also { view.transformMatrixToGlobal(it); it.mapPoints(point) } }
        return Offset(point[0], point[1])
    }

    private fun point(label: String): Offset {
        val node = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
        return node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow
    }

    private fun tap(label: String) {
        val node = compose.onNode(androidx.compose.ui.test.hasContentDescription(label) or androidx.compose.ui.test.hasText(label))
            .assertIsDisplayed().fetchSemanticsNode()
        val target = node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow
        val time = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, listOf(target), time); motion(MotionEvent.ACTION_UP, listOf(target), time)
        compose.waitForIdle()
    }

    private fun stroke(start: Offset, end: Offset, stylus: Boolean, holdMillis: Long) {
        val time = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, listOf(start), time, stylus); SystemClock.sleep(holdMillis)
        for (i in 1..24) { motion(MotionEvent.ACTION_MOVE, listOf(start + (end - start) * (i / 24f)), time, stylus); SystemClock.sleep(16) }
        motion(MotionEvent.ACTION_UP, listOf(end), time, stylus)
        compose.waitForIdle()
    }

    private fun motion(action: Int, points: List<Offset>, down: Long, stylus: Boolean = false) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points.size,
            Array(points.size) { index -> MotionEvent.PointerProperties().apply {
                id = index; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER } },
            Array(points.size) { index -> MotionEvent.PointerCoords().apply {
                x = points[index].x; y = points[index].y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f } },
            0, 0, 1f, 1f, 0, 0, if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
}
