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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
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
class LayerGroupMaskWindowTest {
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
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun propertiesAndCommandPlacementPersistWithoutLosingTheLayerList() {
        tap("New Note"); tap("Create"); tap("Layers")
        tap("Layer list display"); tap("Show properties bar"); tap("Show command bar")
        tap("Commands at bottom"); compose.onNodeWithContentDescription("Layer list display").performClick()
        compose.onNodeWithContentDescription("Layer properties").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add layer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Layer list").assertExists()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val settings = app.inkloom.settings.AppSettingsStore(instrumentation.targetContext).load()
        assertTrue(!settings.layerShowProperties && !settings.layerShowCommands && settings.layerCommandsAtBottom)
        tap("Layer list display"); tap("Show properties bar"); tap("Show command bar")
        compose.onNodeWithContentDescription("Layer list display").performClick()
        compose.onNodeWithContentDescription("Layer properties").assertExists()
        compose.onNodeWithContentDescription("Add layer").assertExists()
    }

    @Test fun dualLayerListsAndDisplayChoicesPersistFromTheRealPanel() {
        tap("New Note"); tap("Create"); tap("Layers")
        val store = app.inkloom.settings.AppSettingsStore(instrumentation.targetContext)

        tap("Layer list display"); tap("Two layer lists"); tap("Compact rows")
        compose.waitUntil(10_000) {
            store.load().layerPanelDualList &&
                store.load().layerRowDensity == app.inkloom.settings.LayerRowDensity.COMPACT
        }
        compose.onNodeWithContentDescription("Second layer list").assertExists()
        val splitBefore = store.load().layerPanelSplitFraction
        val divider = center("Resize layer lists")
        dragFinger(divider, divider + Offset(0f, 72f))
        compose.waitUntil(10_000) { store.load().layerPanelSplitFraction > splitBefore + .03f }

        tap("Layer list display"); tap("Show thumbnails")
        compose.waitUntil(10_000) { !store.load().layerShowThumbnails }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertTrue(store.load().layerPanelDualList)
        assertEquals(app.inkloom.settings.LayerRowDensity.COMPACT, store.load().layerRowDensity)
        assertTrue(!store.load().layerShowThumbnails)
    }

    @Test fun paletteColorMarkerUsesTheLayerMenuAndOneHistoryEntry() {
        tap("New Note"); tap("Create")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync {
            model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]
        }
        fun color() = requireNotNull(model.currentPage()).layers.first { it.name == "Layer 1" }.paletteColor

