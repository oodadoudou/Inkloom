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
import app.inkloom.document.RulerShape
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import kotlin.math.abs

/** Layer rulers: placed with the ruler tool, strokes snap to them, and placing is undoable. */
class RulerWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var rulerMode = "STRAIGHT"
    private val fixture = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            rulerMode = description.getAnnotation(RulerMode::class.java)?.value ?: "STRAIGHT"
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).putString("rulerToolMode", rulerMode)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class RulerMode(val value: String)

    @Test fun strokesSnapToALinearRulerOnTheLayer() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        fun at(x: Float, y: Float) = Offset(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        compose.onNodeWithContentDescription("Ruler").performClick()
        compose.waitForIdle()
        stroke(at(.1f, .5f), at(.3f, .5f))
        compose.waitUntil(5_000) { activeRulers(view).size == 1 }
        val ruler = activeRulers(view).single() as RulerShape.Straight
        assertEquals(.5f, ruler.y1, .01f); assertEquals(.5f, ruler.y2, .01f)

        compose.onAllNodesWithContentDescription("Brush").onFirst().performClick()
        compose.waitForIdle()
        // Starts just below the ruler and wanders away from it; the stroke follows the ruler.
        stroke(at(.12f, .505f), at(.4f, .56f))
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() && view.v5LastCommittedNormalizedPoints().isNotEmpty() }
        val points = view.v5LastCommittedNormalizedPoints()
        assertTrue("every point is on the ruler: ${points.map { it.second }}", points.all { abs(it.second - .5f) < .004f })
        assertTrue("the stroke still travels along it", points.maxOf { it.first } > .35f)

        // A stroke far from the ruler is not snapped.
        stroke(at(.12f, .3f), at(.4f, .36f))
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() }
        assertTrue(view.v5LastCommittedNormalizedPoints().last().second > .33f)

        repeat(3) { compose.onAllNodesWithContentDescription("Undo").onFirst().performClick(); compose.waitForIdle() }
        compose.waitUntil(5_000) { activeRulers(view).isEmpty() }
        assertNull(model().currentPage()!!.layers.first { it.id == view.activeLayerId }.rulers)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(5_000) { activeRulers(view).size == 1 }
    }

    @RulerMode("ELLIPSE")
    @Test fun ellipseRulersAreClosedAndStrokesFollowTheirCurve() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        fun at(x: Float, y: Float) = Offset(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        compose.onNodeWithContentDescription("Ruler").performClick()
        compose.waitForIdle()
        stroke(at(.1f, .3f), at(.4f, .6f))
        compose.waitUntil(5_000) { activeRulers(view).size == 1 }
        val ellipse = activeRulers(view).single() as RulerShape.Path
        assertTrue(ellipse.closed)
        compose.onAllNodesWithContentDescription("Brush").onFirst().performClick()
        compose.waitForIdle()
        // Along the top of the ellipse (centre .25,.45; radii .15 × .15 of the page).
        stroke(at(.25f, .31f), at(.35f, .35f))
        compose.waitUntil(10_000) { !view.hasPendingPaintPublicationForMerge() && view.v5LastCommittedNormalizedPoints().isNotEmpty() }
        val w = bounds.width(); val h = bounds.height()
        view.v5LastCommittedNormalizedPoints().forEach { (x, y) ->
            val nx = (x - .25f) / .15f; val ny = (y - .45f) / .15f
            assertEquals("($x, $y) lies on the ellipse", 1f, nx * nx + ny * ny, .06f)
        }
        assertTrue(w > 0 && h > 0)
    }

    private fun activeRulers(view: InkCanvasView): List<RulerShape> =
        model().currentPage()?.layers?.firstOrNull { it.id == view.activeLayerId }?.rulers?.shapes.orEmpty()

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
