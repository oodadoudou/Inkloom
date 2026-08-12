package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterPaintCommitTest {
    @Test fun penPaintsTheActiveRasterSurfaceWithoutAddingAVectorStroke() {
        val surface = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        var rasterCommits = 0
        var vectorCommits = 0
        var committed: Bitmap? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 42f
            inkColor = Color.MAGENTA
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { _, bitmap -> rasterCommits++; committed = bitmap }
            onStrokeCommitted = { vectorCommits++ }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f + index % 3, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertEquals("One raster paint gesture is one durable surface commit", 1, rasterCommits)
        assertEquals("Raster paint must not leak a vector stroke into the document", 0, vectorCommits)
        assertTrue(requireNotNull(committed).hasAlphaInRegion(80, 270, 260, 340))
        committed?.recycle(); surface.recycle()
    }

    @Test fun rasterPaintCommitStaysWithinOneFrameForAStandardPenStroke() {
        val surface = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 28f
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { _, bitmap -> bitmap.recycle() }
        }
        send(view, MotionEvent.ACTION_DOWN, 120f, 300f, 0L)
        repeat(72) { index -> send(view, MotionEvent.ACTION_MOVE, 125f + index * 7f, 300f + index % 5, 5L + index) }
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        send(view, MotionEvent.ACTION_UP, 635f, 302f, 90L)
        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_standard_pen_commit_ms=$elapsedMs")
        assertTrue("Raster pen-up commit was ${elapsedMs}ms", elapsedMs < 16.7f)
        surface.recycle()
    }

    private fun createCanvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                fingerDrawingEnabled = true
                block()
            }
        }
        return result
    }

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .1f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }

    private fun Bitmap.hasAlphaInRegion(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(height)) for (x in left.coerceAtLeast(0) until right.coerceAtMost(width)) {
            if (Color.alpha(getPixel(x, y)) > 0) return true
        }
        return false
    }
}
