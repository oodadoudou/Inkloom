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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import app.inkloom.importer.BrushMetadataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Sliders and strokes cross the window dispatcher; the disposal test invokes the actual row action. */
class BrushMasterSettingsWindowTest {
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
            context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            try { base.evaluate() }
            finally { context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE).edit().clear().commit() }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    private val context get() = instrumentation.targetContext
    private fun savedBrushes() = BrushMetadataStore(context).load()

    @Test fun switchingBrushesAndReopeningRestoresIndependentMastersAndActualPaint() {
        openPencil()
        tap("Close brushes panel")
        setMasters(24f, .36f)
        val first = Triple(canvas().baseWidth, canvas().inkOpacity, canvas().inkColor)
        drawAt(.27f)
        val firstFingerprint = requireNotNull(canvas().lastBrushExecutionFingerprint)
        val firstAlpha = pixels().sumOf { (it ushr 24).toLong() }
        tap("Brushes")
        tap("Brush preset H Pencil")
        tap("Close brushes panel")
        setMasters(47f, .81f)
        tap("Brushes")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        assertEquals(first, Triple(canvas().baseWidth, canvas().inkOpacity, canvas().inkColor))
        drawAt(.42f)
        val secondAlpha = pixels().sumOf { (it ushr 24).toLong() } - firstAlpha
        assertTrue("Restored brush must deposit comparable pigment: $firstAlpha / $secondAlpha",
            secondAlpha in (firstAlpha * .85).toLong()..(firstAlpha * 1.15).toLong())
        assertEquals(firstFingerprint.depositionHash, canvas().lastBrushExecutionFingerprint?.depositionHash)
        val beforeReload = pixels()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.waitUntil(5_000) { canvas().activeRasterSnapshotForTest()?.tileInfo?.isNotEmpty() == true }
        assertEquals(first, Triple(canvas().baseWidth, canvas().inkOpacity, canvas().inkColor))
        assertTrue(beforeReload.contentEquals(pixels()))
        drawAt(.57f)
        assertEquals(firstFingerprint.depositionHash, canvas().lastBrushExecutionFingerprint?.depositionHash)
        tap("Brushes")
        tap("Brush preset H Pencil")
        assertEquals(47f, canvas().baseWidth, .001f)
        assertEquals(.81f, canvas().inkOpacity, .001f)
        assertTrue("Master controls must not create user presets", savedBrushes().all { it.id.startsWith("system-") })
    }

    @Test fun windowDragsCommitOnceCancelNothingAndAccessibleNoOpDoesNotWrite() {
        openPencil()
        tap("Close brushes panel")
        val preferences = context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE)
        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> writes++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            for (label in listOf("Brush size", "Brush opacity")) {
                val before = canvas().baseWidth to canvas().inkOpacity
                val beforeWrites = writes
                dragMaster(label, cancel = true) { assertEquals(beforeWrites, writes) }
                assertEquals(before, canvas().baseWidth to canvas().inkOpacity)
                assertEquals(beforeWrites, writes)
                dragMaster(label, cancel = false) { assertEquals(beforeWrites, writes) }
                assertEquals("One completed drag must write one control", beforeWrites + 1, writes)
            }
            setProgress("Brush opacity", .5f)
            val count = writes
            setProgress("Brush opacity", .5f)
            assertEquals(count, writes)
            assertEquals(.525f, canvas().inkOpacity, .001f)
            compose.onNodeWithContentDescription("Brush opacity").performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitForIdle()
            assertEquals(.5155f, canvas().inkOpacity, .001f)
            assertEquals("One keyboard step saves once", count + 1, writes)
            drawAt(.4f)
            assertEquals("system-hb-pencil", canvas().lastBrushExecutionFingerprint?.presetId)
        } finally { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Test fun sizeDragShowsPixelReadoutOutsideTheNarrowDockWithoutCommittingEarly() {
        openPencil()
        tap("Close brushes panel")
        val before = canvas().baseWidth
        val slider = bounds("Brush size")
        dragMaster("Brush size", cancel = true) {
            val labels = compose.onAllNodesWithText(" px", substring = true).fetchSemanticsNodes()
            assertEquals("Visible numeric size with units", 1, labels.size)
            val label = labels.single()
            assertTrue(label.boundsInWindow.width > 30f)
            val automation = instrumentation.uiAutomation
            val info = automation.serviceInfo
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            var screenBounds: android.graphics.Rect? = null
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.text?.toString()?.endsWith(" px") == true) screenBounds = android.graphics.Rect().also(node::getBoundsInScreen)
                repeat(node.childCount) { visit(node.getChild(it)) }
            }
            compose.waitUntil(3_000) {
                automation.clearCache()
                automation.windows.forEach { visit(it.root) }
                screenBounds != null
            }
            assertTrue("Readout must escape the dock clip: $screenBounds vs $slider", requireNotNull(screenBounds).left >= slider.right)
            assertEquals(before, canvas().baseWidth, .001f)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /data/local/tmp/inkloom-173-size-readout.png")).use { it.readBytes() }
        }
        assertTrue(compose.onAllNodesWithText(" px", substring = true).fetchSemanticsNodes().isEmpty())
        assertEquals(before, canvas().baseWidth, .001f)
    }

    @Test fun eraserMastersSurviveSwitchingWhilePaintMastersStayIndependent() {
        openPencil()
        tap("Close brushes panel")
        setMasters(33f, .62f)
        drawAt(.38f)
        val beforeErase = pixels().sumOf { (it ushr 24).toLong() }
        tap("Eraser")
        val eraserId = canvas().brushId
        tap("Close brushes panel")
        setMasters(51f, .43f)
        drawAt(.38f)
        assertTrue("The selected eraser must remove actual paint", pixels().sumOf { (it ushr 24).toLong() } < beforeErase)
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        assertEquals(33f, canvas().baseWidth, .001f)
        assertEquals(.62f, canvas().inkOpacity, .001f)
        tap("Eraser")
        assertEquals(eraserId, canvas().brushId)
        assertEquals(51f, canvas().baseWidth, .001f)
        assertEquals(.43f, canvas().inkOpacity, .001f)
        tap("Close brushes panel")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(eraserId, canvas().brushId)
        assertEquals(51f, canvas().baseWidth, .001f)
        assertEquals(.43f, canvas().inkOpacity, .001f)
    }

    @Test fun changingTheBrushDuringASliderDraftDiscardsItAndUsesTheNewTarget() {
        openPencil()
        val preferences = context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE)
        val rect = bounds("Brush opacity")
        val start = Offset(rect.exactCenterX(), rect.top + rect.height() * .7f)
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_MOVE, start.copy(y = start.y + 12), down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        // Invoke the actual row's Compose click action while raw window touch stays down.
        compose.onNodeWithContentDescription("Brush preset H Pencil").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.waitForIdle()
        val nextId = requireNotNull(canvas().brushId)
        assertEquals("system-h-pencil", nextId)
        val newOpacity = canvas().inkOpacity
        event(MotionEvent.ACTION_UP, start.copy(y = start.y + 12), down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        assertTrue("Disposed draft must not save to either brush", preferences.all.isEmpty())
        assertEquals(newOpacity, canvas().inkOpacity)
        tap("Close brushes panel")
        setMasters(17f, .29f)
        drawAt(.35f)
        assertEquals(nextId, canvas().lastBrushExecutionFingerprint?.presetId)
        tap("Brushes")
        tap("Brush preset HB Pencil")
        assertTrue("The original brush must retain its default opacity", canvas().inkOpacity != .29f)
        assertTrue(preferences.all.keys.all { it.endsWith(":$nextId") })
    }

    private fun setMasters(size: Float, opacity: Float) {
        setProgress("Brush size", kotlin.math.sqrt((size - .5f) / 399.5f))
        setProgress("Brush opacity", (opacity - .05f) / .95f)
    }

    private fun dragMaster(label: String, cancel: Boolean, during: () -> Unit) {
        val rect = bounds(label)
        val start = Offset(rect.exactCenterX(), rect.top + rect.height() * .3f)
        val end = Offset(start.x, rect.top + rect.height() * .8f)
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_FINGER)
        repeat(20) { index ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * ((index + 1) / 20f), down, MotionEvent.TOOL_TYPE_FINGER)
        }
        compose.waitForIdle()
        during()
        event(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, end, down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
    }

    private fun windowRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        // Reused Compose lazy-item nodes can remain in UiAutomation's cache after a category swap.
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }

    private fun setProgress(label: String, value: Float) {
        var target: android.view.accessibility.AccessibilityNodeInfo? = null
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.contentDescription?.toString() == label) target = node
            else repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(windowRoot())
        val node = requireNotNull(target)
        assertTrue(node.actionList.contains(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS))
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            android.os.Bundle().apply {
                putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value)
            })
        compose.waitForIdle()
    }

    private fun openPencil() {
        tap("New Note")
        tap("Create")
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
    }

    private fun closePopup() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun bounds(label: String): android.graphics.Rect {
        var result: android.graphics.Rect? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.contentDescription?.toString() == label) result = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(windowRoot())
            result != null
        }
        return requireNotNull(result)
    }

    private fun drawAt(yFraction: Float, pressure: Float = .7f) {
        val canvas = canvas()
        val previous = canvas.lastBrushExecutionFingerprint?.strokeId
        val location = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(location) }
        val start = Offset(location[0] + canvas.width * .20f, location[1] + canvas.height * yFraction)
        stroke(start, Offset(location[0] + canvas.width * .45f, start.y), pressure)
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint?.strokeId?.let { it != previous } == true }
    }

    private fun pixels(): IntArray {
        val snapshot = requireNotNull(canvas().activeRasterSnapshotForTest())
        return snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
    }

    private fun tap(node: String) {
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
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
            visit(windowRoot())
            result != null
        }
        return requireNotNull(result)
    }

    private fun tapAt(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun stroke(start: Offset, end: Offset, pressure: Float = .7f) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, MotionEvent.TOOL_TYPE_STYLUS, pressure)
        repeat(24) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 24f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, MotionEvent.TOOL_TYPE_STYLUS, pressure)
        }
        event(MotionEvent.ACTION_UP, end, time, MotionEvent.TOOL_TYPE_STYLUS, pressure)
    }

    private fun event(action: Int, point: Offset, down: Long, type: Int, pressureValue: Float = .7f) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else pressureValue; size = .01f }),
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
