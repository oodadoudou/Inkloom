package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
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
class BrushWindowReturnTest {
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
            context.getSharedPreferences("inkloom_brush_usage", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun editorTitleSwitchesNotebooksWithoutReturningToGallery() {
        val title = "Switcher ${System.nanoTime()}"
        tap("New Note")
        compose.onNodeWithText("Title").performTextReplacement(title)
        compose.waitForIdle()
        tap("Create")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Switch notebook").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Switch notebook").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("Distributed Systems").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("Distributed Systems").assertExists()
        compose.onNodeWithText("1/3").assertExists()
        compose.onNodeWithContentDescription("Next page").performTouchInput { click() }
        compose.onNodeWithContentDescription("Next page").performTouchInput { click() }
        compose.onNodeWithText("3/3").assertExists()
        compose.onNodeWithContentDescription("Switch notebook").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText(title).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("1/1").assertExists()
        compose.onNodeWithContentDescription("Switch notebook").performTouchInput { click() }
        compose.onNodeWithText("Distributed Systems").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("3/3").assertExists()
    }

    @Test fun lastPagePlusCreatesBlankMatchingTemplateAndNormalNextStillNavigates() {
        tap("New Note")
        tap("New note template Grid · Medium")
        compose.onNodeWithContentDescription("New note paper color Warm ivory").performScrollTo()
        tap("New note paper color Warm ivory")
        tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .5f), pagePoint(view, .7f, .5f))
        compose.waitUntil(10_000) { view.lastBrushExecutionFingerprint != null }
        compose.onNodeWithContentDescription("Add page using current template").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("2/2").assertExists()
        val store = app.inkloom.document.FileDocumentStore(compose.activity.filesDir)
        val note = store.list().first { it.title == "Untitled Note" }
        assertEquals(2, note.pages.size)
        assertEquals(note.pages[0].background, note.pages[1].background)
        assertEquals(note.pages[0].width, note.pages[1].width)
        assertEquals(note.pages[0].height, note.pages[1].height)
        assertTrue(note.pages[1].elements.isEmpty())
        assertEquals(app.inkloom.document.InkPage().layers, note.pages[1].layers)
        compose.onNodeWithContentDescription("Previous page").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("1/2").assertExists()
        compose.onNodeWithContentDescription("Next page").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("2/2").assertExists()
        assertEquals(2, store.list().first { it.id == note.id }.pages.size)
        compose.onNodeWithContentDescription("Add page using current template").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("3/3").assertExists()
        assertEquals(3, store.list().first { it.id == note.id }.pages.size)
    }

    @Test fun realWindowPinchShrinksBelowFitThenPaintRemainsUnderThePen() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val center = pagePoint(view, .5f, .5f)
        val down = SystemClock.uptimeMillis()
        fun fingers(action: Int, halfSpan: Float, dx: Float = 0f, pair: Boolean = true) {
            val points = listOf(Offset(center.x + dx - halfSpan, center.y), Offset(center.x + dx + halfSpan, center.y)).take(if (pair) 2 else 1)
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points.size,
                points.indices.map { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray(),
                points.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = .5f; touchMajor = 4f } }.toTypedArray(),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        fingers(MotionEvent.ACTION_DOWN, 200f, pair = false)
        fingers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 200f)
        repeat(12) { i -> SystemClock.sleep(16); fingers(MotionEvent.ACTION_MOVE, 200f - (i + 1) * 12.5f) }
        fingers(MotionEvent.ACTION_MOVE, 50f, 40f)
        fingers(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 50f, 40f)
        fingers(MotionEvent.ACTION_UP, 50f, 40f, pair = false)
        compose.waitForIdle()
        assertTrue("Fit must not be the minimum zoom: ${view.viewportScale}", view.viewportScale in .05f.. .6f)
        assertTrue("A small canvas must remain pannable", view.viewportPanX > 20f)
        fun transformedPoint(x: Float): Offset {
            val point = app.inkloom.document.ViewportTransform.pageToView(view.width * x, view.height * .5f,
                view.width.toFloat(), view.height.toFloat(), app.inkloom.document.ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees))
            val origin = pagePoint(view, 0f, 0f)
            return origin + Offset(point.first, point.second)
        }
        stroke(transformedPoint(.3f), transformedPoint(.7f))
        compose.waitUntil(10_000) { view.lastBrushExecutionFingerprint != null }
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        val x = snapshot.logicalWidth / 2; val y = snapshot.logicalHeight / 2
        assertTrue("Reduced canvas input must land at document centre", (-5..5).any { dy -> (-5..5).any { dx -> snapshot.pixelAt(x + dx, y + dy) != 0 } })
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/inkloom-173-small-canvas.png")).use { it.readBytes() }
    }

    @Test fun drawingDismissesBrushWindowAndReopeningRevealsCurrentPencil() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Brushes"); tap("Brush category Pencil"); tap("Brush preset HB Pencil")
        // The floating panel is on the right; this exposed page segment remains drawable.
        stroke(pagePoint(view, .12f, .35f), pagePoint(view, .35f, .35f))
        compose.waitForIdle()
        assertTrue("Drawing must dismiss the brush library", !hasLabel("Close brushes panel"))
        compose.waitUntil(10_000) { view.lastBrushExecutionFingerprint != null }
        assertEquals("system-hb-pencil", view.lastBrushExecutionFingerprint?.presetId)
        tap("Brushes")
        compose.waitUntil(5_000) { hasLabel("Brush preset HB Pencil") }
        assertEquals("system-hb-pencil", view.brushId)
    }

    @Test fun consecutiveEraserContactsSurviveBrushWindowAndActivityRecreation() {
        tap("New Note"); tap("Create")
        val view = canvas()
        val rows = (0..5).map { .3f + it * .065f }
        rows.forEach { y -> stroke(pagePoint(view, .2f, y), pagePoint(view, .6f, y)) }
        compose.waitUntil(10_000) {
            val snapshot = view.activeRasterSnapshotForTest() ?: return@waitUntil false
            rows.all { y -> snapshot.pixelAt((snapshot.logicalWidth * .4f).toInt(), (snapshot.logicalHeight * y).toInt()) != 0 }
        }
        tap("Eraser")
        rows.forEach { y -> stroke(pagePoint(view, .35f, y), pagePoint(view, .45f, y)) }
        // Eraser selection opens the brush library; a canvas contact may dismiss it.
        if (!hasLabel("Close brushes panel")) tap("Brushes")
        tap("Close brushes panel")
        val context = instrumentation.targetContext
        val assets = app.inkloom.document.DocumentAssetService(context)
        fun erased(snapshot: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot): Boolean = rows.all { y ->
            val py = (snapshot.logicalHeight * y).toInt()
            snapshot.pixelAt((snapshot.logicalWidth * .4f).toInt(), py) == 0 &&
                snapshot.pixelAt((snapshot.logicalWidth * .25f).toInt(), py) != 0
        }
        compose.waitUntil(15_000) {
            val page = app.inkloom.document.FileDocumentStore(context.filesDir).list().firstOrNull()?.pages?.firstOrNull()
            val reference = page?.layers?.firstOrNull { it.id == view.activeLayerId }?.rasterTileSurface
            reference != null && erased(assets.loadRasterTileSurface(reference).snapshot())
        }
        assertTrue("Brush window recomposition restored erased pixels", erased(requireNotNull(view.activeRasterSnapshotForTest())))
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertTrue("Activity recreation restored erased pixels", erased(requireNotNull(canvas().activeRasterSnapshotForTest())))
    }

    @Test fun eraserToBrushRestoresFactoryPencilAndItsCategoryAfterRestart() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Brushes"); tap("Brush category Pencil"); tap("Brush preset HB Pencil")
        stroke(pagePoint(view, .15f, .35f), pagePoint(view, .35f, .35f))
        tap("Eraser")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .2f, .4f))
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        tap("Brushes")
        assertEquals("Factory brush return must use durable tool history", "system-hb-pencil", canvas().brushId)
        compose.waitUntil(5_000) { hasLabel("Brush preset HB Pencil") }
        val current = canvas()
        stroke(pagePoint(current, .15f, .55f), pagePoint(current, .35f, .55f))
        compose.waitUntil(10_000) { current.lastBrushExecutionFingerprint?.presetId == "system-hb-pencil" }
    }

    private fun hasLabel(label: String): Boolean {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
            return (0 until node.childCount).any { visit(node.getChild(it)) }
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
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
