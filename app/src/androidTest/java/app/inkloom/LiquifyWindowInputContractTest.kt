package app.inkloom

import android.content.Context
import android.os.SystemClock
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class LiquifyWindowInputContractTest {
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

    @Test fun modeSwitchAfterPenUpReconstructsAndPersistsWithExactUndoRedo() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Brushes"); tap("Grid view"); tap("Brush category Pen"); tap("Brush preset Monoline")
        stroke(pagePoint(view, .32f, .28f), pagePoint(view, .32f, .68f))
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        val original = pixels(view)
        assertTrue(original.any { it ushr 24 != 0 })
        tap("Brushes"); tap("Brush category Liquify"); tap("Brush preset Push")
        assertEquals(EditorTool.LIQUIFY, view.tool)
        stroke(pagePoint(view, .32f, .47f), pagePoint(view, .42f, .47f))
        compose.waitForIdle()
        val pushed = pixels(view)
        assertTrue("Push must deform the original line", !original.contentEquals(pushed))
        tap("Brushes")
        assertEquals("Opening the brush library must retain the active Liquify edit session", EditorTool.LIQUIFY, view.tool)
        tap("Brush preset Reconstruct")
        assertEquals(app.inkloom.document.LiquifyMode.RECONSTRUCT, view.liquifyMode)
        stroke(pagePoint(view, .40f, .47f), pagePoint(view, .40f, .48f))
        compose.waitForIdle()
        val reconstructed = pixels(view)
        assertTrue("A new contact must recover the earlier Push, not start from an empty warp", !pushed.contentEquals(reconstructed))
        assertTrue("Reconstruction must approach the original alpha coverage", alphaDistance(original, reconstructed) < alphaDistance(original, pushed))
        tap("Undo"); compose.waitForIdle()
        assertTrue(pushed.contentEquals(pixels(view)))
        tap("Undo"); compose.waitForIdle()
        assertTrue(original.contentEquals(pixels(view)))
        tap("Redo"); tap("Redo"); compose.waitForIdle()
        assertTrue(reconstructed.contentEquals(pixels(view)))
        SystemClock.sleep(1800)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val reopened = canvas()
        compose.waitUntil(10_000) { reconstructed.contentEquals(pixels(reopened)) }
    }

    private fun pixels(view: InkCanvasView): IntArray {
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        return snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
    }

    private fun alphaDistance(first: IntArray, second: IntArray): Long = first.indices.sumOf {
        kotlin.math.abs((first[it] ushr 24) - (second[it] ushr 24)).toLong()
    }

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return Offset(location[0] + x * view.width, location[1] + y * view.height)
    }

    private fun tap(node: String) {
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        var result: Offset? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null || !node.refresh()) return
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
