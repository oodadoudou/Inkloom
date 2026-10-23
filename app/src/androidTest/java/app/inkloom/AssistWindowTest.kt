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
import app.inkloom.document.LayerBlendMode
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Shading assist and colorize add their result as a new layer in one undo step. */
class AssistWindowTest {
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

    @Test fun shadingAssistAddsAMultiplyLayer() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("Fill layer").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.any { it.generator != null } == true }
        val before = model().currentPage()!!.layers.size
        openAction("Shading assist…")
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(20_000) { model().currentPage()!!.layers.size == before + 1 }
        val shading = model().currentPage()!!.layers.first { it.name == "Shading" }
        assertEquals(LayerBlendMode.MULTIPLY, shading.blendMode)
        assertNotNull(shading.rasterTileSurface)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(5_000) { model().currentPage()!!.layers.size == before }
    }

    @Test fun colorizeFillsTheLineArtFromTheHintLayer() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        fun at(x: Float, y: Float) = Offset(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        // A closed box of line art.
        stroke(at(.2f, .3f), at(.5f, .3f)); stroke(at(.5f, .3f), at(.5f, .6f)); stroke(at(.5f, .6f), at(.2f, .6f)); stroke(at(.2f, .6f), at(.2f, .3f))
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() }
        val lineArt = model().currentPage()!!.layers.first { it.id == view.activeLayerId }
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add layer").performClick()
        compose.waitUntil(5_000) { view.activeLayerId != lineArt.id }
        val hintLayer = view.activeLayerId
        // Let the new layer's surface reach the canvas before the pen touches it.
        compose.waitForIdle(); SystemClock.sleep(800); compose.waitForIdle()
        stroke(at(.3f, .45f), at(.38f, .5f))
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() && view.activeRasterSnapshotForTest()?.tileCount?.let { it > 0 } == true }
        compose.onAllNodesWithContentDescription("Select layer ${lineArt.name}").onFirst().performClick()
        compose.waitUntil(5_000) { view.activeLayerId == lineArt.id }
        val before = model().currentPage()!!.layers.size
        openAction("Colorize…")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(20_000) { model().currentPage()!!.layers.size == before + 1 }
        val layers = model().currentPage()!!.layers
        val colors = layers.first { it.name == "Colors" }
        assertTrue("the colours go under the line art", layers.indexOf(colors) < layers.indexOfFirst { it.id == lineArt.id })
        assertTrue("the hint layer is hidden", !layers.first { it.id == hintLayer }.visible)
    }

    private fun openAction(label: String) {
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
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
