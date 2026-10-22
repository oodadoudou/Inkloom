package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Rotate or flip canvas changes the artwork itself, as one undo step. */
class CanvasOrientationWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun flipHorizontallyMovesInkToTheOtherSideAndUndoRestoresIt() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        val y = bounds.top + bounds.height() * .5f
        stroke(Offset(bounds.left + bounds.width() * .15f, y), Offset(bounds.left + bounds.width() * .30f, y))
        compose.waitUntil(10_000) { !empty(view) }
        assertTrue(inkIn(view, .15f, .30f)); assertFalse(inkIn(view, .70f, .85f))

        openOrientation()
        compose.onNodeWithText("Flip horizontally").performClick()
        compose.waitUntil(10_000) { inkIn(canvas(), .70f, .85f) }
        assertFalse("The original side is clear", inkIn(canvas(), .15f, .30f))

        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(10_000) { inkIn(canvas(), .15f, .30f) }
        assertFalse(inkIn(canvas(), .70f, .85f))
    }

    @Test fun rotateCounterclockwiseSwapsThePageSides() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        canvas()
        val before = model().currentPage()!!
        openOrientation()
        compose.onNodeWithText("Rotate 90° counterclockwise").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.width == before.height }
        assertEquals(before.width, model().currentPage()!!.height)
        assertEquals(3, model().currentPage()!!.backgroundRotationQuarterTurns)
    }

    @Test fun pagePanelRotationTurnsLayerMasksWithThePage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = app.inkloom.document.DocumentAssetService(context)
        val mask = app.inkloom.engine.v5.surface.MaskTileSurface(64, 32, initialDefaultAlpha = 255)
        mask.beginWrite("hole").use { it.setAlpha(50, 5, 0); it.commit().close() }
        val reference = assets.saveMaskTileSurface(mask.snapshot())
        val page = app.inkloom.document.InkPage(id = "rotate-mask-page", width = 64f, height = 32f,
            layers = app.inkloom.document.defaultInkLayers().map { if (it.id == app.inkloom.document.LayerIds.INK) it.copy(mask = app.inkloom.document.LayerMask(surface = reference)) else it })
        app.inkloom.document.FileDocumentStore(context.filesDir).save(app.inkloom.document.InkDocument(id = "rotate-mask", title = "Rotate mask", pages = listOf(page)))
        try {
            model().rotatePageClockwise("rotate-mask", page.id)
            compose.waitUntil(10_000) { app.inkloom.document.FileDocumentStore(context.filesDir).get("rotate-mask")?.pages?.single()?.width == 32f }
            val rotated = requireNotNull(app.inkloom.document.FileDocumentStore(context.filesDir).get("rotate-mask")).pages.single()
            val surface = assets.loadMaskTileSurface(requireNotNull(rotated.layers.first { it.id == app.inkloom.document.LayerIds.INK }.mask?.surface)).snapshot()
            assertEquals(32, surface.logicalWidth); assertEquals(64, surface.logicalHeight)
            // Clockwise: (50, 5) → (32 − 1 − 5, 50).
            assertEquals(0, surface.alphaAt(26, 50)); assertEquals(255, surface.alphaAt(26, 10))
        } finally { assets.releaseAssetReservation(reference.assetPath) }
    }

    @Test fun clearUndoHistoryKeepsTheArtworkAndEmptiesUndo() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        val y = bounds.top + bounds.height() * .5f
        stroke(Offset(bounds.left + bounds.width() * .3f, y), Offset(bounds.left + bounds.width() * .6f, y))
        compose.waitUntil(10_000) { !empty(view) && !view.hasPendingPaintPublicationForMerge() }
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Clear undo history…").performScrollTo().performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(5_000) { view.historySize == 0 }
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitForIdle(); SystemClock.sleep(300)
        assertTrue("The artwork is kept", inkIn(view, .3f, .6f))
    }

    private fun openOrientation() {
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Rotate or flip canvas…").performScrollTo().performClick()
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

    private fun inkIn(view: InkCanvasView, from: Float, to: Float): Boolean {
        val snapshot = view.activeRasterSnapshotForTest() ?: return false
        return (0 until 40).any { step ->
            val x = (snapshot.logicalWidth * (from + (to - from) * step / 40f)).toInt()
            (-4..4).any { dy -> snapshot.pixelAt(x, snapshot.logicalHeight / 2 + dy) != 0 }
        }
    }

    /** The page's on-screen rectangle at the fit-to-window view. */
    private fun pageScreenBounds(view: InkCanvasView): android.graphics.RectF {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return android.graphics.RectF(location[0].toFloat(), location[1].toFloat(), location[0] + view.width.toFloat(), location[1] + view.height.toFloat())
    }

    private fun stroke(start: Offset, end: Offset) {
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, start, down)
        for (i in 1..20) { SystemClock.sleep(10); motion(MotionEvent.ACTION_MOVE, start + (end - start) * (i / 20f), down) }
        motion(MotionEvent.ACTION_UP, end, down)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun empty(view: InkCanvasView): Boolean {
        val snapshot = view.activeRasterSnapshotForTest() ?: return true
        return snapshot.tileInfo.none { it.nonTransparentPixelCount > 0 }
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
