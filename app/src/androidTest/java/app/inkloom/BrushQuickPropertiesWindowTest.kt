package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.brushpresetv5.BrushPresetV5Import
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class BrushQuickPropertiesWindowTest {
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
            context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().remove("studioTab").putBoolean("wheelHlsTriangle", true).commit()
            context.filesDir.resolve("library.json").delete()
            try { base.evaluate() }
            finally { context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE).edit().clear().commit() }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    private val context get() = instrumentation.targetContext
    private fun savedBrushes() = BrushMetadataStore(context).load()

    @Test fun factoryFlowDragCommitsOneCopyAndChangesTheNextActualStroke() {
        openPencil()
        val canvas = canvas()
        tap("Close brushes panel")
        tap("Colors")
        val ring = bounds("Hue ring")
        tapAt(ColorWheelTestCoordinates.hue(ring, 270f))
        val field = bounds("Saturation and brightness field")
        tapAt(ColorWheelTestCoordinates.triangle(field, .8f, .7f))
        closePopup()
        val opacity = bounds("Brush opacity")
        tapAt(Offset(opacity.exactCenterX(), opacity.top + opacity.height() * .4f))
        compose.waitForIdle()
        val masters = Triple(canvas.baseWidth, canvas.inkOpacity, canvas.inkColor)
        drawAt(.30f)
        val originalExecution = requireNotNull(canvas.lastBrushExecutionFingerprint)
        val beforeAlpha = pixels().sumOf { (it ushr 24).toLong() }
        tap("Brushes")
        assertEquals("Opening the library must retain live brush settings", masters,
            Triple(canvas.baseWidth, canvas.inkOpacity, canvas.inkColor))
        compose.onNodeWithContentDescription("Quick brush properties").fetchSemanticsNode().let { node ->
            tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        }
        compose.waitForIdle()
        val preferences = context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE)
        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "imported_brushes") writes++
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            dragFlow(cancel = true) { assertEquals(0, writes); assertTrue(savedBrushes().isEmpty()) }
            assertEquals(0, writes)
            assertEquals("system-hb-pencil", canvas.brushId)
            assertTrue(savedBrushes().isEmpty())
            dragFlow(cancel = false) { assertEquals(0, writes); assertTrue(savedBrushes().isEmpty()) }
            assertEquals("One completed gesture must save once", 1, writes)
        } finally { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        val saved = savedBrushes().single()
        val program = BrushPresetV5Import.requirePreset(saved)
        assertEquals(program.identity.renderBrushId, canvas.brushId)
        assertTrue(program.deposition.flow < .2f)
        assertEquals("system-hb-pencil", saved.derivedFromFactoryId)
        assertEquals(masters, Triple(canvas.baseWidth, canvas.inkOpacity, canvas.inkColor))
        closePopup()
        tap("Close brushes panel")
        drawAt(.43f)
        val edited = requireNotNull(canvas.lastBrushExecutionFingerprint)
        assertEquals(saved.id, edited.presetId)
        assertNotEquals(originalExecution.depositionHash, edited.depositionHash)
        assertEquals(originalExecution.renderSeed, edited.renderSeed)
        val addedAlpha = pixels().sumOf { (it ushr 24).toLong() } - beforeAlpha
        assertTrue("Low flow must deposit less pigment, before=$beforeAlpha after=$addedAlpha", addedAlpha > 0 && addedAlpha < beforeAlpha * .8)
        val beforeReload = pixels()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.waitUntil(5_000) { canvas().activeRasterSnapshotForTest()?.tileInfo?.isNotEmpty() == true }
        val afterReload = pixels()
        assertEquals(masters, Triple(canvas().baseWidth, canvas().inkOpacity, canvas().inkColor))
        assertTrue("Reopening must preserve exact committed paint pixels: ${beforeReload.indices.count { beforeReload[it] != afterReload[it] }} changed",
            beforeReload.contentEquals(afterReload))
        drawAt(.56f)
        assertEquals(saved.id, canvas().lastBrushExecutionFingerprint?.presetId)
        assertEquals(program.identity.version, canvas().lastBrushExecutionFingerprint?.presetRevision)
        assertEquals(saved.v5PresetJson, savedBrushes().single().v5PresetJson)
    }

    @Test fun userPressureEditKeepsItsIdAndChangesActualPressureWidth() {
        openPencil()
        compose.onNodeWithContentDescription("Quick brush properties").fetchSemanticsNode().let { node ->
            tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        }
        compose.waitForIdle()
        dragFlow(cancel = false) {}
        val user = savedBrushes().single()
        // Restore a substantial flow so the measured footprint is not near alpha quantization.
        val flow = bounds("Flow")
        val point = Offset(flow.left + flow.width() * .82f, flow.exactCenterY())
        tapAt(point)
        compose.waitForIdle()
        val beforeRevision = BrushPresetV5Import.requirePreset(savedBrushes().single()).identity.version
        closePopup()
        tap("Close brushes panel")
        val narrow = drawWidthAt(.25f, .15f)
        val wide = drawWidthAt(.38f, .9f)
        assertTrue("Enabled pressure must change footprint width: $narrow / $wide", wide > narrow + 1)
        tap("Brushes")
        compose.onNodeWithContentDescription("Quick brush properties").fetchSemanticsNode().let { node ->
            tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        }
        compose.waitForIdle()
        tap("Pressure controls size")
        val saved = savedBrushes().single()
        assertEquals(user.id, saved.id)
        val preset = BrushPresetV5Import.requirePreset(saved)
        assertEquals(beforeRevision + 1, preset.identity.version)
        assertTrue(!preset.dynamics.pressure.widthEnabled)
        closePopup()
        tap("Close brushes panel")
        val low = drawWidthAt(.51f, .15f)
        val high = drawWidthAt(.64f, .9f)
        assertTrue("Disabled pressure must keep footprint width: $low / $high", kotlin.math.abs(low - high) <= 2)
        assertEquals(saved.id, canvas().lastBrushExecutionFingerprint?.presetId)
        assertEquals(preset.identity.version, canvas().lastBrushExecutionFingerprint?.presetRevision)
    }

    @Test fun quickEntryCanOpenFactoryFullEditorAndCancelWithoutSaving() {
        openPencil()
        compose.onNodeWithContentDescription("Quick brush properties").fetchSemanticsNode().let { node ->
            tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        }
        compose.waitForIdle()
        tap("Open full brush editor")
        tap("Cancel")
        assertTrue(savedBrushes().isEmpty())
        assertEquals("system-hb-pencil", canvas().brushId)
    }

    @Test fun accessibilityCommitsOnceAndUserCancelAndNoOpDoNotSave() {
        openPencil()
        compose.onNodeWithContentDescription("Quick brush properties").fetchSemanticsNode().let { node ->
            tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        }
        compose.waitForIdle()
        val preferences = context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE)
        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "imported_brushes") writes++
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            setProgress("Flow", .5f)
            assertEquals(1, writes)
            val user = savedBrushes().single()
            assertEquals(.5f, BrushPresetV5Import.requirePreset(user).deposition.flow, .001f)
            setProgress("Flow", .5f)
            assertEquals("An unchanged accessible value must not save", 1, writes)
            dragFlow(cancel = true) { assertEquals(1, writes) }
            assertEquals(1, writes)
            assertEquals(user.v5PresetJson, savedBrushes().single().v5PresetJson)
            tap("Open full brush editor")
            tap("Cancel")
            assertEquals(user.v5PresetJson, savedBrushes().single().v5PresetJson)
            tap("Close brushes panel")
            drawAt(.35f)
            assertEquals(user.id, canvas().lastBrushExecutionFingerprint?.presetId)
        } finally { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun setProgress(label: String, value: Float) {
        var target: android.view.accessibility.AccessibilityNodeInfo? = null
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.contentDescription?.toString() == label) target = node
            else repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
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

    private fun dragFlow(cancel: Boolean, during: () -> Unit) {
        val rect = bounds("Flow")
        val start = Offset(rect.left + rect.width() * .68f, rect.exactCenterY())
        val end = Offset(rect.left + rect.width() * .08f, start.y)
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_FINGER)
        repeat(20) {
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * ((it + 1) / 20f), down, MotionEvent.TOOL_TYPE_FINGER)
        }
        during()
        event(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, end, down, MotionEvent.TOOL_TYPE_FINGER)
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
            visit(instrumentation.uiAutomation.rootInActiveWindow)
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

    private fun drawWidthAt(yFraction: Float, pressure: Float): Int {
        val before = canvas().activeRasterSnapshotForTest()?.let { pixels() }
        drawAt(yFraction, pressure)
        val snapshot = requireNotNull(canvas().activeRasterSnapshotForTest())
        val after = pixels()
        val rows = after.indices.filter { after[it] ushr 24 > 0 && after[it] != (before?.get(it) ?: 0) }.map { it / snapshot.logicalWidth }
        assertTrue("A real stroke must change pixels", rows.isNotEmpty())
        return rows.max() - rows.min() + 1
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
