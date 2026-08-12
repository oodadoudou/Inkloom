package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.InkStroke
import app.inkloom.document.LayerIds
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasProfessionalToolsTest {
    @Test fun liquifyCommitsAReversibleExistingStrokeEdit() {
        var replacement: InkStroke? = null
        val original = line("liquify", listOf(StrokePoint(.45f, .5f), StrokePoint(.55f, .5f)))
        val view = canvas(EditorTool.LIQUIFY, original) {
            baseWidth = 500f
            onStrokesReplaced = { _, changed, _ -> replacement = changed.firstOrNull() }
        }
        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 10L)
        send(view, MotionEvent.ACTION_UP, 650f, 500f, 20L)

        assertNotEquals(original.points, requireNotNull(replacement).points)
    }

    @Test fun stationaryLiquifyModesApplyAfterLongPress() {
        var replacement: InkStroke? = null
        val original = line("liquify-hold", listOf(StrokePoint(.45f, .5f), StrokePoint(.55f, .5f)))
        val view = canvas(EditorTool.LIQUIFY, original) {
            baseWidth = 500f
            liquifyMode = app.inkloom.document.LiquifyMode.EXPAND
            onStrokesReplaced = { _, changed, _ -> replacement = changed.firstOrNull() }
        }

        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.runLiquifyLongPressFrameForTest() }
        send(view, MotionEvent.ACTION_UP, 500f, 500f, 100L)

        assertNotEquals("Long-press Expand must change geometry without a drag", original.points, requireNotNull(replacement).points)
    }

    @Test fun correctLineSmoothsAndCommitsTheTargetLine() {
        var replacement: InkStroke? = null
        val original = line("rough", listOf(
            StrokePoint(.2f, .5f), StrokePoint(.35f, .62f), StrokePoint(.5f, .38f), StrokePoint(.65f, .61f), StrokePoint(.8f, .5f),
        ))
        val view = canvas(EditorTool.CORRECT_LINE, original) {
            correctLineMode = CorrectLineMode.SMOOTH
            onStrokesReplaced = { _, changed, _ -> replacement = changed.firstOrNull() }
        }
        send(view, MotionEvent.ACTION_DOWN, 500f, 380f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 700f, 380f, 10L)
        send(view, MotionEvent.ACTION_UP, 700f, 380f, 20L)

        assertNotEquals(original.points, requireNotNull(replacement).points)
    }

    @Test fun correctLineSimplifiesVectorPointsAndAdjustsWholeStrokeWidth() {
        val original = line("rough", listOf(
            StrokePoint(.2f, .5f), StrokePoint(.35f, .51f), StrokePoint(.5f, .49f), StrokePoint(.65f, .51f), StrokePoint(.8f, .5f),
        ))
        var simplified: InkStroke? = null
        val simplifyView = canvas(EditorTool.CORRECT_LINE, original) {
            correctLineMode = CorrectLineMode.SIMPLIFY
            correctLineAmount = 1f
            onStrokesReplaced = { _, changed, _ -> simplified = changed.firstOrNull() }
        }
        send(simplifyView, MotionEvent.ACTION_DOWN, 500f, 490f, 0L)
        send(simplifyView, MotionEvent.ACTION_UP, 500f, 490f, 20L)
        assertTrue(requireNotNull(simplified).points.size < original.points.size)

        var widened: InkStroke? = null
        val widthView = canvas(EditorTool.CORRECT_LINE, original) {
            correctLineMode = CorrectLineMode.ADJUST_WIDTH
            correctLineAmount = 1f
            onStrokesReplaced = { _, changed, _ -> widened = changed.firstOrNull() }
        }
        send(widthView, MotionEvent.ACTION_DOWN, 500f, 490f, 0L)
        send(widthView, MotionEvent.ACTION_UP, 500f, 490f, 20L)
        assertEquals(original.width * 2f, requireNotNull(widened).width)
    }

    @Test fun controlPointModeMovesTheNearestVectorPoint() {
        var replacement: InkStroke? = null
        val original = line("points", listOf(StrokePoint(.2f, .5f), StrokePoint(.5f, .5f), StrokePoint(.8f, .5f)))
        val view = canvas(EditorTool.CORRECT_LINE, original) {
            correctLineMode = CorrectLineMode.CONTROL_POINTS
            onStrokesReplaced = { _, changed, _ -> replacement = changed.firstOrNull() }
        }

        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 600f, 700f, 10L)
        send(view, MotionEvent.ACTION_UP, 600f, 700f, 20L)

        assertEquals(.6f, requireNotNull(replacement).points[1].x, .001f)
        assertEquals(.7f, requireNotNull(replacement).points[1].y, .001f)
    }

    @Test fun connectModeJoinsNearbyVectorEndpointsIntoOneStroke() {
        var replacements = emptyList<InkStroke>()
        val first = line("first", listOf(StrokePoint(.2f, .5f), StrokePoint(.48f, .5f)))
        val second = line("second", listOf(StrokePoint(.52f, .5f), StrokePoint(.8f, .5f)))
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1_000, 1_000)
                tool = EditorTool.CORRECT_LINE
                correctLineMode = CorrectLineMode.CONNECT
                fingerDrawingEnabled = true
                activeLayerId = LayerIds.INK
                setDocumentStrokes(listOf(first, second), "connect-lines")
                onStrokesReplaced = { _, changed, _ -> replacements = changed }
            }
        }

        send(view, MotionEvent.ACTION_DOWN, 480f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 520f, 500f, 10L)
        send(view, MotionEvent.ACTION_UP, 520f, 500f, 20L)

        assertEquals(1, replacements.size)
        assertTrue(replacements.single().points.size >= 3)
    }

    @Test fun invertAndFreeTransformOperateOnEditableSelection() {
        val view = canvas(EditorTool.SELECT, line("one", listOf(StrokePoint(.3f, .3f), StrokePoint(.4f, .4f))))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(view.selectAllEditableStrokes())
            assertFalse(view.invertSelection())
            assertTrue(view.invertSelection())
            assertTrue(view.enterFreeTransform())
            assertTrue(view.clearSelection())
        }
    }

    private fun canvas(tool: EditorTool, stroke: InkStroke, block: InkCanvasView.() -> Unit = {}): InkCanvasView {
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1_000, 1_000)
                this.tool = tool
                fingerDrawingEnabled = true
                activeLayerId = LayerIds.INK
                setDocumentStrokes(listOf(stroke), "professional-tools")
                block()
            }
        }
        return view
    }

    private fun line(id: String, points: List<StrokePoint>) = InkStroke(id = id, points = points, width = 18f, colorArgb = 0xFF111111, layerId = LayerIds.INK)

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }
}
