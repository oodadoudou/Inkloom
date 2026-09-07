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
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class ColorWindowInputContractTest {
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

    @Test fun firstHueDownFromDefaultGraySurvivesSaturationAndKeepsPencil() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Colors")
        assertHueSelectionSurvivesNeutralColor(canvas)
    }

    @Test fun firstHueFromBlackIsUsedWhenSaturationAndBrightnessIncrease() {
        verifyNeutralEndpoint(1f)
    }

    @Test fun firstHueFromWhiteIsUsedWhenSaturationIncreases() {
        verifyNeutralEndpoint(0f)
    }

    private fun verifyNeutralEndpoint(verticalFraction: Float) {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Colors")
        val field = bounds("Saturation and brightness field")
        // Start inside the field, then drag just beyond its edge to exercise the exact clamped endpoint.
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, Offset(field.exactCenterX(), field.exactCenterY()), down, MotionEvent.TOOL_TYPE_FINGER)
        val endpoint = Offset(field.left - 2f, if (verticalFraction == 0f) field.top - 2f else field.bottom + 2f)
        event(MotionEvent.ACTION_MOVE, endpoint, down, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, endpoint, down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        assertEquals(if (verticalFraction == 0f) android.graphics.Color.WHITE else android.graphics.Color.BLACK, canvas.inkColor)
        assertHueSelectionSurvivesNeutralColor(canvas)
    }

    private fun assertHueSelectionSurvivesNeutralColor(canvas: InkCanvasView) {
        val originalColor = canvas.inkColor
        val ring = bounds("Hue ring")
        val contact = Offset(ring.exactCenterX(), ring.top + ring.height() * .07f)
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, contact, time, MotionEvent.TOOL_TYPE_FINGER)
        try {
            compose.waitUntil(2_000) { hasText("270°") }
            assertEquals("Choosing hue must not increase saturation or brightness", originalColor, canvas.inkColor)
        } finally {
            event(MotionEvent.ACTION_UP, contact, time, MotionEvent.TOOL_TYPE_FINGER)
        }
        compose.waitForIdle()
        assertEquals("system-hb-pencil", canvas.brushId)
        val field = bounds("Saturation and brightness field")
        tapAt(Offset(field.left + field.width() * .8f, field.top + field.height() * .3f))
        compose.waitForIdle()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(canvas.inkColor, hsv)
        assertEquals("SV must use the hue selected while RGB had no hue", 270f, hsv[0], 1f)
        assertTrue(hsv[1] > .7f)
        assertEquals("system-hb-pencil", canvas.brushId)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val location = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(location) }
        val start = Offset(location[0] + canvas.width * .15f, location[1] + canvas.height * .4f)
        stroke(start, Offset(location[0] + canvas.width * .55f, start.y))
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint != null }
        assertEquals("The next actual stroke must execute the selected pencil", "system-hb-pencil", canvas.lastBrushExecutionFingerprint?.presetId)
        val snapshot = requireNotNull(canvas.activeRasterSnapshotForTest())
        val pixels = snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
        assertTrue("The selected violet must reach committed pixels", pixels.any { pixel ->
            val alpha = pixel ushr 24 and 255
            alpha > 20 && android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) && android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel)
        })
    }

    @Test fun saturatedHueDragKeepsWorkspaceWritesAtTheGestureBoundary() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Colors")
        val field = bounds("Saturation and brightness field")
        tapAt(Offset(field.left + field.width() * .8f, field.top + field.height() * .3f))
        compose.waitForIdle()
        val brushBefore = canvas.brushId
        val trace = app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleMethods = InstrumentationRegistry.getArguments().getString("colorMethodTrace") == "true"
        val allocatedBefore = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val ioBefore = java.io.File("/proc/self/io").readText()
        trace.reset()
        trace.enabled = true
        if (sampleMethods) android.os.Debug.startMethodTracingSampling(
            java.io.File(context.filesDir, "color-drag.trace").absolutePath, 16 * 1024 * 1024, 1000,
        )
        try {
            val ring = bounds("Hue ring")
            val down = SystemClock.uptimeMillis()
            fun point(index: Int): Offset {
                val angle = Math.toRadians((270 + index * 3).toDouble())
                return Offset(ring.exactCenterX() + kotlin.math.cos(angle).toFloat() * ring.width() * .43f,
                    ring.exactCenterY() + kotlin.math.sin(angle).toFloat() * ring.height() * .43f)
            }
            event(MotionEvent.ACTION_DOWN, point(0), down, MotionEvent.TOOL_TYPE_FINGER)
            repeat(30) { index ->
                SystemClock.sleep(16)
                event(MotionEvent.ACTION_MOVE, point(index + 1), down, MotionEvent.TOOL_TYPE_FINGER)
            }
            assertEquals("MOVE must not persist a workspace color", 0L, trace.snapshot().workspaceCommits)
            event(MotionEvent.ACTION_UP, point(30), down, MotionEvent.TOOL_TYPE_FINGER)
            compose.waitForIdle()
            val snapshot = trace.snapshot()
            assertEquals(1L, snapshot.workspaceCommits)
            assertTrue(snapshot.pointerSamples > 1)
            assertEquals(0L, snapshot.hueRingAllocations)
            assertEquals(brushBefore, canvas.brushId)
            val allocatedAfter = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
            android.util.Log.i("InkloomColorProfile", "snapshot=$snapshot allocatedBytes=${if (allocatedBefore != null && allocatedAfter != null) allocatedAfter - allocatedBefore else null}; ioBefore=$ioBefore; ioAfter=${java.io.File("/proc/self/io").readText()}; methodSampling=$sampleMethods")
        } finally {
            if (sampleMethods) android.os.Debug.stopMethodTracing()
            trace.enabled = false
            trace.reset()
        }
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
