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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
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
class LayerTargetWindowTest {
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
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("color-mixing-board.bin").delete()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun selectedLockedHiddenAndFolderTargetsNeverRedirectWindowPaint() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        val rejected = java.util.concurrent.CopyOnWriteArrayList<String>()
        instrumentation.runOnMainSync {
            val original = view.onPaintCapabilityRejected
            view.onPaintCapabilityRejected = { rejected += it; original(it) }
        }
        val a = pagePoint(view, .2f, .3f); val b = pagePoint(view, .5f, .3f)
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.any { it.rasterTileSurface != null } == true }
        tap("Layers"); tap("Add layer"); val target = view.activeLayerId
        assertNativeSelected("Layer 1")
        tap("Close layers panel"); stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == target }?.rasterTileSurface != null }
        fun assertRejectedPaint(reason: String) {
            val before = requireNotNull(model.currentPage())
            val count = rejected.size
            stroke(a, b)
            compose.waitUntil(5_000) { rejected.size > count }
            SystemClock.sleep(200); compose.waitForIdle()
            assertTrue(rejected.last(), rejected.last().contains(reason, ignoreCase = true))
            assertEquals(target, view.activeLayerId)
            assertEquals("Rejected paint cannot write another layer", before.layers, model.currentPage()!!.layers)
            assertEquals(before.elements, model.currentPage()!!.elements)
        }
        tap("Layers"); tap("Layer actions Layer 1"); tap("Lock")
        compose.waitUntil { model.currentPage()!!.layers.first { it.id == target }.locked }
        assertEquals(target, view.activeLayerId)
        tap("Close layers panel"); assertRejectedPaint("unlock")
        tap("Eraser"); assertRejectedPaint("unlock")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_B)
        compose.waitForIdle(); assertEquals(target, view.activeLayerId)
        tap("Layers"); tap("Layer actions Layer 1"); tap("Unlock")
        tapVisibility("Layer 1")
        compose.waitUntil { !model.currentPage()!!.layers.first { it.id == target }.visible }
        assertEquals(target, view.activeLayerId)
        tap("Close layers panel"); assertRejectedPaint("show")
        tap("Layers"); tapVisibility("Layer 1"); tap("Close layers panel")
        val before = pixels(view)
        stroke(pagePoint(view, .2f, .5f), pagePoint(view, .5f, .5f))
        compose.waitUntil(10_000) { !before.contentEquals(pixels(view)) }
        assertEquals(target, view.activeLayerId)
        tap("Layers"); tap("Add layer group"); tap("Select layer Folder 1")
        val folder = model.currentPage()!!.layers.first { it.name == "Folder 1" }.id
        compose.waitUntil { view.activeLayerId == folder }
        assertNativeSelected("Folder 1")
        tap("Toggle folder Folder 1"); assertEquals(folder, view.activeLayerId)
        tap("Toggle folder Folder 1"); assertEquals(folder, view.activeLayerId)
        val beforeFolder = model.currentPage()!!
        tap("Close layers panel"); val count = rejected.size; stroke(a, b)
        compose.waitUntil { rejected.size > count }
        assertTrue(rejected.last().contains("folder")); assertEquals(folder, view.activeLayerId)
        assertEquals(beforeFolder.layers, model.currentPage()!!.layers)
        val beforeScale = view.viewportScale
        pinch(view)
        compose.waitForIdle()
        assertTrue("An unwritable target must not block pinch navigation", view.viewportScale > beforeScale + .05f)
        tap("Layers"); tap("Layer content Layer 1")
        assertEquals(target, view.activeLayerId)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals("Recreation retains the explicit target", target, canvas().activeLayerId)
    }

    @Test fun drawingAssistCanBeToggledFromTheLayerMenuAndPersists() {
        tap("New Note"); tap("Create")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        val drawingLayerName = model.currentPage()!!.layers.first { it.name != "Background" }.name
        tap("Layers")
        tap("Layer actions $drawingLayerName")
        tap("Turn on Drawing Assist")
        compose.waitUntil { model.currentPage()!!.layers.first { it.name == drawingLayerName }.drawingAssist }
        tap("Layer actions $drawingLayerName")
        tap("Turn off Drawing Assist")
        compose.waitUntil { !model.currentPage()!!.layers.first { it.name == drawingLayerName }.drawingAssist }
    }

    @Test fun adjustmentCreationPreviewCancelApplyAndHistoryUseTheWindowControls() {
        tap("New Note"); tap("Create")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        tap("Layers"); tap("Add adjustment layer"); tap("Brightness / Contrast")
        compose.waitUntil(10_000) { model.currentPage()?.layers?.any { it.role == app.inkloom.document.LayerRole.ADJUSTMENT } == true }
        val id = model.currentPage()!!.layers.single { it.role == app.inkloom.document.LayerRole.ADJUSTMENT }.id
        compose.onNodeWithContentDescription("Brightness adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        tap("Cancel")
        assertEquals(0f, model.currentPage()!!.layers.single { it.id == id }.adjustment!!.brightness)
        tap("Edit adjustment Adjustment 1")
        compose.onNodeWithContentDescription("Brightness adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        tap("Apply")
        compose.waitUntil(10_000) {
            model.currentPage()!!.layers.single { it.id == id }.adjustment!!.brightness > .25f &&
                !canvas().selectionLayerTransferPending
        }
        tap("Undo")
        compose.waitUntil(10_000) { model.currentPage()!!.layers.single { it.id == id }.adjustment!!.brightness == 0f }
        tap("Layer actions Adjustment 1"); tap("Mask"); tap("Mask selection")
        compose.waitUntil(10_000) { model.currentPage()!!.layers.single { it.id == id }.mask?.surface != null }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { model.currentPage()?.layers?.singleOrNull { it.id == id }?.mask?.surface != null }
    }

    @Test fun adjustmentLayersCanClipToTheLayerBelowFromWindowControls() {
        tap("New Note"); tap("Create")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        tap("Layers"); tap("Add adjustment layer"); tap("Brightness / Contrast")
        compose.waitUntil(10_000) { model.currentPage()?.layers?.any { it.role == app.inkloom.document.LayerRole.ADJUSTMENT } == true }
        tap("Cancel")
        val id = model.currentPage()!!.layers.single { it.role == app.inkloom.document.LayerRole.ADJUSTMENT }.id
        fun clipped() = model.currentPage()!!.layers.single { it.id == id }.clippedToBelow
        tap("Toggle clipping")
        compose.waitUntil(5_000) { clipped() && id in canvas().clippedLayers }
        fun history(redo: Boolean = false) {
            val key = if (redo) android.view.KeyEvent.KEYCODE_Y else android.view.KeyEvent.KEYCODE_Z
            val time = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, key, 0, android.view.KeyEvent.META_CTRL_ON))
            instrumentation.sendKeySync(android.view.KeyEvent(time, time + 1, android.view.KeyEvent.ACTION_UP, key, 0, android.view.KeyEvent.META_CTRL_ON))
            compose.waitForIdle()
        }
        history(); compose.waitUntil(5_000) { !clipped() && id !in canvas().clippedLayers }
        assertTrue("undoing clipping keeps the adjustment layer", model.currentPage()!!.layers.any { it.id == id })
        history(true); compose.waitUntil(5_000) { clipped() && id in canvas().clippedLayers }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.waitUntil(10_000) { model.currentPage()?.layers?.singleOrNull { it.id == id }?.clippedToBelow == true }
        compose.waitUntil(5_000) { id in canvas().clippedLayers }
        if (compose.onAllNodesWithContentDescription("Layer actions Adjustment 1").fetchSemanticsNodes().isEmpty()) tap("Layers")
        tap("Layer actions Adjustment 1"); tap("Stop clipping")
        compose.waitUntil(5_000) { !clipped() && id !in canvas().clippedLayers }
    }

    @Test fun creationFollowsSelectedSiblingOrFolderAndSharesPaintHistory() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        fun layers() = model.currentPage()!!.layers
        fun id(name: String) = layers().single { it.name == name }.id
        fun history(redo: Boolean = false) {
            val key = if (redo) android.view.KeyEvent.KEYCODE_Y else android.view.KeyEvent.KEYCODE_Z
            val time = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, key, 0, android.view.KeyEvent.META_CTRL_ON))
            instrumentation.sendKeySync(android.view.KeyEvent(time, time + 1, android.view.KeyEvent.ACTION_UP, key, 0, android.view.KeyEvent.META_CTRL_ON))
            compose.waitForIdle()
        }
        tap("Layers"); tap("Add layer"); assertNativeSelected("Layer 1")
        tap("Add layer"); assertNativeSelected("Layer 2")
        tap("Select layer Notes"); tap("Add layer"); assertNativeSelected("Layer 3")
        assertEquals(listOf("Layer 2", "Layer 1", "Layer 3", "Notes", "Background"),
            app.inkloom.document.LayerHierarchy.panelOrder(layers()).map { it.name })
        tap("Add layer group"); assertNativeSelected("Folder 1")
        val folderId = id("Folder 1")
        tap("Toggle folder Folder 1"); tap("Add layer"); assertNativeSelected("Layer 4")
        val childId = id("Layer 4")
        assertEquals(folderId, layers().single { it.id == childId }.parentId)
        assertTrue(!layers().single { it.id == folderId }.collapsed)
        history()
        compose.waitUntil { layers().none { it.id == childId } }
        assertNativeSelected("Folder 1")
        assertTrue(layers().single { it.id == folderId }.collapsed)
        history(true); assertNativeSelected("Layer 4")
        assertEquals(childId, view.activeLayerId)
        tap("Close layers panel")
        stroke(pagePoint(view, .25f, .5f), pagePoint(view, .6f, .5f))
        compose.waitUntil(10_000) { layers().single { it.id == childId }.rasterTileSurface != null }
        val painted = pixels(view)
        history() // Ink precedes creation in reverse chronological order.
        compose.waitForIdle(); SystemClock.sleep(300)
        assertTrue(layers().any { it.id == childId })
        assertTrue("Undo paint clears its surface", pixels(view).all { it == 0 })
        history()
        compose.waitUntil(5_000) { layers().none { it.id == childId } }
        assertEquals(folderId, view.activeLayerId)
        history(true)
        compose.waitUntil { layers().any { it.id == childId } }
        assertEquals(childId, view.activeLayerId)
        history(true)
        compose.waitUntil(10_000) { layers().single { it.id == childId }.rasterTileSurface != null }
        compose.waitUntil(10_000) { painted.contentEquals(pixels(view)) }
        tap("Layers"); assertNativeSelected("Layer 4")
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(childId, canvas().activeLayerId)
        assertEquals(folderId, model.currentPage()!!.layers.single { it.id == childId }.parentId)
        // Create while the parent is hidden, then reveal it before drawing. The history surface
        // must already be retained even though a visibility-filtered reconciliation would omit it.
        tap("Select layer Folder 1"); tapVisibility("Folder 1"); tap("Add layer")
        assertNativeSelected("Layer 5"); val hiddenChild = id("Layer 5")
        tapVisibility("Folder 1"); tap("Close layers panel")
        val recreatedView = canvas()
        stroke(pagePoint(recreatedView, .25f, .7f), pagePoint(recreatedView, .6f, .7f))
        compose.waitUntil(10_000) { layers().single { it.id == hiddenChild }.rasterTileSurface != null }
        val hiddenPainted = pixels(recreatedView)
        history(); compose.waitForIdle(); history()
        compose.waitUntil(5_000) { layers().none { it.id == hiddenChild } }
        history(true); compose.waitUntil { layers().any { it.id == hiddenChild } }
        history(true); compose.waitUntil(10_000) { hiddenPainted.contentEquals(pixels(recreatedView)) }
    }

    private fun assertNativeSelected(name: String) {
        compose.onNodeWithContentDescription("Select layer $name").assertIsSelected()
        compose.waitUntil(5_000) {
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.contentDescription?.toString() == "Select layer $name") {
                    var row = node
                    while (row != null && !row.isCheckable) row = row.parent
                    // AndroidX maps non-tab Selected to checked plus a selected state description.
                    return row?.let { it.isChecked && it.isClickable && !it.stateDescription.isNullOrBlank() } == true
                }
                return (0 until node.childCount).any { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
        }
    }

    private fun tapVisibility(name: String) {
        val node = compose.onNode(hasContentDescription("Toggle visibility; long press to solo") and
            hasAnyAncestor(hasContentDescription("Select layer $name")), useUnmergedTree = true).fetchSemanticsNode()
        tapAt(node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow)
        compose.waitForIdle()
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
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
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
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
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
