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
        // Synchronous UIAutomation injection can pause between points on a loaded emulator.
        // Keep the production timer unchanged; allow the synthetic curve to finish first.
        view.drawAndHoldDelayMillis = 1500L
        val time = roughStroke(view, curved = true)
        compose.waitUntil(4_000) { view.heldStrokeKind != null }
        assertEquals(app.inkloom.engine.v5.runtime.StrokeRegularizer.Kind.CURVE, view.heldStrokeKind)
        event(MotionEvent.ACTION_UP, pagePoint(view, .65f, .45f), time, MotionEvent.TOOL_TYPE_STYLUS)
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        val committedPoints = view.v5LastCommittedNormalizedPoints()
        assertTrue("Expected a bowed curve, got ${committedPoints.size} points with y=" +
            committedPoints.minOf { it.second } + ".." + committedPoints.maxOf { it.second },
            committedPoints.minOf { it.second } < .38f)
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

    @Test fun heldCircleCanBeReshapedWithItsHandles() {
        tap("New Note"); tap("Create")
        val view = canvas()
        view.drawAndHoldDelayMillis = 1500L
        val centre = pagePoint(view, .5f, .5f)
        val radius = view.width * .15f
        val time = SystemClock.uptimeMillis()
        fun on(angle: Float, r: Float = radius) = Offset(centre.x + r * kotlin.math.cos(angle), centre.y + r * kotlin.math.sin(angle))
        event(MotionEvent.ACTION_DOWN, on(0f), time, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(60) { index ->
            val angle = (index + 1) / 60f * 2f * Math.PI.toFloat() * 1.03f
            event(MotionEvent.ACTION_MOVE, on(angle, radius + kotlin.math.sin(index * 1.3f) * 2f), time, MotionEvent.TOOL_TYPE_STYLUS, sync = false)
            SystemClock.sleep(8)
        }
        instrumentation.waitForIdleSync()
        compose.waitUntil(4_000) { view.heldStrokeKind != null }
        assertEquals(app.inkloom.engine.v5.runtime.StrokeRegularizer.Kind.CIRCLE, view.heldStrokeKind)
        event(MotionEvent.ACTION_UP, on(2f * Math.PI.toFloat() * 1.03f), time, MotionEvent.TOOL_TYPE_STYLUS)
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        compose.waitUntil(5_000) { view.shapeEditor != null }
        val committed = requireNotNull(view.activeRasterSnapshotForTest())
        val scale = committed.logicalWidth / view.width.toFloat()
        val location = IntArray(2); instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val fitted = requireNotNull(view.shapeEditor).shape as app.inkloom.engine.v5.runtime.HeldShape.Ellipse
        // The top of the fitted circle, and of the same circle 1.4 times as large, in surface pixels.
        val oldTop = fitted.cx.toInt() to (fitted.cy - fitted.a).toInt()
        val newTop = fitted.cx.toInt() to (fitted.cy - fitted.a * 1.4f).toInt()
        fun inked(snapshot: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot, p: Pair<Int, Int>) =
            // The brush's own pressure and shape put the ink within a few pixels of the centre line.
            (p.second - 12..p.second + 12).any { snapshot.pixelAt(p.first, it) != 0 }
        val pts = view.v5LastCommittedNormalizedPoints().map { (x, y) -> kotlin.math.hypot(x * committed.logicalWidth - fitted.cx, y * committed.logicalHeight - fitted.cy) }
        assertTrue("fitted=$fitted ink=" + app.inkloom.engine.v5.tools.selectionContentBounds(committed) +
            " committed radii ${pts.minOrNull()}..${pts.maxOrNull()} n=${pts.size}", inked(committed, oldTop))

        // Drag the radius handle out to 1.4 × the radius.
        val (hx, hy) = requireNotNull(view.shapeEditor).shape.handles()[1]
        val handle = Offset(location[0] + hx / scale, location[1] + hy / scale)
        val target = on(0f, radius * 1.4f)
        val dragTime = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, handle, dragTime, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(12) { index -> event(MotionEvent.ACTION_MOVE, handle + (target - handle) * ((index + 1) / 12f), dragTime, MotionEvent.TOOL_TYPE_STYLUS); SystemClock.sleep(40) }
        event(MotionEvent.ACTION_UP, target, dragTime, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
        val reshaped = requireNotNull(view.activeRasterSnapshotForTest())
        val edited = requireNotNull(view.shapeEditor).shape as app.inkloom.engine.v5.runtime.HeldShape.Ellipse
        assertEquals(fitted.a * 1.4f, edited.a, fitted.a * .05f)
        val editedTop = edited.cx.toInt() to (edited.cy - edited.a).toInt()
        assertTrue("the old circle is gone", !inked(reshaped, oldTop))
        assertTrue("the larger circle is drawn", inked(reshaped, editedTop))

        tap("Undo")
        compose.waitUntil(5_000) { view.activeRasterSnapshotForTest()?.let { inked(it, oldTop) } == true }
        assertTrue(!inked(requireNotNull(view.activeRasterSnapshotForTest()), editedTop))
        assertTrue(newTop.second < oldTop.second)
        tap("Undo")
        compose.waitUntil(5_000) { view.activeRasterSnapshotForTest()?.tileCount == 0 }
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
            val y = if (curved) .45f - .5f * t * (1f - t) else .45f + if (index == 29) 0f else kotlin.math.sin(index.toFloat()) * .002f
            event(MotionEvent.ACTION_MOVE, pagePoint(view, .3f + .35f * t, y), time, MotionEvent.TOOL_TYPE_STYLUS, sync = false)
            SystemClock.sleep(8)
        }
        instrumentation.waitForIdleSync()
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

    private fun event(action: Int, point: Offset, down: Long, type: Int, sync: Boolean = true) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0,
            if (type == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Window rejected input", instrumentation.uiAutomation.injectInputEvent(motion, sync)) }
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
