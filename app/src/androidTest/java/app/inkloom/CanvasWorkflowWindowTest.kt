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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class CanvasWorkflowWindowTest {
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

    @Test fun defaultNotebookPaintSurfaceKeepsNativePaperResolution() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val initial = requireNotNull(view.activeRasterSnapshotForTest())
        assertEquals("Default 1600 px paper must not be pre-downsampled", 1600, initial.logicalWidth)
        assertEquals(2263, initial.logicalHeight)
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        val painted = requireNotNull(view.activeRasterSnapshotForTest())
        assertEquals(1600, painted.logicalWidth)
        assertEquals(2263, painted.logicalHeight)
    }

    @Test fun canvasSizeDialogAppliesAndUndoesNativeDimensions() {
        tap("New Note"); tap("Create")
        val view = canvas()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Canvas & resolution").performScrollTo().performClick()
        compose.onNodeWithText("Width").performTextReplacement("1800")
        compose.onNodeWithText("Height").performTextReplacement("2400")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) { view.activeRasterSnapshotForTest()?.logicalWidth == 1800 }
        assertEquals(2400, view.activeRasterSnapshotForTest()?.logicalHeight)
        tap("Undo")
        compose.waitUntil(10_000) { view.activeRasterSnapshotForTest()?.logicalWidth == 1600 }
        assertEquals(2263, view.activeRasterSnapshotForTest()?.logicalHeight)
        tap("Redo")
        compose.waitUntil(10_000) { view.activeRasterSnapshotForTest()?.logicalWidth == 1800 }
    }

    @Test fun colorDropLeavesTwoFingerNavigationAndDrawingAvailable() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        colorDrop(view)
        val before = view.viewportScale
        pinch(view)
        compose.waitForIdle()
        assertTrue("ColorDrop must not block two-finger zoom", view.viewportScale > before + .05f)
    }

    @Test fun colorDropBecomesEditablePixelsAndErasureSurvivesReopen() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        view.awaitV5StrokeWorkerIdleForTest()
        colorDrop(view)
        val filled = requireNotNull(view.activeRasterSnapshotForTest())
        val x = (filled.logicalWidth * .5f).toInt()
        val y = (filled.logicalHeight * .7f).toInt()
        assertEquals("ColorDrop must be editable paint pixels", view.inkColor, filled.pixelAt(x, y))
        val contour = app.inkloom.engine.v5.surface.PixelRect((filled.logicalWidth * .25f).toInt(), (filled.logicalHeight * .35f).toInt(), (filled.logicalWidth * .55f).toInt(), (filled.logicalHeight * .45f).toInt())
        val palePixels = filled.copyPremultipliedPixels(contour).count { it ushr 24 < 250 }
        assertEquals("No pale antialias ring around same-color filled ink", 0, palePixels)
        tap("Eraser")
        stroke(pagePoint(view, .4f, .7f), pagePoint(view, .6f, .7f))
        view.awaitV5StrokeWorkerIdleForTest()
        compose.waitForIdle()
        val erased = requireNotNull(view.activeRasterSnapshotForTest()).pixelAt(x, y)
        assertTrue("Filled pigment must erase", erased ushr 24 < 50)
        SystemClock.sleep(1500)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val restored = canvas()
        compose.waitUntil(5_000) { restored.activeRasterSnapshotForTest()?.pixelAt(x, y) == erased }
        assertEquals(erased, restored.activeRasterSnapshotForTest()?.pixelAt(x, y))
    }

    @Test fun erasedInkStaysErasedAfterCheckpointAndBrushPanelChanges() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        val initial = requireNotNull(view.activeRasterSnapshotForTest())
        fun inkCount(): Int {
            val pixels = requireNotNull(view.activeRasterSnapshotForTest())
            val bounds = app.inkloom.engine.v5.surface.PixelRect((pixels.logicalWidth * .32f).toInt(), (pixels.logicalHeight * .395f).toInt(), (pixels.logicalWidth * .48f).toInt(), (pixels.logicalHeight * .405f).toInt())
            return pixels.copyPremultipliedPixels(bounds).count { it != 0 }
        }
        val before = inkCount()
        assertTrue(before > 0)
        tap("Eraser")
        stroke(pagePoint(view, .25f, .4f), pagePoint(view, .55f, .4f))
        compose.waitUntil(10_000) { requireNotNull(view.activeRasterSnapshotForTest()).revision > initial.revision }
        val erased = inkCount()
        assertTrue("Eraser must remove ink", erased < before / 2)
        compose.waitUntil(5_000) {
            app.inkloom.ui.EditorPanelState(instrumentation.targetContext)
                .layout.open.none { it == app.inkloom.settings.EditorPanel.BRUSHES }
        }
        tapAt(centerTopmost("Brushes")); compose.waitForIdle()
        tap("Close brushes panel")
        SystemClock.sleep(1500)
        assertEquals("Checkpoint and brush panel must not restore erased pixels", erased, inkCount())
    }

    @Test fun panelResizeReturnsKeyboardHoverZoomAndDrawingToCanvas() = panelGeometryThenInput(resize = true)

    @Test fun panelDragReturnsKeyboardHoverZoomAndDrawingToCanvas() = panelGeometryThenInput(resize = false)

    private fun panelGeometryThenInput(resize: Boolean) {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Layers")
        val start = center(if (resize) "Resize layers panel · bottom-left corner" else "Drag layers panel")
        val end = start + if (resize) Offset(-100f, -100f) else Offset(-160f, 90f)
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_FINGER)
        for (index in 1..24) {
            event(MotionEvent.ACTION_MOVE, start + (end-start)*(index/24f), down, MotionEvent.TOOL_TYPE_FINGER)
            SystemClock.sleep(16)
        }
        event(MotionEvent.ACTION_UP, end, down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        tap("Close layers panel")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_E); compose.waitForIdle()
        assertEquals(EditorTool.ERASER, view.tool)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_B); compose.waitForIdle()
        assertEquals(EditorTool.PEN, view.tool)
        hoverThenTap(pagePoint(view, .25f, .3f))
        val scale = view.viewportScale
        pinch(view); compose.waitForIdle()
        assertTrue(view.viewportScale > scale)
        stroke(visibleCanvasPoint(view, .35f, .5f), visibleCanvasPoint(view, .6f, .5f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
    }

    private fun colorDrop(view: InkCanvasView) {
        val start = center("Colors") + Offset(-8f, 8f)
        val end = pagePoint(view, .5f, .7f)
        val injector = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val drag = injector.submit {
                instrumentation.uiAutomation.executeShellCommand(
                    "input touchscreen draganddrop ${start.x.toInt()} ${start.y.toInt()} ${end.x.toInt()} ${end.y.toInt()} 1000",
                ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
            }
            // Compose's long-press deadline uses its test clock, while Android drag dispatch
            // uses real uptime. Advance frames while the real window gesture runs.
            while (!drag.isDone) { compose.mainClock.advanceTimeBy(16); SystemClock.sleep(8) }
            drag.get()
        } finally { injector.shutdownNow() }
        compose.waitForIdle()
        center("Continue filling")
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

    private fun visibleCanvasPoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val visible = android.graphics.Rect()
        instrumentation.runOnMainSync { assertTrue(view.getGlobalVisibleRect(visible)) }
        return Offset(visible.left + x * visible.width(), visible.top + y * visible.height())
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

    private fun centerTopmost(label: String): Offset {
        var result: android.graphics.Rect? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (result == null || bounds.top < requireNotNull(result).top) result = bounds
                }
                repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result).let { Offset(it.exactCenterX(), it.exactCenterY()) }
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
