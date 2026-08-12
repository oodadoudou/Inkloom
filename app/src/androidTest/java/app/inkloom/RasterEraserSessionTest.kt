package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v2.render.RasterEraserSession
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterEraserSessionTest {
    @Test fun softEraserPreservesPartialAlphaAndSelectionIsolation() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val selection = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 50) for (x in 0 until 160) selection.setPixel(x, y, Color.WHITE)
        RasterEraserSession(bitmap, selection).use { session ->
            session.eraseSegment(30f, 50f, 130f, 50f, 24f, .65f, .35f)
        }
        assertTrue("Soft eraser creates partial alpha", Color.alpha(bitmap.getPixel(80, 38)) in 1..254)
        assertEquals("Selection excludes the lower half", 255, Color.alpha(bitmap.getPixel(80, 62)))
        selection.recycle(); bitmap.recycle()
    }

    @Test fun rasterEraserGestureCommitsOneSurfaceAndKeepsOtherLayersOut() {
        val surface = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        var commits = 0
        var committed: Bitmap? = null
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400); fingerDrawingEnabled = true; tool = EditorTool.ERASER
                activeLayerId = "pixels"; eraserWidth = 180f; eraserHardness = 1f; eraserOpacity = 1f
                setRasterLayerBitmaps(mapOf("pixels" to surface))
                onRasterLayerCommitted = { layer, bitmap -> assertEquals("pixels", layer); commits++; committed = bitmap }
            }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
        send(view, MotionEvent.ACTION_UP, 320f, 200f, 40L)
        assertEquals(1, commits)
        assertEquals(0, Color.alpha(requireNotNull(committed).getPixel(200, 200)))
        committed?.recycle(); surface.recycle()
    }

    @Test fun rasterEraserSegmentMeetsRealtimeBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val times = ArrayList<Long>(96)
        RasterEraserSession(bitmap).use { session ->
            var x = 180f
            repeat(96) { index ->
                val next = x + 4f
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                session.eraseSegment(x, 450f, next, 450f + index % 5, 72f, .55f, .5f)
                times += android.os.SystemClock.elapsedRealtimeNanos() - started
                x = next
            }
        }
        val sorted = times.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_eraser_local_p95_ms=$p95Ms")
        assertTrue("Raster Eraser p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
        bitmap.recycle()
    }

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }
}
