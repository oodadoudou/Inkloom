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

/** T15: the copy stamp takes its source from a tap and paints a copy of it elsewhere, undoably. */
class CloneStampWindowTest {
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

    @Test fun copyStampClonesFromTheChosenSourceAndUndoes() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Brushes"); tap("Grid view"); tap("Brush category Pen"); tap("Brush preset Monoline")
        stroke(pagePoint(view, .30f, .25f), pagePoint(view, .30f, .70f))
        view.awaitV5StrokeWorkerIdleForTest(); compose.waitForIdle()
        val original = pixels(view)
        tap("Brushes"); tap("Brush category Blend"); tap("Brush preset Copy Stamp")
        assertEquals(EditorTool.SMUDGE, view.tool)
        assertTrue("the copy stamp replaces smearing", view.cloneStamp.enabled)
        // The first pen touch chooses the source; it paints nothing.
        pagePoint(view, .30f, .45f).let { stroke(it, it) }; compose.waitForIdle()
        assertTrue(view.cloneStamp.source != null)
        assertTrue(original.contentEquals(pixels(view)))
        stroke(pagePoint(view, .65f, .40f), pagePoint(view, .65f, .50f))
        compose.waitForIdle()
        val cloned = pixels(view)
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        fun inkNear(fx: Float, fy: Float): Boolean {
            val cx = (fx * snapshot.logicalWidth).toInt(); val cy = (fy * snapshot.logicalHeight).toInt()
            return (-12..12).any { dx -> (snapshot.pixelAt(cx + dx, cy) ushr 24) > 128 }
        }
        assertTrue("the line is copied beside the stroke", inkNear(.65f, .45f))
        assertTrue("the source stays", inkNear(.30f, .45f))
        assertTrue("outside the stroke nothing is copied", !inkNear(.65f, .65f))
        tap("Undo"); compose.waitForIdle()
        assertTrue(original.contentEquals(pixels(view)))
        tap("Redo"); compose.waitForIdle()
        assertTrue(cloned.contentEquals(pixels(view)))
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
