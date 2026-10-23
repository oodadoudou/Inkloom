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
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import kotlin.math.abs

/** Vector layers: lines are kept, erased by area or whole line, edited, undone and rasterized. */
class VectorLayerWindowTest {
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

    @Test fun vectorLinesAreDrawnErasedEditedAndRasterized() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("Vector layer").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.any { it.vector != null } == true }
        val layer = model().currentPage()!!.layers.first { it.vector != null }
        compose.waitUntil(5_000) { view.activeLayerId == layer.id }
        compose.waitForIdle(); SystemClock.sleep(300)

        val bounds = pageScreenBounds(view)
        fun at(x: Float, y: Float) = Offset(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        stroke(at(.08f, .4f), at(.38f, .4f))
        stroke(at(.08f, .6f), at(.38f, .6f))
        awaitLines(layer.id, 2)
        assertReplayMatches(view)

        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        awaitLines(layer.id, 1)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        awaitLines(layer.id, 2)

        // The touched-area eraser cuts both lines in two.
        stroke(at(.16f, .3f), at(.16f, .7f), eraser = true)
        awaitLines(layer.id, 4)
        assertReplayMatches(view)

        // The whole-line eraser removes the two right-hand pieces it touches.
        openVectorLines(layer.name)
        compose.onNodeWithText("Whole line").performClick()
        compose.onNodeWithText("Done").performClick()
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        compose.waitUntil(5_000) { preferences.getBoolean("vectorEraseWholeLine", false) }
        compose.waitForIdle(); SystemClock.sleep(300)
        stroke(at(.33f, .3f), at(.33f, .7f), eraser = true)
        awaitLines(layer.id, 2)
        assertReplayMatches(view)
        val remaining = lines(layer.id)
        assertTrue("the left-hand pieces remain", remaining.all { stroke -> (0 until stroke.pointCount).all { stroke.x(it) < .2f } })

        // Dialog operations: recolour, then delete all, then undo.
        openVectorLines(layer.name)
        compose.onNodeWithText("2 of 2 lines").assertExists()
        compose.onNodeWithText("Delete").performClick()
        awaitLines(layer.id, 0)
        compose.onNodeWithText("Done").performClick()
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        awaitLines(layer.id, 2)
        assertReplayMatches(view)

        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(layer.name)].performClick()
        compose.onNodeWithText("Rasterize").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.first { it.id == layer.id }?.vector == null }
    }

    @Test fun selectionTransformsMoveTheLines() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("Vector layer").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.any { it.vector != null } == true }
        val layer = model().currentPage()!!.layers.first { it.vector != null }
        compose.waitUntil(5_000) { view.activeLayerId == layer.id }
        compose.waitForIdle(); SystemClock.sleep(300)
        val bounds = pageScreenBounds(view)
        fun at(x: Float, y: Float) = Offset(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        stroke(at(.1f, .4f), at(.3f, .4f))
        awaitLines(layer.id, 1)
        val before = lines(layer.id).single()
        instrumentation.runOnMainSync { view.selectAllEditableStrokes(); view.flipSelection(horizontal = true) }
        compose.waitUntil(10_000) { lines(layer.id).singleOrNull()?.let { it.x(0) > .5f } == true }
        val flipped = lines(layer.id).single()
        assertEquals("the line is mirrored across the page", 1f - before.x(0), flipped.x(0), .003f)
        assertEquals(before.diameter, flipped.diameter, 1e-3f)
        assertReplayMatches(view)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(10_000) { lines(layer.id).singleOrNull()?.let { it.x(0) < .5f } == true }
        assertReplayMatches(view)
    }

    private fun lines(layerId: String) = model().currentPage()?.layers?.first { it.id == layerId }?.vector?.strokes.orEmpty()

    private fun awaitLines(layerId: String, count: Int) {
        try { compose.waitUntil(10_000) { lines(layerId).size == count } }
        catch (failure: Throwable) { throw AssertionError("expected $count lines, found ${lines(layerId).size}", failure) }
        compose.waitForIdle()
    }

    private fun openVectorLines(name: String) {
        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(name)].performClick()
        compose.onNodeWithText("Vector lines…").performClick()
        compose.onNodeWithText("Vector lines").assertExists()
    }

    /** The live pixels are exactly the lines rendered afresh, up to rounding. */
    private fun assertReplayMatches(view: InkCanvasView) {
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() }
        var live: PixelTileSurfaceSnapshot? = null; var replay: PixelTileSurfaceSnapshot? = null
        instrumentation.runOnMainSync { view.awaitV5StrokeWorkerIdleForTest(); live = view.activeRasterSnapshotForTest(); replay = view.vectorReplayForTest() }
        val a = requireNotNull(live); val b = requireNotNull(replay)
        var worst = 0; var inked = 0
        for (y in 0 until a.logicalHeight step 2) for (x in 0 until a.logicalWidth step 2) {
            val p = a.pixelAt(x, y); val q = b.pixelAt(x, y)
            if (p != 0) inked++
            for (shift in intArrayOf(0, 8, 16, 24)) worst = maxOf(worst, abs((p ushr shift and 255) - (q ushr shift and 255)))
        }
        assertTrue("the layer has ink", inked > 0)
        assertTrue("live pixels match the replayed lines (worst channel difference $worst)", worst <= 2)
    }

    /** Index of a layer row among the rows' action buttons (rows are listed top layer first). */
    private fun layerRow(name: String): Int {
        val rows = compose.onAllNodesWithContentDescription("Layer actions").fetchSemanticsNodes()
        val names = compose.onAllNodesWithText(name).fetchSemanticsNodes()
        val target = names.first().boundsInRoot.center.y
        return rows.indices.minBy { kotlin.math.abs(rows[it].boundsInRoot.center.y - target) }
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

    private fun pageScreenBounds(view: InkCanvasView): android.graphics.RectF {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return android.graphics.RectF(location[0].toFloat(), location[1].toFloat(), location[0] + view.width.toFloat(), location[1] + view.height.toFloat())
    }

    private fun stroke(start: Offset, end: Offset, eraser: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, start, down, eraser)
        for (i in 1..20) { SystemClock.sleep(10); motion(MotionEvent.ACTION_MOVE, start + (end - start) * (i / 20f), down, eraser) }
        motion(MotionEvent.ACTION_UP, end, down, eraser)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long, eraser: Boolean) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = if (eraser) MotionEvent.TOOL_TYPE_ERASER else MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
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
