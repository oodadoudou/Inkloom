package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.ProcreateBrushBlend
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encodes the front-buffer cancel/clear/commit/replay call graph as an executable contract.
 *
 * The full graph is documented in tasks/FRONT_BUFFER_CALL_GRAPH.md; these tests pin the parts
 * that determine whether the landing pipeline can flicker:
 * - DOWN begins with a cancel of any stale preview, then the first frame renders the contact dab.
 * - MOVE frames grow the preview monotonically through render().
 * - UP renders the exact final packet then commits exactly once; the committed stroke equals the
 *   final rendered stroke (no divergent replay).
 * - ACTION_CANCEL discards the preview with no commit.
 * - The view-preview path (masks / non-Normal blend) cancels the front buffer instead of
 *   rendering into it, so the main canvas owns the preview.
 * - A cancelled stroke never commits, and the next stroke restarts the render sequence fresh.
 */
@RunWith(AndroidJUnit4::class)
class FrontBufferLifecycleTest {
    @Test
    fun downMoveUpRendersGrowingPreviewThenCommitsTheFinalPacket() {
        val view = newView()
        val surface = RecordingActiveSurface()
        view.activeStrokeSurface = surface
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), null, 20f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 400f, 1L))
            assertTrue("DOWN must cancel any stale preview before the first frame", surface.events.any { it == "cancel" })
            view.flushActiveStrokeFrame()
            assertEquals("First front-buffer frame renders the contact dab", 1, surface.rendered.last().first().points.size)
            val afterDown = surface.events.size

            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 500f, 400f, 5L))
            view.flushActiveStrokeFrame()
            val previewPoints = surface.rendered.last().first().points.size
            assertTrue("MOVE frames must grow the preview through render()", previewPoints > 1)
            assertTrue(surface.events.drop(afterDown).all { it == "render" })
            assertTrue("No commit may happen before pen-up", surface.committed.isEmpty())

            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 900f, 400f, 9L))
            assertTrue("UP must render the exact final packet before committing", surface.events.lastOrNull { it == "render" } != null)
            assertEquals("UP must commit exactly once", 1, surface.committed.size)
            assertEquals("Committed stroke must equal the final rendered packet", surface.rendered.last().first(), surface.committed.last().first())
            assertTrue("No trailing cancel after a committed stroke", surface.events.lastOrNull { it == "cancel" }?.let { surface.events.lastIndexOf(it) < surface.events.indexOfLast { e -> e == "commit" } } ?: true)
        }
    }

    @Test
    fun actionCancelDiscardsThePreviewWithoutCommitting() {
        val view = newView()
        val surface = RecordingActiveSurface()
        view.activeStrokeSurface = surface
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), null, 20f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 400f, 1L))
            view.flushActiveStrokeFrame()
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 500f, 400f, 5L))
            view.flushActiveStrokeFrame()
            val rendersBefore = surface.rendered.size

            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_CANCEL, 500f, 400f, 7L))
            assertTrue("ACTION_CANCEL must clear the transient surface", surface.events.any { it == "cancel" })
            assertEquals("A cancelled stroke must never commit", 0, surface.committed.size)
            assertTrue("No renders may follow the cancellation", surface.rendered.size <= rendersBefore)
        }
    }

    @Test
    fun viewPreviewPathCancelsTheFrontBufferInsteadOfRenderingIntoIt() {
        val view = newView()
        val surface = RecordingActiveSurface()
        view.activeStrokeSurface = surface
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), null, 20f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.procreateBrushBlendMode = ProcreateBrushBlend.MULTIPLY
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 400f, 1L))
            view.flushActiveStrokeFrame()
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 500f, 400f, 5L))
            view.flushActiveStrokeFrame()
            assertTrue("The view-preview path must cancel the front buffer each frame", surface.events.any { it == "cancel" })
            assertTrue("The view-preview path must not render into the front buffer", surface.rendered.isEmpty())
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 900f, 400f, 9L))
            assertEquals("The view-preview path commits through the main canvas, never the front buffer", 0, surface.committed.size)
        }
    }

    @Test
    fun cancelledStrokeThenNewStrokeRestartsTheRenderSequenceFresh() {
        val view = newView()
        val surface = RecordingActiveSurface()
        view.activeStrokeSurface = surface
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), null, 20f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 400f, 1L))
            view.flushActiveStrokeFrame()
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_CANCEL, 250f, 400f, 2L))
            val rendersAfterCancel = surface.rendered.size

            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 300f, 500f, 10L))
            view.flushActiveStrokeFrame()
            assertEquals("A new stroke after a cancel must restart from the contact dab", 1, surface.rendered.last().first().points.size)
            assertTrue("The new stroke must render after the previous cancel", surface.rendered.size > rendersAfterCancel)
        }
    }

    private fun newView(): InkCanvasView {
        val w = 1600
        val h = 1200
        return InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply { layout(0, 0, w, h) }
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .75f; size = .05f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }

    private class RecordingActiveSurface : ActiveStrokeCompositor {
        val events = mutableListOf<String>()
        val rendered = mutableListOf<List<InkStroke>>()
        val committed = mutableListOf<List<InkStroke>>()
        override fun render(strokes: List<InkStroke>) {
            events += "render"
            rendered += strokes.map { it.copy(points = it.points.toList()) }
        }
        override fun commit(strokes: List<InkStroke>) {
            events += "commit"
            committed += strokes.map { it.copy(points = it.points.toList()) }
        }
        override fun cancel() {
            events += "cancel"
        }
        override val hasActiveInk: Boolean
            get() = rendered.isNotEmpty() && events.lastOrNull() == "render"
    }
}
