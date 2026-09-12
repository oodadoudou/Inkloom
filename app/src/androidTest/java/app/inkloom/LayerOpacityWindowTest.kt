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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
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

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class LayerOpacityWindowTest {
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
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("color-mixing-board.bin").delete()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun opacityPreviewCancelCommitUndoAndPersistenceUseTheRealWindow() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        val layerId = view.activeLayerId
        fun opacity() = requireNotNull(model.currentPage()).layers.first { it.id == layerId }.opacity
        val a = pagePoint(view, .25f, .3f); val b = pagePoint(view, .6f, .3f)
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == layerId }?.rasterTileSurface != null }
        val ink = pixels(view)
        assertTrue(ink.any { it != 0 })
        val probe = a + (b - a) * .5f
        fun brightness(): Int {
            SystemClock.sleep(120)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { return android.graphics.Color.red(bitmap.getPixel(probe.x.toInt(), probe.y.toInt())) }
            finally { bitmap.recycle() }
        }
        val dark = brightness()
        tap("Layers"); tap("N")
        val slider = bounds("Layer opacity")
        fun point(f: Float) = Offset(slider.left + 20f + (slider.width() - 40f) * f, slider.exactCenterY())
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point(.5f), down, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
        assertEquals("Preview must not save", 1f, opacity())
        assertTrue("Opacity preview must visibly affect canvas pixels", brightness() > dark + 15)
        event(MotionEvent.ACTION_CANCEL, point(.5f), down, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
        assertEquals(1f, opacity()); assertTrue(kotlin.math.abs(brightness() - dark) <= 2)
        assertTrue(!view.selectionLayerTransferPending)
        tapAt(point(.5f)); compose.waitUntil(10_000) { opacity() in .48f.. .52f && !view.selectionLayerTransferPending }
        val committed = opacity()
        org.junit.Assert.assertArrayEquals("Changing layer opacity must not rewrite paint", ink, pixels(view))
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == committed }
        tapAt(point(0f)); compose.waitUntil { opacity() == 0f }
        assertTrue(brightness() > dark + 40)
        tap("Undo"); compose.waitUntil { opacity() == committed }
        tap("Close layers panel")
        stroke(pagePoint(view, .25f, .6f), pagePoint(view, .6f, .6f))
        compose.waitUntil(10_000) { !ink.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { ink.contentEquals(pixels(view)) }
        assertEquals(committed, opacity())
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == committed }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(committed, opacity())
        org.junit.Assert.assertArrayEquals(ink, pixels(canvas()))
    }

    private fun pixels(view: InkCanvasView): IntArray {
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        return snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
    }
    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val point = floatArrayOf(x * view.width, y * view.height)
        instrumentation.runOnMainSync { android.graphics.Matrix().also { view.transformMatrixToGlobal(it); it.mapPoints(point) } }
        return Offset(point[0], point[1])
    }

    private fun hasText(text: String): Boolean {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == text) return true
            return (0 until node.childCount).any { visit(node.getChild(it)) }
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun bounds(label: String): android.graphics.Rect {
        var result: android.graphics.Rect? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.contentDescription?.toString() == label) {
                    result = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result)
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
                node.refresh()
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
