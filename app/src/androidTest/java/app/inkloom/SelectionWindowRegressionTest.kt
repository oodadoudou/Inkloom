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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
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

    @Test fun featherPreviewCancelApplyUndoAndDeselectUndoAreReversible() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .5f))
        compose.waitForIdle()
        fun alpha(): ByteArray {
            val mask = requireNotNull(view.selectionMaskSurfaceSnapshot())
            return mask.copyAlpha(app.inkloom.engine.v5.surface.PixelRect(0, 0, mask.logicalWidth, mask.logicalHeight))
        }
        val original = alpha()
        fun preview() {
            tap("Selection settings"); tap("Feather selection")
            val slider = center("Selection feather radius")
            tapAt(slider + Offset(40f, 0f)); compose.waitForIdle()
            assertTrue(view.selectionFeatherPixels > 0f)
            assertTrue(!original.contentEquals(alpha()))
        }
        preview(); tap("Cancel feather")
        org.junit.Assert.assertArrayEquals(original, alpha())
        assertEquals(0f, view.selectionFeatherPixels, 0f)
        preview(); tap("Apply feather")
        val feathered = alpha()
        hoverThenTap(toolbarCenter("Undo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(original, alpha())
        hoverThenTap(toolbarCenter("Redo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(feathered, alpha())
        tap("Deselect")
        org.junit.Assert.assertFalse(view.hasActiveSelection())
        hoverThenTap(toolbarCenter("Undo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(feathered, alpha())
    }

    @Test fun interpolationChoiceReachesCanvasAndSurvivesRestart() {
        tap("New Note"); tap("Create")
        val view = canvas()
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .6f))
        compose.waitForIdle()
        assertEquals(app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR, view.selectionInterpolation)
        tap("Selection settings")
        tap("Bicubic")
        compose.waitUntil(5_000) { view.selectionInterpolation == app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC, canvas().selectionInterpolation)
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
        val dragStarted = SystemClock.uptimeMillis()
        stroke(pagePoint(view, .4f, .4f), pagePoint(view, .4f, .6f))
        val dragMillis = SystemClock.uptimeMillis() - dragStarted
        println("Selection window: 24 move packets took $dragMillis ms")
        assertTrue("Selection drag blocked input for $dragMillis ms", dragMillis < 5_000L)
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

    @Test fun oppositeCornerCancelAndWholeSessionUndoWorkThroughWindowControls() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        val initial = requireNotNull(view.activeRasterSnapshotForTest())
        val all = app.inkloom.engine.v5.surface.PixelRect(0, 0, initial.logicalWidth, initial.logicalHeight)
        val originalPixels = initial.copyPremultipliedPixels(all)
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .5f)); compose.waitForIdle()
        tap("Free transform"); tap("Uniform")
        val start = requireNotNull(view.selectionTransformUiState).pose
        val corner = start.point(0f, 0f)
        val resizedAt = SystemClock.uptimeMillis()
        stroke(pagePoint(view, corner.first / initial.logicalWidth, corner.second / initial.logicalHeight), pagePoint(view, .1f, .2f))
        val resizeMillis = SystemClock.uptimeMillis() - resizedAt
        println("Selection resize: 24 move packets took $resizeMillis ms")
        assertTrue("Corner resize blocked input for $resizeMillis ms", resizeMillis < 5_000L)
        compose.waitForIdle()
        val resized = requireNotNull(view.selectionTransformUiState).pose
        assertEquals(start.width / start.height, resized.width / resized.height, .001f)
        assertEquals(start.point(1f, 1f).first, resized.point(1f, 1f).first, 1f)
        assertEquals(start.point(1f, 1f).second, resized.point(1f, 1f).second, 1f)
        assertTrue(resized.width > start.width + 20f)
        tap("Cancel transform")
        org.junit.Assert.assertArrayEquals(originalPixels, requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        tap("Free transform")
        // Outside the bounds still moves this selection; it cannot replace it with a new lasso.
        stroke(pagePoint(view, .75f, .4f), pagePoint(view, .8f, .4f))
        stroke(pagePoint(view, .8f, .6f), pagePoint(view, .8f, .7f))
        compose.waitForIdle()
        val moved = requireNotNull(view.selectionTransformUiState).pose
        assertEquals(start.width, moved.width, 1f)
        assertEquals(start.centerX + initial.logicalWidth * .05f, moved.centerX, 2f)
        assertEquals(start.centerY + initial.logicalHeight * .1f, moved.centerY, 2f)
        tap("Transform dimensions")
        compose.onNode(hasText("Width (px)") and hasSetTextAction()).performTextReplacement("480")
        compose.onNode(hasText("Height (px)") and hasSetTextAction()).performTextReplacement("320")
        compose.onNode(hasText("Rotation (degrees)") and hasSetTextAction()).performTextReplacement("30")
        compose.waitForIdle()
        tap("Update dimensions")
        compose.waitUntil(5_000) { view.selectionTransformUiState?.pose?.width == 480f }
        val numeric = requireNotNull(view.selectionTransformUiState).pose
        assertEquals(480f, numeric.width, .01f); assertEquals(320f, numeric.height, .01f)
        assertEquals(30f, numeric.angle, .01f)
        tap("Apply transform")
        val applied = requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all)
        org.junit.Assert.assertFalse(originalPixels.contentEquals(applied))
        hoverThenTap(toolbarCenter("Undo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(originalPixels, requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        hoverThenTap(toolbarCenter("Redo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(applied, requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        tap("Deselect")
        org.junit.Assert.assertFalse(view.hasActiveSelection())
    }

    @Test fun perspectiveCornerThenAffineMoveCancelAndApplyUseTheSameOriginal() {
        tap("New Note"); tap("Create")
        val view=canvas()
        stroke(pagePoint(view,.3f,.4f),pagePoint(view,.5f,.4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        val source=requireNotNull(view.activeRasterSnapshotForTest())
        val all=app.inkloom.engine.v5.surface.PixelRect(0,0,source.logicalWidth,source.logicalHeight)
        val pixels=source.copyPremultipliedPixels(all)
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view,.2f,.3f),pagePoint(view,.6f,.5f)); compose.waitForIdle()
        tap("Transform selection"); tap("Distort")
        val original=requireNotNull(view.selectionTransformUiState).pose
        val q=original.sourceCorners()
        stroke(pagePoint(view,q[0].first/source.logicalWidth,q[0].second/source.logicalHeight),pagePoint(view,.3f,.33f))
        compose.waitForIdle()
        val distorted=requireNotNull(view.selectionTransformUiState).pose
        assertTrue(distorted.corners != null)
        assertEquals(q.drop(1),distorted.corners!!.drop(1))
        assertTrue(!pixels.contentEquals(requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all)))
        tap("Uniform")
        stroke(pagePoint(view,.75f,.4f),pagePoint(view,.8f,.4f))
        compose.waitForIdle()
        val moved=requireNotNull(view.selectionTransformUiState).pose
        assertTrue(moved.corners != null)
        distorted.corners.zip(moved.corners!!).forEach { (a,b) -> assertEquals(a.first+source.logicalWidth*.05f,b.first,2f) }
        tap("Cancel transform")
        org.junit.Assert.assertArrayEquals(pixels,requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        tap("Transform selection"); tap("Distort")
        stroke(pagePoint(view,q[0].first/source.logicalWidth,q[0].second/source.logicalHeight),pagePoint(view,.3f,.33f))
        tap("Transform selection")
        val after=requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all)
        assertTrue(!pixels.contentEquals(after))
        hoverThenTap(toolbarCenter("Undo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(pixels,requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        hoverThenTap(toolbarCenter("Redo")); compose.waitForIdle()
        org.junit.Assert.assertArrayEquals(after,requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
    }

    @Test fun topTransformEntryStaysAvailableWhenDraftMovesOutsidePaper() {
        tap("New Note"); tap("Create")
        val view=canvas()
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view,.2f,.3f),pagePoint(view,.6f,.5f)); compose.waitForIdle()
        val original=requireNotNull(view.selectionMaskSurfaceSnapshot()).nonZeroBounds()
        tap("Transform selection")
        stroke(pagePoint(view,.75f,.1f),pagePoint(view,.75f,.95f)); compose.waitForIdle()
        org.junit.Assert.assertFalse(view.hasActiveSelection())
        assertTrue(view.selectionTransformUiState != null)
        assertTrue(center("Transform selection").x > 0f)
        tap("Cancel transform")
        assertEquals(original,requireNotNull(view.selectionMaskSurfaceSnapshot()).nonZeroBounds())
    }

    @Test fun pinchDuringTransformKeepsDraftAndBackCancelsBeforeDrawingAgain() {
        tap("New Note"); tap("Create")
        val view = canvas()
        stroke(pagePoint(view, .3f, .4f), pagePoint(view, .5f, .4f))
        compose.waitUntil(10_000) { (view.activeRasterSnapshotForTest()?.tileCount ?: 0) > 0 }
        val initial = requireNotNull(view.activeRasterSnapshotForTest())
        val all = app.inkloom.engine.v5.surface.PixelRect(0, 0, initial.logicalWidth, initial.logicalHeight)
        val before = initial.copyPremultipliedPixels(all)
        tap("Selection"); tap("Rectangle selection")
        stroke(pagePoint(view, .2f, .3f), pagePoint(view, .6f, .5f)); compose.waitForIdle()
        tap("Free transform")
        stroke(pagePoint(view, .4f, .4f), pagePoint(view, .4f, .5f))
        val draft = requireNotNull(view.selectionTransformUiState).pose
        val time = SystemClock.uptimeMillis()
        fun packet(action: Int, points: List<Offset>) {
            val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, points.size,
                Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } },
                Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].x; y = points[i].y; pressure = 1f; touchMajor = 8f } },
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
            SystemClock.sleep(20)
        }
        val a = pagePoint(view, .35f, .6f); val b = pagePoint(view, .65f, .6f)
        val c = pagePoint(view, .2f, .6f); val d = pagePoint(view, .8f, .6f)
        packet(MotionEvent.ACTION_DOWN, listOf(a))
        packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(a, b))
        repeat(8) { i -> val t = (i + 1) / 8f; packet(MotionEvent.ACTION_MOVE, listOf(a + (c - a) * t, b + (d - b) * t)) }
        packet(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(c, d))
        packet(MotionEvent.ACTION_UP, listOf(c))
        compose.waitForIdle()
        assertTrue("Pinch must navigate", view.viewportScale > 1.1f)
        assertEquals(draft, requireNotNull(view.selectionTransformUiState).pose)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle()
        org.junit.Assert.assertNull(view.selectionTransformUiState)
        org.junit.Assert.assertArrayEquals(before, requireNotNull(view.activeRasterSnapshotForTest()).copyPremultipliedPixels(all))
        tap("Deselect")
        val revision = requireNotNull(view.activeRasterSnapshotForTest()).revision
        stroke(pagePoint(view, .3f, .7f), pagePoint(view, .5f, .7f))
        compose.waitUntil(10_000) { view.activeRasterSnapshotForTest()?.revision != revision }
    }

    private fun toolbarCenter(label: String): Offset {
        compose.waitForIdle()
        val bounds = mutableListOf<android.graphics.Rect>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.contentDescription?.toString() == label) {
                bounds += android.graphics.Rect().also(node::getBoundsInScreen)
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        val rect = bounds.minBy { it.top }
        return Offset(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val point = floatArrayOf(x * view.width, y * view.height)
        instrumentation.runOnMainSync {
            android.graphics.Matrix().also { view.transformMatrixToGlobal(it); it.mapPoints(point) }
        }
        return Offset(point[0], point[1])
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
