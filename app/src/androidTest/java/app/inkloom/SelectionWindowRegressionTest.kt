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
class SelectionWindowRegressionTest {
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

    @Test fun stylusHoverThenLauncherXActuallyDeselects() = checkDeselect("Deselect")

    @Test fun stylusHoverThenBottomXActuallyDeselects() = checkDeselect("Deselect and exit")

    private fun checkDeselect(button: String) {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .6f))
        compose.waitForIdle()
        assertTrue("Window drag must create a selection", view.hasActiveSelection())
        val target = center(button)
        hoverThenTap(target)
        compose.waitForIdle()
        org.junit.Assert.assertFalse("$button must cancel after pen hover", view.hasActiveSelection())
        val before = view.activeRasterSnapshotForTest()?.revision
        stroke(pagePoint(view, .2f, .8f), pagePoint(view, .6f, .8f))
        compose.waitUntil(10_000) { view.activeRasterSnapshotForTest()?.revision != before }
    }

    @Test fun selectedLayerContentMovesThroughWindowTransformControl() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .5f))
        compose.waitForIdle()
        val before = requireNotNull(view.selectionMaskSurfaceSnapshot()).nonZeroBounds()!!
        hoverThenTap(center("Free transform"))
        stroke(pagePoint(view, .4f, .4f), pagePoint(view, .4f, .6f))
        compose.waitForIdle()
        val after = requireNotNull(view.selectionMaskSurfaceSnapshot()).nonZeroBounds()!!
        assertTrue("Selected content must follow a downward drag: $before -> $after", after.top > before.top + before.height / 2)
        val pixels = requireNotNull(view.activeRasterSnapshotForTest())
        val old = app.inkloom.engine.v5.surface.PixelRect((pixels.logicalWidth * .3f).toInt(), (pixels.logicalHeight * .38f).toInt(), (pixels.logicalWidth * .5f).toInt(), (pixels.logicalHeight * .42f).toInt())
        org.junit.Assert.assertTrue("The original ink must move away", pixels.copyPremultipliedPixels(old).all { it == 0 })
        val moved = app.inkloom.engine.v5.surface.PixelRect((pixels.logicalWidth * .3f).toInt(), (pixels.logicalHeight * .58f).toInt(), (pixels.logicalWidth * .5f).toInt(), (pixels.logicalHeight * .62f).toInt())
        org.junit.Assert.assertTrue("Moved ink must arrive in the destination", pixels.copyPremultipliedPixels(moved).any { it != 0 })
        val rendered = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { view.draw(android.graphics.Canvas(rendered)) }
        fun darkPixels(top: Float, bottom: Float): Int {
            var count = 0
            for (y in (view.height * top).toInt() until (view.height * bottom).toInt()) {
                for (x in (view.width * .3f).toInt() until (view.width * .5f).toInt()) {
                    if (android.graphics.Color.red(rendered.getPixel(x, y)) < 128) count++
                }
            }
            return count
        }
        try {
            assertEquals("Presented canvas must remove the original ink", 0, darkPixels(.38f, .42f))
            assertTrue("Presented canvas must show the moved ink", darkPixels(.58f, .62f) > 0)
        } finally { rendered.recycle() }

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
