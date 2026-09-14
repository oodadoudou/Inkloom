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
class LayerOpacityWindowTest {
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

    @Test fun opacityPreviewCancelCommitUndoAndPersistenceUseTheRealWindow() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        val layerId = view.activeLayerId
        fun opacity() = requireNotNull(model.currentPage()).layers.first { it.id == layerId }.opacity
        val a = pagePoint(view, .25f, .3f); val b = pagePoint(view, .6f, .3f)
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == layerId }?.rasterTileSurface != null }
        val ink = pixels(view)
        assertTrue(ink.any { it != 0 })
        val probe = a + (b - a) * .5f
        fun brightness(): Int {
            SystemClock.sleep(120)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { return android.graphics.Color.red(bitmap.getPixel(probe.x.toInt(), probe.y.toInt())) }
            finally { bitmap.recycle() }
        }
        val dark = brightness()
        tap("Layers")
        val slider = bounds("Layer opacity")
        fun point(f: Float) = Offset(slider.left + 20f + (slider.width() - 40f) * f, slider.exactCenterY())
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point(.5f), down, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
        assertEquals("Preview must not save", 1f, opacity())
        assertTrue("Opacity preview must visibly affect canvas pixels", brightness() > dark + 15)
        event(MotionEvent.ACTION_CANCEL, point(.5f), down, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
        assertEquals(1f, opacity()); assertTrue(kotlin.math.abs(brightness() - dark) <= 2)
        assertTrue(!view.selectionLayerTransferPending)
        tapAt(point(.5f)); compose.waitUntil(10_000) { opacity() in .48f.. .52f && !view.selectionLayerTransferPending }
        val committed = opacity()
        org.junit.Assert.assertArrayEquals("Changing layer opacity must not rewrite paint", ink, pixels(view))
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == committed }
        tapAt(point(0f)); compose.waitUntil { opacity() == 0f }
        assertTrue(brightness() > dark + 40)
        tap("Undo"); compose.waitUntil { opacity() == committed }
        tap("Close layers panel")
        stroke(pagePoint(view, .25f, .6f), pagePoint(view, .6f, .6f))
        compose.waitUntil(10_000) { !ink.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { ink.contentEquals(pixels(view)) }
        assertEquals(committed, opacity())
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == committed }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(committed, opacity())
        org.junit.Assert.assertArrayEquals(ink, pixels(canvas()))
    }

    @Test fun numericOpacityAndBlendModeHaveScopedHistoryAndSurviveReopening() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        chooseRgb(180)
        val baseId = view.activeLayerId
        fun baseOpacity() = requireNotNull(model.currentPage()).layers.first { it.id == baseId }.opacity
        val a = pagePoint(view, .22f, .28f); val b = pagePoint(view, .55f, .28f)
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == baseId }?.rasterTileSurface != null }
        tap("Layers")
        val fixed = bounds("Layer properties")
        for (percent in listOf(0, 1, 50, 99, 100)) {
            tap("Edit layer opacity"); typePercent(percent.toString()); tap("Apply layer opacity")
            compose.waitUntil(10_000) { baseOpacity() == percent / 100f && !view.selectionLayerTransferPending }
        }
        tap("Edit layer opacity"); typePercent("25")
        assertEquals(1f, baseOpacity())
        typePercent("101")
        compose.onNodeWithContentDescription("Apply layer opacity").assertIsNotEnabled()
        typePercent("")
        compose.onNodeWithContentDescription("Apply layer opacity").assertIsNotEnabled()
        tap("Cancel layer opacity")
        assertEquals(1f, baseOpacity()); assertTrue(!view.selectionLayerTransferPending)
        compose.waitUntil(5_000) { fixed == bounds("Layer properties") }
        assertEquals("Numeric keyboard dismissal preserves the fixed bar", fixed, bounds("Layer properties"))
        tap("Edit layer opacity"); typePercent("50"); tap("Apply layer opacity")
        compose.waitUntil { baseOpacity() == .5f && !view.selectionLayerTransferPending }
        tap("Add layer")
        compose.waitUntil { view.activeLayerId != baseId }
        val upperId = view.activeLayerId
        fun mode() = requireNotNull(model.currentPage()).layers.first { it.id == upperId }.blendMode
        tap("Close layers panel"); chooseRgb(0); stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == upperId }?.rasterTileSurface != null }
        val ink = pixels(view)
        fun brightness(): Int {
            SystemClock.sleep(160)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { val probe = a + (b - a) * .5f; return android.graphics.Color.red(image.getPixel(probe.x.toInt(), probe.y.toInt())) }
            finally { image.recycle() }
        }
        val dark = brightness()
        tap("Layers"); tap("Layer blend mode")
        compose.onNodeWithText("Screen").performScrollTo(); tap("Screen")
        compose.waitUntil(10_000) { mode() == app.inkloom.document.LayerBlendMode.SCREEN && !view.selectionLayerTransferPending }
        val screened = brightness()
        assertTrue("Screen must expose the gray layer below black ink: $dark -> $screened", screened > dark + 30)
        org.junit.Assert.assertArrayEquals(ink, pixels(view))
        tap("Undo"); compose.waitUntil { mode() == app.inkloom.document.LayerBlendMode.NORMAL }
        assertTrue(kotlin.math.abs(brightness() - dark) <= 2)
        tap("Redo"); compose.waitUntil { mode() == app.inkloom.document.LayerBlendMode.SCREEN }
        tap("Close layers panel")
        stroke(pagePoint(view, .22f, .55f), pagePoint(view, .55f, .55f))
        compose.waitUntil(10_000) { !ink.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { ink.contentEquals(pixels(view)) }
        assertEquals(app.inkloom.document.LayerBlendMode.SCREEN, mode())
        tap("Undo"); compose.waitUntil { mode() == app.inkloom.document.LayerBlendMode.NORMAL }
        org.junit.Assert.assertArrayEquals(ink, pixels(view))
        tap("Redo"); compose.waitUntil { mode() == app.inkloom.document.LayerBlendMode.SCREEN }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(app.inkloom.document.LayerBlendMode.SCREEN, mode())
        assertEquals(.5f, baseOpacity())
        tap("Layers"); tap("Layer 1")
        assertEquals(fixed, bounds("Layer properties"))
        assertTrue(hasText("Screen"))
    }

    @Test fun groupOpacityCompositesOverlappingChildrenOnceAndSharesPaintHistory() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        tap("Layers"); tap("Add layer group")
        val groupId = view.activeLayerId
        tap("Add layer"); val firstId = view.activeLayerId
        tap("Close layers panel")
        stroke(pagePoint(view, .2f, .4f), pagePoint(view, .6f, .4f))
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == firstId }?.rasterTileSurface != null }
        tap("Layers"); tap("Add layer"); val secondId = view.activeLayerId
        tap("Close layers panel")
        stroke(pagePoint(view, .4f, .25f), pagePoint(view, .4f, .6f))
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == secondId }?.rasterTileSurface != null }
        val original = pixels(view)
        tap("Layers"); tap("Select layer Folder 1")
        compose.waitUntil { view.activeLayerId == groupId }
        fun opacity() = requireNotNull(model.currentPage()).layers.first { it.id == groupId }.opacity
        val probes = listOf(.3f to .4f, .4f to .4f, .4f to .5f)
        fun screenValues(): List<Int> {
            SystemClock.sleep(160)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            return try { probes.map { (x, y) ->
                val point = pagePoint(canvas(), x, y)
                android.graphics.Color.red(bitmap.getPixel(point.x.toInt(), point.y.toInt()))
            } } finally { bitmap.recycle() }
        }
        fun exportValues(): List<Int> {
            val bitmap = app.inkloom.document.DocumentAssetService(compose.activity).renderPageBitmap(requireNotNull(model.currentPage()), 800)
            return try { probes.map { (x, y) -> android.graphics.Color.red(bitmap.getPixel((x * bitmap.width).toInt(), (y * bitmap.height).toInt())) } }
            finally { bitmap.recycle() }
        }
        val beforeScreen = screenValues(); val beforeExport = exportValues()
        assertTrue("All probes must contain real paint", beforeScreen.all { it < 100 } && beforeExport.all { it < 100 })
        tap("Edit layer opacity"); typePercent("50"); tap("Apply layer opacity")
        compose.waitUntil(10_000) { opacity() == .5f && !view.selectionLayerTransferPending }
        fun checkPicture() {
            screenValues().zip(beforeScreen).forEach { (after, before) ->
                assertEquals("Group opacity applies once, including overlapping paint", (255 + before) / 2.0, after.toDouble(), 2.0)
            }
            exportValues().zip(beforeExport).forEach { (after, before) ->
                assertEquals("Export applies the same group alpha", (255 + before) / 2.0, after.toDouble(), 2.0)
            }
        }
        checkPicture()
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == .5f }
        tap("Select layer Layer 2"); tap("Close layers panel")
        stroke(pagePoint(view, .2f, .7f), pagePoint(view, .6f, .7f))
        compose.waitUntil(10_000) { !original.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { original.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil { opacity() == 1f }
        tap("Redo"); compose.waitUntil { opacity() == .5f }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(.5f, opacity()); checkPicture()
        org.junit.Assert.assertArrayEquals(original, pixels(canvas()))
    }

    @Test fun throughModeUsesTheBackdropAndItsOwnUndoHistoryInTheRealWindow() {
        tap("New Note"); tap("Create"); val view = canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        chooseRgb(200)
        val bottomId = view.activeLayerId
        val a = pagePoint(view, .2f, .35f); val b = pagePoint(view, .6f, .35f)
        fun brightness(): Int {
            SystemClock.sleep(160)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { val probe = a + (b - a) * .5f; return android.graphics.Color.red(bitmap.getPixel(probe.x.toInt(), probe.y.toInt())) }
            finally { bitmap.recycle() }
        }
        stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == bottomId }?.rasterTileSurface != null }
        val backdrop = brightness()
        tap("Layers"); tap("Add layer group"); val groupId = view.activeLayerId
        tap("Add layer"); val childId = view.activeLayerId
        tap("Close layers panel"); chooseRgb(128); stroke(a, b)
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == childId }?.rasterTileSurface != null }
        val ink = pixels(view)
        fun group() = requireNotNull(model.currentPage()).layers.first { it.id == groupId }
        tap("Layers"); tap("Layer blend mode")
        compose.onNodeWithText("Multiply").performScrollTo(); tap("Multiply")
        compose.waitUntil(10_000) { !view.selectionLayerTransferPending }
        tap("Select layer Folder 1")
        val isolated = brightness()
        // Arrange retained content away from the pixel probe, then exercise actual window controls.
        instrumentation.runOnMainSync {
            val document = requireNotNull(model.currentDocument())
            val page = requireNotNull(model.currentPage())
            model.addElement(document.id, page.id, app.inkloom.document.PageElement.Shape(
                x = .72f, y = .75f, width = 24f, height = 24f, layerId = childId,
                kind = app.inkloom.document.ShapeKind.RECTANGLE, colorArgb = 0xFF000000,
                fillArgb = 0xFF000000, strokeWidth = 0f))
        }
        compose.waitForIdle()
        tap("Layer blend mode"); tap("Through")
        compose.waitUntil(10_000) { group().passThrough && !view.selectionLayerTransferPending }
        val through = brightness()
        assertTrue("Child multiply must see the external backdrop: $isolated -> $through", through < isolated - 10)
        tap("Undo"); compose.waitUntil { !group().passThrough }
        assertEquals(isolated.toDouble(), brightness().toDouble(), 2.0)
        tap("Redo"); compose.waitUntil { group().passThrough }
        tap("Layer blend mode"); tap("Normal")
        compose.waitUntil { !group().passThrough && !view.selectionLayerTransferPending }
        tap("Undo"); compose.waitUntil { group().passThrough }
        assertEquals(through.toDouble(), brightness().toDouble(), 2.0)
        val conversions = view.retainedBitmapConversionCount
        assertTrue("Mixed Through must convert retained content", conversions > 0)
        tap("Edit layer opacity"); typePercent("50"); tap("Apply layer opacity")
        compose.waitUntil { group().opacity == .5f && !view.selectionLayerTransferPending }
        val half = brightness()
        assertEquals("Changing group opacity must not rerender or reconvert retained content", conversions, view.retainedBitmapConversionCount)
        android.util.Log.i("InkloomLayers", "Through window backdrop=$backdrop isolated=$isolated through=$through half=$half")
        assertEquals("Half-strength Through interpolates the actual backdrop once", (backdrop + through) / 2.0, half.toDouble(), 2.0)
        tap("Select layer Layer 1"); tap("Close layers panel")
        stroke(pagePoint(view, .2f, .65f), pagePoint(view, .6f, .65f))
        compose.waitUntil(10_000) { !ink.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil(10_000) { ink.contentEquals(pixels(view)) }
        tap("Undo"); compose.waitUntil { group().opacity == 1f }
        tap("Redo"); compose.waitUntil { group().opacity == .5f }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertTrue(group().passThrough); assertEquals(.5f, group().opacity)
        org.junit.Assert.assertArrayEquals(ink, pixels(canvas()))
    }

    @Test fun scrollingTheActualLayerListKeepsPropertiesFixedOnTheSelectedLayer() {
        tap("New Note"); tap("Create"); val view = canvas(); tap("Layers")
        val propertyBounds = bounds("Layer properties")
        repeat(12) { tap("Add layer") }
        val targetId = view.activeLayerId
        fun visibleRows(): Set<String> = compose.onAllNodes(hasContentDescription("Reorder layer", substring = true))
            .fetchSemanticsNodes().filter { it.boundsInWindow.height > 0f }
            .map { it.config[SemanticsProperties.ContentDescription].joinToString() }.toSet()
        val before = visibleRows()
        val list = bounds("Layer list")
        val top = Offset(list.exactCenterX(), list.top + 60f)
        val bottom = Offset(list.exactCenterX(), list.bottom - 60f)
        fun swipe(start: Offset, end: Offset) {
            val down = SystemClock.uptimeMillis()
            event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_FINGER)
            repeat(20) { i ->
                SystemClock.sleep(16)
                event(MotionEvent.ACTION_MOVE, start + (end - start) * ((i + 1) / 20f), down, MotionEvent.TOOL_TYPE_FINGER)
            }
            event(MotionEvent.ACTION_UP, end, down, MotionEvent.TOOL_TYPE_FINGER)
            compose.waitForIdle()
        }
        swipe(bottom, top)
        if (before == visibleRows()) swipe(top, bottom)
        assertTrue("Native vertical drag must actually scroll the layer list", before != visibleRows())
        assertEquals(propertyBounds, bounds("Layer properties"))
        assertEquals(targetId, view.activeLayerId)
        tap("Edit layer opacity"); typePercent("25"); tap("Apply layer opacity")
        lateinit var model: app.inkloom.ui.InkloomViewModel
        instrumentation.runOnMainSync { model = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java] }
        compose.waitUntil(10_000) { model.currentPage()?.layers?.first { it.id == targetId }?.opacity == .25f }
        assertEquals("A scrolled-off layer remains the property target", targetId, view.activeLayerId)
    }

    private fun chooseRgb(value: Int) {
        tap("Colors"); tap("Wheel"); tap("Edit color values")
        for (channel in listOf("R", "G", "B")) {
            val node = compose.onNodeWithContentDescription("Color value $channel")
            val field = node.fetchSemanticsNode()
            tapAt(field.boundsInWindow.center + field.positionOnScreen - field.positionInWindow)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MOVE_END)
            repeat(field.config[SemanticsProperties.EditableText].text.length) {
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL)
            }
            value.toString().forEach { digit -> instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_0 + digit.digitToInt()) }
            compose.waitForIdle()
        }
        tap("Done")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun typePercent(text: String) {
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
        val screen = field.boundsInWindow.center + field.positionOnScreen - field.positionInWindow
        tapAt(screen)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MOVE_END)
        repeat(field.config[SemanticsProperties.EditableText].text.length) {
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL)
        }
        text.forEach { digit -> instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_0 + digit.digitToInt()) }
        compose.waitForIdle()
        assertEquals(text, compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
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
