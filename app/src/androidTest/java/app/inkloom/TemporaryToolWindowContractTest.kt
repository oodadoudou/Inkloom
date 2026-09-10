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
class TemporaryToolWindowContractTest {
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
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().remove("studioTab").commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun leavingSelectionRetainsTheFactoryPencilForTheNextStroke() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        assertEquals("system-hb-pencil", canvas.brushId)
        tap("Selection")
        tap("Exit selection tool")
        val position = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(position) }
        val start = Offset(position[0] + canvas.width * .12f, position[1] + canvas.height * .35f)
        stroke(start, Offset(position[0] + canvas.width * .45f, start.y))
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint != null }
        val execution = requireNotNull(canvas.lastBrushExecutionFingerprint)
        android.util.Log.i("InkloomTemporaryToolContract", execution.toString())
        assertEquals("Leaving selection must keep the explicitly selected factory brush", "system-hb-pencil", execution.presetId)
    }

    @Test fun foregroundChosenDuringSelectionSurvivesReturningToDrawing() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        val initialColor = canvas.inkColor
        tap("Selection")
        tap("Freehand")
        tap("Colors")
        tap("Saturation and brightness field")
        val chosenColor = canvas.inkColor
        assertNotEquals(initialColor, chosenColor)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        tap("Exit selection tool")
        assertEquals("Temporary tool return must not revert the foreground", chosenColor, canvas.inkColor)
        assertEquals("system-hb-pencil", canvas.brushId)
    }

    @Test fun eraserShortcutReturnsToTheCapturedFactoryPencil() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_E)
        compose.waitForIdle()
        assertEquals(EditorTool.ERASER, canvas.tool)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_E)
        compose.waitForIdle()
        val position = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(position) }
        val start = Offset(position[0] + canvas.width * .12f, position[1] + canvas.height * .35f)
        stroke(start, Offset(position[0] + canvas.width * .45f, start.y))
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint != null }
        assertEquals("system-hb-pencil", canvas.lastBrushExecutionFingerprint?.presetId)
    }

    @Test fun nestedEraserShortcutStillAllowsSelectionToFinishWithItsBrush() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Selection")
        tap("Freehand")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_E)
        compose.waitForIdle()
        assertEquals(EditorTool.ERASER, canvas.tool)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_E)
        compose.waitForIdle()
        assertEquals(EditorTool.SELECT, canvas.tool)
        tap("Exit selection tool")
        assertEquals(EditorTool.PEN, canvas.tool)
        assertEquals("system-hb-pencil", canvas.brushId)
        val position = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(position) }
        val start = Offset(position[0] + canvas.width * .12f, position[1] + canvas.height * .35f)
        stroke(start, Offset(position[0] + canvas.width * .45f, start.y))
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint != null }
        assertEquals("system-hb-pencil", canvas.lastBrushExecutionFingerprint?.presetId)
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