        tap("Layers")
        tap("Add layer")
        tap("Layer actions Layer 1")
        compose.onNodeWithText("Palette color").performScrollTo().performClick()
        tap("Red")
        compose.waitUntil(10_000) { color() == app.inkloom.document.LayerPaletteColor.RED }
        tap("Undo")
        compose.waitUntil(10_000) { color() == app.inkloom.document.LayerPaletteColor.NONE }
        tap("Redo")
        compose.waitUntil(10_000) { color() == app.inkloom.document.LayerPaletteColor.RED }
    }

    @Test fun maskExpressionDialogCommitsOnceAndUndoRestoresSoftGradients() {
        tap("New Note"); tap("Create")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync {
            model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]
        }
        fun mask() = requireNotNull(model.currentPage()).layers.first { it.name == "Layer 1" }.mask
        tap("Layers"); tap("Add layer")
        tap("Layer actions Layer 1"); tap("Mask"); tap("Mask selection")
        compose.waitUntil(10_000) { mask() != null }
        tap("Layer actions Layer 1"); tap("Mask")
        compose.onNodeWithText("Mask gradients and threshold").performScrollTo().performClick()
        tap("Show mask gradients switch")
        tap("Apply")
        compose.waitUntil(10_000) { mask()?.showGradients == false }
        assertEquals(128, mask()?.expressionThreshold)
        tap("Undo")
        compose.waitUntil(10_000) { mask()?.showGradients == true }
        tap("Redo")
        compose.waitUntil(10_000) { mask()?.showGradients == false }
    }

    @Test fun groupCopyDeleteUngroupAndUndoKeepNestedPaintOnTheRealWindow() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        fun layers() = requireNotNull(model.currentPage()).layers
        tap("Layers"); tap("Add layer group"); val originalGroupId = view.activeLayerId
        tap("Add layer"); val childId = view.activeLayerId
        tap("Close layers panel")
        stroke(pagePoint(view, .2f, .35f), pagePoint(view, .65f, .35f))
        compose.waitUntil(10_000) { layers().first { it.id == childId }.rasterTileSurface != null }
        val painted = pixels(view)
        tap("Layers"); tap("Layer actions Folder 1"); tap("Duplicate")
        compose.waitUntil(10_000) { layers().any { it.name == "Folder 1 copy" } }
        val copy = layers().single { it.name == "Folder 1 copy" }
        val copiedChild = layers().single { it.parentId == copy.id }
        assertTrue(copiedChild.id != childId)
        assertEquals(layers().single { it.id == childId }.rasterTileSurface, copiedChild.rasterTileSurface)
        tap("Undo"); compose.waitUntil(10_000) { layers().none { it.id == copy.id } }
        tap("Redo"); compose.waitUntil(10_000) { layers().any { it.id == copy.id } }
        tap("Layer actions Folder 1 copy"); tap("Delete")
        compose.waitUntil(10_000) { layers().none { it.id == copy.id || it.id == copiedChild.id } }
        tap("Undo"); compose.waitUntil(10_000) { layers().any { it.id == copiedChild.id } }
        tap("Layer actions Folder 1"); tap("Ungroup layer folder")
        compose.waitUntil(10_000) { layers().none { it.id == originalGroupId } }
        assertEquals(null, layers().single { it.id == childId }.parentId)
        tap("Undo"); compose.waitUntil(10_000) { layers().single { it.id == childId }.parentId == originalGroupId }
        tap("Select layer Layer 1")
        assertTrue("One structural history entry must preserve the prior stroke", painted.contentEquals(pixels(view)))
    }

    @Test fun folderMaskCommandsPaintHistoryAndReopenUseActualWindow() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        tap("Layers"); tap("Add layer group")
        val groupId = view.activeLayerId
        tap("Add layer"); val childId = view.activeLayerId
        tap("Close layers panel")
        val a = pagePoint(view, .2f, .35f); val b = pagePoint(view, .65f, .35f)
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == childId }?.rasterTileSurface != null }
        val originalPixels = pixels(view)
        fun group() = requireNotNull(model.currentPage()).layers.first { it.id == groupId }
        val assets = app.inkloom.document.DocumentAssetService(instrumentation.targetContext)
        fun mask() = assets.loadMaskTileSurface(requireNotNull(group().mask?.surface)).snapshot()
        tap("Layers"); tap("Select layer Folder 1")
        fun menu(command: String) {
            tap("Layer actions Folder 1"); tap("Mask")
            compose.onNodeWithText(command).performScrollTo(); tap(command)
            compose.waitUntil(10_000) { !view.selectionLayerTransferPending }
        }
        menu("Mask outside selection")
        assertEquals(0, mask().alphaAt(0, 0))
        assertTrue(view.isEditingLayerMask(groupId))
        val empty = requireNotNull(group().mask?.surface)
        tap("Close layers panel")
        stroke(a, b)
        compose.waitUntil(10_000) { group().mask?.surface != empty }
        val painted = requireNotNull(group().mask?.surface)
        assertTrue(mask().alphaAt((mask().logicalWidth * .4f).toInt(), (mask().logicalHeight * .35f).toInt()) > 0)
        // Mask edit owns the folder, never paints an arbitrary child layer.
        assertEquals(groupId, view.activeLayerId)
        tap("Undo"); compose.waitUntil { group().mask?.surface == empty }
        tap("Undo"); compose.waitUntil { group().mask == null }
        tap("Redo"); compose.waitUntil { group().mask?.surface == empty }
        tap("Redo"); compose.waitUntil { group().mask?.surface == painted }
        tap("Layers"); menu("Disable layer mask")
        assertTrue(!requireNotNull(group().mask).enabled)
        tap("Undo"); compose.waitUntil { group().mask?.enabled == true }
        menu("Invert layer mask")
        val inverted = requireNotNull(group().mask?.surface)
        assertEquals(255, mask().alphaAt(0, 0))
        tap("Undo"); compose.waitUntil { group().mask?.surface == painted }
        tap("Redo"); compose.waitUntil { group().mask?.surface == inverted }
        menu("Delete layer mask"); assertEquals(null, group().mask)
        tap("Undo"); compose.waitUntil { group().mask?.surface == inverted }
        menu("Hide all"); assertEquals(0, mask().alphaAt(0, 0))
        tap("Undo"); compose.waitUntil { group().mask?.surface == inverted }
        tap("Edit layer mask Folder 1"); assertTrue(view.isEditingLayerMask(groupId))
        tap("Select layer Layer 1"); assertTrue(!view.isEditingLayerMask(groupId))
        org.junit.Assert.assertArrayEquals("Mask commands must not rewrite child pixels", originalPixels, pixels(view))
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(inverted, group().mask?.surface)
        assertEquals(255, mask().alphaAt(0, 0))
        tap("Layers"); tap("Select layer Layer 1")
        org.junit.Assert.assertArrayEquals(originalPixels, pixels(canvas()))
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

    private fun dragFinger(start: Offset, end: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, MotionEvent.TOOL_TYPE_FINGER)
        repeat(12) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 12f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, MotionEvent.TOOL_TYPE_FINGER)
        }
        event(MotionEvent.ACTION_UP, end, time, MotionEvent.TOOL_TYPE_FINGER)
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
