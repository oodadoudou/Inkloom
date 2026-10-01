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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
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
class LayerOrderWindowTest {
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


    private fun model(): app.inkloom.ui.InkloomViewModel {
        lateinit var result: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { result = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        return result
    }
    private fun history(redo: Boolean = false) {
        val key = if (redo) android.view.KeyEvent.KEYCODE_Y else android.view.KeyEvent.KEYCODE_Z
        val time = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, key, 0, android.view.KeyEvent.META_CTRL_ON))
        instrumentation.sendKeySync(android.view.KeyEvent(time, time + 1, android.view.KeyEvent.ACTION_UP, key, 0, android.view.KeyEvent.META_CTRL_ON))
        compose.waitForIdle()
    }
    private fun secondary(name: String) {
        val rect = bounds("Reorder layer $name")
        val from = Offset(rect.left + 3f, rect.exactCenterY())
        val to = Offset(rect.right - 3f, rect.exactCenterY())
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, from, time, MotionEvent.TOOL_TYPE_FINGER)
        repeat(12) { index -> SystemClock.sleep(20); event(MotionEvent.ACTION_MOVE, from + (to - from) * ((index + 1) / 12f), time, MotionEvent.TOOL_TYPE_FINGER) }
        event(MotionEvent.ACTION_UP, to, time, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
    }
    private fun drag(source: String, destination: String, fraction: Float = .5f, cancel: Boolean = false, dwell: Long = 120) {
        val from = center("Reorder layer $source")
        val rect = bounds("Select layer $destination")
        val to = Offset(rect.exactCenterX(), rect.top + rect.height() * fraction)
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, from, time, MotionEvent.TOOL_TYPE_FINGER)
        // Native events still cross the window; the Compose test clock owns long-press timers.
        compose.mainClock.advanceTimeBy(800)
        SystemClock.sleep(80)
        repeat(10) { index -> SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, from + (to - from) * ((index + 1) / 10f), time, MotionEvent.TOOL_TYPE_FINGER) }
        SystemClock.sleep(dwell)
        compose.mainClock.advanceTimeBy(dwell)
        event(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, to, time, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
    }

    @Test fun folderReferenceAndDraftButtonsPersistAndChildrenInheritWithoutOwnFlags() {
        tap("New Note"); tap("Create"); canvas(); tap("Layers"); tap("Add layer group")
        tap("Toggle reference"); tap("Toggle draft"); tap("Add layer")
        fun page() = requireNotNull(model().currentPage())
        compose.waitUntil(5_000) {
            page().layers.single { it.name == "Folder 1" }.let { it.reference && it.draft }
        }
        val child = page().layers.single { it.name == "Layer 1" }
        assertTrue(app.inkloom.document.LayerHierarchy.effectiveReference(page().layers, child))
        assertTrue(app.inkloom.document.LayerHierarchy.effectiveDraft(page().layers, child))
        org.junit.Assert.assertFalse(child.reference); org.junit.Assert.assertFalse(child.draft)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val reopened = page().layers.single { it.id == child.id }
        assertTrue(app.inkloom.document.LayerHierarchy.effectiveDraft(page().layers, reopened))
        tap("Select layer Folder 1"); tap("Toggle reference"); tap("Toggle draft")
        compose.waitUntil(5_000) { page().layers.single { it.name == "Folder 1" }.let { !it.reference && !it.draft } }
        org.junit.Assert.assertFalse(app.inkloom.document.LayerHierarchy.effectiveReference(page().layers, reopened))
        org.junit.Assert.assertFalse(app.inkloom.document.LayerHierarchy.effectiveDraft(page().layers, reopened))
    }

    @Test fun selectedLayersBecomeOneGroupAndOneUndoRestoresTheirParents() {
        tap("New Note"); tap("Create"); canvas(); val model = model()
        tap("Layers"); tap("Add layer"); tap("Add layer")
        fun layers() = requireNotNull(model.currentPage()).layers
        val before = layers().map { it.id to it.parentId }
        secondary("Layer 1")
        tap("Group selected layers")
        compose.waitUntil(10_000) {
            val folder = layers().firstOrNull { it.name == "Folder 1" }
            folder != null && layers().filter { it.name in setOf("Layer 1", "Layer 2") }.all { it.parentId == folder.id }
        }
        val folderId = layers().single { it.name == "Folder 1" }.id
        history()
        compose.waitUntil(10_000) { layers().none { it.id == folderId } }
        assertEquals(before, layers().map { it.id to it.parentId })
    }

    @Test fun nativeMultiLayerDropPreservesOrderPixelsSelectionAndOneHistoryEntry() {
        tap("New Note"); tap("Create"); val view = canvas(); val model = model()
        tap("Layers"); tap("Add layer group"); tap("Select layer Notes")
        repeat(3) { tap("Add layer") }
        fun layers() = model.currentPage()!!.layers
        val primary = layers().single { it.name == "Layer 3" }.id
        val other = layers().single { it.name == "Layer 1" }.id
        val group = layers().single { it.name == "Folder 1" }.id
        tap("Close layers panel")
        stroke(pagePoint(view, .25f, .4f), pagePoint(view, .6f, .4f))
        compose.waitUntil(10_000) { layers().single { it.id == primary }.rasterTileSurface != null }
        val painted = pixels(view)
        tap("Layers"); secondary("Layer 1")
        compose.onNodeWithContentDescription("Exit layer selection").assertExists()
        tap("Close layers panel"); tap("Layers")
        compose.onNodeWithContentDescription("Exit layer selection").assertExists()
        val before = layers().map { it.id to it.parentId }
        drag("Layer 1", "Folder 1")
        compose.waitUntil(5_000) { layers().filter { it.id in setOf(primary, other) }.all { it.parentId == group } }
        assertNativeSelected("Layer 3")
        assertEquals(listOf("Folder 1", "Layer 3", "Layer 1", "Layer 2", "Notes", "Background"),
            app.inkloom.document.LayerHierarchy.panelOrder(layers()).map { it.name })
        assertTrue(painted.contentEquals(pixels(view)))
        val moved = layers().map { it.id to it.parentId }
        history(); compose.waitUntil { layers().map { it.id to it.parentId } == before }
        assertTrue("Structural undo preserves the previous stroke", painted.contentEquals(pixels(view)))
        history(); compose.waitUntil { pixels(view).all { it == 0 } }
        history(true); compose.waitUntil { painted.contentEquals(pixels(view)) }
        history(true); compose.waitUntil { layers().map { it.id to it.parentId } == moved }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(moved, model.currentPage()!!.layers.map { it.id to it.parentId })
    }

    @Test fun nativeHoverExpansionCancelsWithoutChangingFolderOrHistoryAndEdgesMoveOut() {
        tap("New Note"); tap("Create"); canvas(); val model = model()
        tap("Layers"); tap("Add layer group"); tap("Add layer")
        tap("Toggle folder Folder 1"); tap("Select layer Notes"); tap("Add layer")
        val before = model.currentPage()!!.layers
        drag("Layer 2", "Folder 1", cancel = true, dwell = 850)
        assertEquals("Cancel restores temporary expansion and never changes hierarchy", before, model.currentPage()!!.layers)
        compose.onNodeWithContentDescription("Select layer Layer 1").assertDoesNotExist()
        history(); compose.waitUntil { model.currentPage()!!.layers.none { it.name == "Layer 2" } }
        history(true); compose.waitUntil { model.currentPage()!!.layers.any { it.name == "Layer 2" } }
        drag("Layer 2", "Folder 1", dwell = 850)
        val group = model.currentPage()!!.layers.single { it.name == "Folder 1" }.id
        compose.waitUntil { model.currentPage()!!.layers.single { it.name == "Layer 2" }.parentId == group }
        compose.onNodeWithContentDescription("Select layer Layer 1").assertExists()
        drag("Layer 2", "Folder 1", fraction = .03f)
        compose.waitUntil { model.currentPage()!!.layers.single { it.name == "Layer 2" }.parentId == null }
        assertEquals("Layer 2", app.inkloom.document.LayerHierarchy.panelOrder(model.currentPage()!!.layers).first().name)
        history(); compose.waitUntil { model.currentPage()!!.layers.single { it.name == "Layer 2" }.parentId == group }
    }

    @Test fun nativeAncestorAndDescendantSelectionMovesTheMaskedTreeOnlyOnce() {
        tap("New Note"); tap("Create"); canvas(); val model = model(); tap("Layers")
        tap("Add layer group"); tap("Add layer group"); tap("Add layer")
        tap("Select layer Notes"); tap("Add layer group")
        tap("Select layer Folder 1"); tap("Active layer mask"); tap("Mask selection")
        compose.waitUntil(5_000) { model.currentPage()!!.layers.single { it.name == "Folder 1" }.mask != null }
        val before = model.currentPage()!!.layers
        val root = before.single { it.name == "Folder 1" }; val nested = before.single { it.name == "Folder 2" }
        val destination = before.single { it.name == "Folder 3" }
        tap("Secondary selection Folder 2")
        assertNativeSelected("Folder 1")
        drag("Folder 2", "Folder 3")
        compose.waitUntil(5_000) { model.currentPage()!!.layers.single { it.id == root.id }.parentId == destination.id }
        val after = model.currentPage()!!.layers
        assertEquals(before.size, after.size)
        assertEquals(root.id, after.single { it.id == nested.id }.parentId)
        assertEquals(nested.id, after.single { it.name == "Layer 1" }.parentId)
        assertEquals(root.mask, after.single { it.id == root.id }.mask)
        assertNativeSelected("Folder 1")
        history(); compose.waitUntil { model.currentPage()!!.layers == before }
        history(true); compose.waitUntil { model.currentPage()!!.layers == after }
    }

    @Test fun nativeEdgeScrollKeepsContactAfterTheSourceRowIsRecycled() {
        tap("New Note"); tap("Create"); canvas(); val model = model(); tap("Layers")
        repeat(14) { index -> tap("Add layer"); compose.waitUntil(5_000) { model.currentPage()!!.layers.any { it.name == "Layer ${index + 1}" } } }
        val before = model.currentPage()!!.layers.map { it.id to it.parentId }
        compose.onNodeWithContentDescription("Layer list").performScrollToIndex(0)
        val from = center("Reorder layer Layer 14")
        val list = bounds("Layer list")
        val edge = Offset(list.exactCenterX(), list.bottom - 8f)
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, from, time, MotionEvent.TOOL_TYPE_FINGER)
        // Native events still cross the window; the Compose test clock owns long-press timers.
        compose.mainClock.advanceTimeBy(800)
        SystemClock.sleep(80)
        repeat(12) { index -> SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, from + (edge - from) * ((index + 1) / 12f), time, MotionEvent.TOOL_TYPE_FINGER) }
        SystemClock.sleep(2_500)
        compose.mainClock.advanceTimeBy(2_500)
        val destination = bounds("Select layer Layer 1")
        val to = Offset(destination.exactCenterX(), destination.top + 3f)
        event(MotionEvent.ACTION_MOVE, to, time, MotionEvent.TOOL_TYPE_FINGER)
        SystemClock.sleep(70)
        event(MotionEvent.ACTION_UP, to, time, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        compose.waitUntil { model.currentPage()!!.layers.map { it.id to it.parentId } != before }
        val ordered = app.inkloom.document.LayerHierarchy.panelOrder(model.currentPage()!!.layers).map { it.name }
        assertTrue(ordered.toString(), ordered.indexOf("Layer 14") > ordered.indexOf("Layer 2"))
        assertEquals(ordered.indexOf("Layer 1") - 1, ordered.indexOf("Layer 14"))
        history(); compose.waitUntil { model.currentPage()!!.layers.map { it.id to it.parentId } == before }
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

    private fun pixels(view: InkCanvasView): IntArray {
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        return snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
    }
    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val point = floatArrayOf(x * view.width, y * view.height)
        instrumentation.runOnMainSync { android.graphics.Matrix().also { view.transformMatrixToGlobal(it); it.mapPoints(point) } }
        return Offset(point[0], point[1])
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
