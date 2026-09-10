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
class RasterLayerMergeWindowTest {
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

    @Test fun actualLayerMenuMergesJustFinishedStrokesAndUndoRedoSurvivesFurtherDrawing() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        val title = requireNotNull(model.currentDocument()).title
        tap("Layers"); tap("Add layer"); tap("Close layers panel")
        val lowerId = view.activeLayerId
        val lowerName = requireNotNull(model.currentPage()).layers.first { it.id == lowerId }.name
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .65f, .3f))
        tap("Layers"); tap("Add layer"); tap("Close layers panel")
        val upperId = view.activeLayerId
        val upperName = requireNotNull(model.currentPage()).layers.first { it.id == upperId }.name
        org.junit.Assert.assertNotEquals("Adding a layer must select a distinct new layer", lowerId, upperId)
        stroke(pagePoint(view, .2f, .6f), pagePoint(view, .65f, .6f))
        tap("Layers"); tapLayerActions(upperName); tap("Merge down")
        compose.waitUntil(15_000) { model.currentPage()?.layers?.none { it.id == upperId } == true && !view.selectionLayerTransferPending }
        assertEquals(lowerId, view.activeLayerId)
        val merged = pixels(view)
        val width = requireNotNull(view.activeRasterSnapshotForTest()).logicalWidth
        val height = requireNotNull(view.activeRasterSnapshotForTest()).logicalHeight
        assertTrue("Lower stroke disappeared", merged.sliceArray((height * .28f).toInt() * width until (height * .32f).toInt() * width).any { it != 0 })
        assertTrue("Last upper stroke disappeared", merged.sliceArray((height * .58f).toInt() * width until (height * .62f).toInt() * width).any { it != 0 })
        tap("Undo")
        compose.waitUntil { model.currentPage()?.layers?.any { it.id == upperId } == true }
        assertEquals(upperId, view.activeLayerId)
        tap("Redo")
        compose.waitUntil { model.currentPage()?.layers?.none { it.id == upperId } == true }
        org.junit.Assert.assertArrayEquals(merged, pixels(view))
        tap("Close layers panel")
        stroke(pagePoint(view, .2f, .8f), pagePoint(view, .65f, .8f))
        compose.waitUntil(10_000) { !merged.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { merged.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil { model.currentPage()?.layers?.any { it.id == upperId } == true }
        tap("Redo"); compose.waitUntil { model.currentPage()?.layers?.none { it.id == upperId } == true }
        org.junit.Assert.assertArrayEquals(merged, pixels(view))
        tap("Gallery"); tap("Notebook $title. Long press to move.")
        val restored = canvas()
        compose.waitUntil(10_000) { model.currentPage()?.layers?.firstOrNull { it.id == lowerId }?.rasterTileSurface != null }
        // Selecting the merged layer must restore its durable pair, not only the session preview.
        tap("Layers"); tap(lowerName); tap("Close layers panel")
        org.junit.Assert.assertArrayEquals(merged, pixels(restored))
    }

    private fun tapLayerActions(name: String) {
        val row = center(name)
        val candidates = mutableListOf<Offset>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            node.refresh()
            if (node.contentDescription?.toString() == "Layer actions") {
                val rect = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                candidates += Offset(rect.exactCenterX(), rect.exactCenterY())
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        tapAt(requireNotNull(candidates.minByOrNull { kotlin.math.abs(it.y - row.y) }))
        compose.waitForIdle()
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
