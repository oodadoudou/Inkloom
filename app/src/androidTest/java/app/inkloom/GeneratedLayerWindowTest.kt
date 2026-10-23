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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Fill, gradient and tone layers: create, refuse brushes, edit, undo and rasterize. */
class GeneratedLayerWindowTest {
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

    @Test fun toneLayerIsCreatedEditedUndoneAndRasterized() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("Tone layer").performClick()
        compose.onNodeWithText("New tone layer").assertExists()
        compose.waitForIdle()
        compose.onNodeWithText("Create").performClick()
        try { compose.waitUntil(10_000) { model().currentPage()?.layers?.any { it.generator is app.inkloom.document.LayerGenerator.Tone } == true } }
        catch (e: Throwable) { instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/gen-fail.png").close(); SystemClock.sleep(800)
            throw AssertionError("layers=${model().currentPage()?.layers?.map { it.name }} pending=${view.selectionLayerTransferPending} paint=${view.hasPendingPaintPublicationForMerge()}", e) }
        val tone = model().currentPage()!!.layers.first { it.generator != null }
        compose.waitUntil(5_000) { view.activeLayerId == tone.id }
        val first = requireNotNull(tone.rasterTileSurface)

        // Brushes are refused: the tone pixels stay exactly as generated.
        val bounds = pageScreenBounds(view)
        compose.waitUntil(10_000) { view.activeLayerId == tone.id && pixels(view) != 0 }
        val beforeStroke = pixels(view)
        // The left part of the page, clear of the docked layer panel.
        stroke(Offset(bounds.left + bounds.width() * .08f, bounds.top + bounds.height() * .5f),
            Offset(bounds.left + bounds.width() * .3f, bounds.top + bounds.height() * .5f))
        SystemClock.sleep(800); compose.waitForIdle()
        assertEquals(first, model().currentPage()!!.layers.first { it.id == tone.id }.rasterTileSurface)
        val generated = pixels(view)
        assertEquals("brushes do not paint on a tone layer", beforeStroke, generated)

        // Edit the density from the layer's menu; the pixels are regenerated.
        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(tone.name)].performClick()
        compose.onNodeWithText("Edit layer settings").performClick()
        compose.onNodeWithContentDescription("Density value").performTextReplacement("70")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(10_000) { (model().currentPage()?.layers?.first { it.id == tone.id }?.generator as? app.inkloom.document.LayerGenerator.Tone)?.density == .7f }
        assertNotEquals(first, model().currentPage()!!.layers.first { it.id == tone.id }.rasterTileSurface)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.first { it.id == tone.id }?.rasterTileSurface == first }

        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(tone.name)].performClick()
        compose.onNodeWithText("Rasterize").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.first { it.id == tone.id }?.generator == null }
        // Let the canvas receive the rasterized layer list before the pen touches it.
        compose.waitForIdle(); SystemClock.sleep(500); compose.waitForIdle()
        // The left part of the page, clear of the docked layer panel.
        stroke(Offset(bounds.left + bounds.width() * .08f, bounds.top + bounds.height() * .5f),
            Offset(bounds.left + bounds.width() * .3f, bounds.top + bounds.height() * .5f))
        compose.waitUntil(10_000) { view.activeLayerId == tone.id && pixels(view) != generated }
    }

    /** Fingerprint of the active layer's live pixels, as the canvas shows them. */
    private fun pixels(view: InkCanvasView): Int {
        val snapshot = view.activeRasterSnapshotForTest() ?: return 0
        return snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight)).contentHashCode()
    }

    @Test fun convertLayerFromTheLayerMenuMakesItMonochrome() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        stroke(Offset(bounds.left + bounds.width() * .1f, bounds.top + bounds.height() * .4f),
            Offset(bounds.left + bounds.width() * .3f, bounds.top + bounds.height() * .4f))
        compose.waitUntil(10_000) { pixels(view) != 0 && !view.hasPendingPaintPublicationForMerge() }
        val ink = pixels(view)
        val layerName = model().currentPage()!!.layers.first { it.id == view.activeLayerId }.name
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(layerName)].performClick()
        compose.onNodeWithText("Convert layer…").performClick()
        compose.onNodeWithText("Expression color").assertExists()
        compose.onNodeWithText("Monochrome").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) { !view.selectionLayerTransferPending && pixels(view) != ink }
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        val colours = (0 until snapshot.logicalWidth step 3).flatMap { x -> (0 until snapshot.logicalHeight step 7).map { y -> snapshot.pixelAt(x, y) } }.toSet()
        assertTrue("only black ink or nothing: $colours", colours.all { it == 0 || it == 0xFF000000.toInt() })
    }

    /** Index of a layer row among the rows' action buttons (rows are listed top layer first). */
    private fun layerRow(name: String): Int {
        val rows = compose.onAllNodesWithContentDescription("Layer actions").fetchSemanticsNodes()
        val names = compose.onAllNodesWithText(name).fetchSemanticsNodes()
        val target = names.first().boundsInRoot.center.y
        return rows.indices.minBy { kotlin.math.abs(rows[it].boundsInRoot.center.y - target) }
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

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
