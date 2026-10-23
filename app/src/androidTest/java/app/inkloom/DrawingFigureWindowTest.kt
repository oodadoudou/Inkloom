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

/** LM-36: a posed 3D drawing figure on its own layer, edited, undone and rasterized. */
class DrawingFigureWindowTest {
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

    @Test fun figureIsPosedEditedAndRasterized() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add vector, fill, gradient or tone layer").performClick()
        compose.onNodeWithText("3D drawing figure…").performClick()
        compose.onNodeWithText("Pose").performClick()
        compose.onNodeWithText("T-pose").performClick()
        compose.onNodeWithText("Perspective ruler from the camera").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(20_000) { figureLayer() != null }
        val layer = requireNotNull(figureLayer())
        assertEquals(90f, layer.figure!!.turn(app.inkloom.document.FigureJoint.LEFT_UPPER_ARM).z, .01f)
        assertNotNull(layer.rasterTileSurface)
        assertNotNull("the camera's perspective ruler", layer.rulers?.perspective)
        compose.waitUntil(5_000) { view.activeLayerId == layer.id }

        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(layer.name)].performClick()
        compose.onNodeWithText("Edit layer settings").performClick()
        compose.onNodeWithText("Outline").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(20_000) { figureLayer()?.figure?.style == app.inkloom.document.FigureStyle.OUTLINE }
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(10_000) { figureLayer()?.figure?.style == app.inkloom.document.FigureStyle.SHADED }

        compose.onAllNodesWithContentDescription("Layer actions")[layerRow(layer.name)].performClick()
        compose.onNodeWithText("Rasterize").performClick()
        compose.waitUntil(10_000) { model().currentPage()!!.layers.first { it.id == layer.id }.figure == null }
    }

    private fun figureLayer() = model().currentPage()?.layers?.firstOrNull { it.figure != null }

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
