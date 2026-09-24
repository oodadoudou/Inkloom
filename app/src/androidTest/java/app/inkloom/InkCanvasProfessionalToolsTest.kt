package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Professional tool integration over V5 raster state only. */
@RunWith(AndroidJUnit4::class)
class InkCanvasProfessionalToolsTest {
    @Test fun momentumAddsBoundedPenUpTravelAndKeepsOneUndoStep() {
        fun result(momentum: Float): IntArray {
            val (view, surface) = rasterLiquifyCanvas { liquifyMomentum = momentum }
            val bounds = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
            val before = surface.snapshot().copyPremultipliedPixels(bounds)
            send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
            send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 10L)
            send(view, MotionEvent.ACTION_UP, 650f, 500f, 20L)
            val after = surface.snapshot().copyPremultipliedPixels(bounds)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
            assertEquals(before.toList(), surface.snapshot().copyPremultipliedPixels(bounds).toList())
            return after
        }
        assertFalse(result(0f).contentEquals(result(1f)))
    }

    @Test fun distinctPenContactsReconstructAndCancelPreservesEarlierEdits() {
        val (view, surface) = rasterLiquifyCanvas()
        val bounds = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val original = surface.snapshot().copyPremultipliedPixels(bounds)
        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 10L)
        send(view, MotionEvent.ACTION_UP, 650f, 500f, 20L)
        val pushed = surface.snapshot().copyPremultipliedPixels(bounds)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.liquifyMode = app.inkloom.document.LiquifyMode.RECONSTRUCT }
        send(view, MotionEvent.ACTION_DOWN, 650f, 500f, 30L)
        repeat(6) { send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 40L + it) }
        send(view, MotionEvent.ACTION_CANCEL, 650f, 500f, 50L)
        assertEquals(pushed.toList(), surface.snapshot().copyPremultipliedPixels(bounds).toList())
        send(view, MotionEvent.ACTION_DOWN, 650f, 500f, 60L)
        repeat(6) { send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 70L + it) }
        send(view, MotionEvent.ACTION_UP, 650f, 500f, 80L)
        val reconstructed = surface.snapshot().copyPremultipliedPixels(bounds)
        assertFalse(pushed.contentEquals(reconstructed))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(pushed.toList(), surface.snapshot().copyPremultipliedPixels(bounds).toList())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(original.toList(), surface.snapshot().copyPremultipliedPixels(bounds).toList())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo(); view.redo() }
        assertEquals(reconstructed.toList(), surface.snapshot().copyPremultipliedPixels(bounds).toList())
    }

    @Test fun liquifyCommitsOneReversibleRasterTransaction() {
        val (view, surface) = rasterLiquifyCanvas()
        val wholePage = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val before = surface.snapshot().copyPremultipliedPixels(wholePage)
        var commits = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onRasterLayerCommitted = { commits++ } }

        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 10L)
        send(view, MotionEvent.ACTION_UP, 650f, 500f, 20L)

        assertEquals(1, commits)
        assertFalse(before.contentEquals(surface.snapshot().copyPremultipliedPixels(wholePage)))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(before.toList(), surface.snapshot().copyPremultipliedPixels(wholePage).toList())
    }

    @Test fun liquifyChangesOnlyTheActiveRasterSurface() {
        val seed = seededBitmap()
        val active = PixelTileBitmapAdapter.import(seed)
        val inactive = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        val inactiveRevision = inactive.revision
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1_000, 1_000)
                tool = EditorTool.LIQUIFY
                fingerDrawingEnabled = true
                activeLayerId = "paint"
                baseWidth = 500f
                setRasterLayerSurfaces(mapOf("paint" to active, "notes" to inactive))
                onRasterLayerCommitted = { }
            }
        }

        send(view, MotionEvent.ACTION_DOWN, 300f, 500f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 650f, 500f, 20L)
        send(view, MotionEvent.ACTION_UP, 650f, 500f, 40L)

        assertFalse(active.revision == 1L)
        assertEquals(inactiveRevision, inactive.revision)
    }

    @Test fun stationaryExpandLiquifyAppliesAfterLongPress() {
        val (view, surface) = rasterLiquifyCanvas { liquifyMode = app.inkloom.document.LiquifyMode.EXPAND }
        val wholePage = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val before = surface.snapshot().copyPremultipliedPixels(wholePage)

        send(view, MotionEvent.ACTION_DOWN, 500f, 500f, 0L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.runLiquifyLongPressFrameForTest() }
        send(view, MotionEvent.ACTION_UP, 500f, 500f, 100L)

        assertFalse(before.contentEquals(surface.snapshot().copyPremultipliedPixels(wholePage)))
    }

    private fun rasterLiquifyCanvas(block: InkCanvasView.() -> Unit = {}): Pair<InkCanvasView, app.inkloom.engine.v5.surface.PixelTileSurface> {
        val seed = seededBitmap()
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1_000, 1_000)
                tool = EditorTool.LIQUIFY
                fingerDrawingEnabled = true
                activeLayerId = "paint"
                baseWidth = 500f
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { }
                block()
            }
        }
        return view to surface
    }

    private fun seededBitmap() = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
        for (y in 65 until 135) for (x in 35 until 100) setPixel(x, y, Color.RED)
    }

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }
}
