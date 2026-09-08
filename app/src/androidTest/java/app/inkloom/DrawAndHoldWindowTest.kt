package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
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
class DrawAndHoldWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun ordinaryPenRegularizesBeforeUpAndCommitsOneUndoableStroke() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val revision = requireNotNull(view.activeRasterSnapshotForTest()).revision
        val time = roughStroke(view, curved = false)
        compose.waitUntil(4_000) { view.heldStrokeKind != null }
        assertEquals(app.inkloom.engine.v5.runtime.StrokeRegularizer.Kind.LINE, view.heldStrokeKind)
        assertEquals("Holding is still an uncommitted preview", revision, view.activeRasterSnapshotForTest()?.revision)
        val endpoint = pagePoint(view, .7f, .55f)
        event(MotionEvent.ACTION_MOVE, endpoint, time, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_UP, endpoint, time, MotionEvent.TOOL_TYPE_STYLUS)
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        val points = view.v5LastCommittedNormalizedPoints()
        assertTrue(points.size > 30)
        assertEquals(.7f, points.last().first, .005f)
        assertEquals(.55f, points.last().second, .005f)
        val a = points.first(); val b = points.last()
        assertTrue("Committed geometry must be a straight line", points.all {
            kotlin.math.abs((it.first - a.first) * (b.second - a.second) - (it.second - a.second) * (b.first - a.first)) < .0001f
        })
        val committed = requireNotNull(view.activeRasterSnapshotForTest())
        assertEquals(revision + 1, committed.revision)
        tap("Undo")
        compose.waitUntil(5_000) { view.activeRasterSnapshotForTest()?.tileCount == 0 }
        tap("Redo")
        compose.waitUntil(5_000) { view.activeRasterSnapshotForTest()?.tileCount == committed.tileCount }
        val beforeZoom = view.viewportScale
        SystemClock.sleep(500)
        pinch(view); compose.waitForIdle()
        assertTrue(view.viewportScale > beforeZoom + .05f)
    }

    @Test fun heldCurveKeepsItsCurvatureAndCanBeErasedAfterReopen() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val time = roughStroke(view, curved = true)
        compose.waitUntil(4_000) { view.heldStrokeKind != null }
        assertEquals(app.inkloom.engine.v5.runtime.StrokeRegularizer.Kind.CURVE, view.heldStrokeKind)
        event(MotionEvent.ACTION_UP, pagePoint(view, .65f, .45f), time, MotionEvent.TOOL_TYPE_STYLUS)
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        assertTrue(view.v5LastCommittedNormalizedPoints().minOf { it.second } < .38f)
        val committed = requireNotNull(view.activeRasterSnapshotForTest())
        val x = (committed.logicalWidth * .3f).toInt()
        val y = (committed.logicalHeight * .45f).toInt()
        assertNotEquals(0, committed.pixelAt(x, y))
        SystemClock.sleep(1500)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val restored = canvas()
        compose.waitUntil(5_000) { restored.activeRasterSnapshotForTest()?.pixelAt(x, y) == committed.pixelAt(x, y) }
        tap("Eraser")
        stroke(pagePoint(restored, .26f, .45f), pagePoint(restored, .34f, .45f))
        restored.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        assertEquals(0, restored.activeRasterSnapshotForTest()?.pixelAt(x, y))
    }

    @Test fun cancellationDropsHeldPreviewAndNextStrokeStillDraws() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val time = roughStroke(view, curved = false)
        compose.waitUntil(4_000) { view.heldStrokeKind != null }
        event(MotionEvent.ACTION_CANCEL, pagePoint(view, .65f, .45f), time, MotionEvent.TOOL_TYPE_STYLUS)
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        assertEquals(0, view.activeRasterSnapshotForTest()?.tileCount)
        stroke(pagePoint(view, .3f, .6f), pagePoint(view, .6f, .6f))
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        assertTrue(requireNotNull(view.activeRasterSnapshotForTest()).tileCount > 0)
        assertTrue(view.v5LastCommittedNormalizedPoints().size < 32)
    }

    private fun roughStroke(view: InkCanvasView, curved: Boolean): Long {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, pagePoint(view, .3f, .45f), time, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(30) { index ->
            val t = (index + 1) / 30f
            val y = if (curved) .45f - .36f * t * (1f - t) else .45f + if (index == 29) 0f else kotlin.math.sin(index.toFloat()) * .002f
            event(MotionEvent.ACTION_MOVE, pagePoint(view, .3f + .35f * t, y), time, MotionEvent.TOOL_TYPE_STYLUS)
            SystemClock.sleep(8)
        }
        return time
    }

    private fun pinch(view: InkCanvasView) {
        val time = SystemClock.uptimeMillis()
        fun packet(action: Int, first: Offset, second: Offset? = null) {
            val points = listOfNotNull(first, second)
            val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, points.size,
                Array(points.size) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } },
                Array(points.size) { index -> MotionEvent.PointerCoords().apply { x = points[index].x; y = points[index].y; pressure = 1f; touchMajor = 8f } },
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
            SystemClock.sleep(20)
        }
        val a = pagePoint(view, .35f, .6f); val b = pagePoint(view, .65f, .6f)
        packet(MotionEvent.ACTION_DOWN, a)
        packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), a, b)
        val c = pagePoint(view, .2f, .6f); val d = pagePoint(view, .8f, .6f)
        repeat(8) { index -> val t = (index + 1) / 8f; packet(MotionEvent.ACTION_MOVE, a + (c - a) * t, b + (d - b) * t) }
        packet(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), c, d)
        packet(MotionEvent.ACTION_UP, c)
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
                visit(compose.activity.window.decorView)
            }
            result != null
        }
        return requireNotNull(result)
    }
}
