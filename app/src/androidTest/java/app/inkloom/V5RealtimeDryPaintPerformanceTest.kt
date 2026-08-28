package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V5RealtimeDryPaintPerformanceTest {
    @Test
    fun maximumRealtimeSmoothingStillKeepsVisibleInkAtThePhysicalTip() {
        val surface = PixelTileSurface(2048, 1536)
        val display = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1024, 768)
                tool = EditorTool.PEN
                activeLayerId = "paint"
                installV5TestInk(size = 12f, smoothing = 1f)
                fingerDrawingEnabled = true
                setRasterLayerSurfaces(mapOf("paint" to surface))
            }
        }

        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, 80f, 260f, downTime, downTime)
        dispatch(view, MotionEvent.ACTION_MOVE, 900f, 260f, downTime, downTime + 4L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            display.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(display))
        }

        var visibleAtTip = false
        for (y in 252..268) for (x in 892..904) {
            if (Color.alpha(display.getPixel(x, y)) > 0) visibleAtTip = true
        }
        assertTrue("Explicit smoothing may soften the path but must not visibly trail the stylus", visibleAtTip)
        dispatch(view, MotionEvent.ACTION_UP, 900f, 260f, downTime, downTime + 8L)
        display.recycle()
    }

    @Test
    fun threeHundredHertzInputRemainsVisibleBeforeUpAndMeetsTheFrameGate() {
        val surface = PixelTileSurface(2048, 1536)
        val display = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        var commits = 0
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1024, 768)
                tool = EditorTool.PEN
                activeLayerId = "paint"
                installV5TestInk(size = 12f)
                fingerDrawingEnabled = true
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { commits++ }
            }
        }

        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, 80f, 260f, downTime, downTime)
        val frameTimesMs = ArrayList<Float>(240)
        repeat(240) { index ->
            val x = 84f + index * 3.4f
            val y = 260f + kotlin.math.sin(index * .12f) * 16f
            val started = SystemClock.elapsedRealtimeNanos()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                dispatchDirect(view, MotionEvent.ACTION_MOVE, x, y, downTime, downTime + 1L + index * 3L)
                display.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(display))
            }
            frameTimesMs += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }

        assertEquals("The committed surface is immutable until publication", 0, Color.alpha(surface.pixelAt(900, 520)))
        assertTrue("The private active delta must be visible before UP", displayHasInk(display))
        val previewPixels = IntArray(display.width * display.height)
        display.getPixels(previewPixels, 0, display.width, 0, 0, display.width, display.height)

        val lastX = 84f + 239 * 3.4f
        val lastY = 260f + kotlin.math.sin(239 * .12f) * 16f
        dispatch(view, MotionEvent.ACTION_UP, lastX, lastY, downTime, downTime + 730L)
        display.eraseColor(Color.TRANSPARENT)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(display)) }
        val committedPixels = IntArray(display.width * display.height)
        display.getPixels(committedPixels, 0, display.width, 0, 0, display.width, display.height)

        val ordered = frameTimesMs.sorted()
        val p95 = ordered[(ordered.size * .95f).toInt().coerceIn(0, ordered.lastIndex)]
        val p99 = ordered[(ordered.size * .99f).toInt().coerceIn(0, ordered.lastIndex)]
        android.util.Log.i("InkloomPerf", "v5_dry_input_present_p95_ms=$p95 p99_ms=$p99")
        assertTrue("V5 input-to-present p95 was ${p95}ms", p95 < 16.7f)
        assertTrue("V5 input-to-present p99 was ${p99}ms", p99 < 33.4f)
        assertEquals("One physical gesture must publish exactly once", 1, commits)
        assertTrue("ACTION_UP must not replace the visible render product", previewPixels.contentEquals(committedPixels))
        assertTrue("The published document must contain the stroke", surface.tileCount > 0)
        display.recycle()
    }

    private fun dispatch(view: InkCanvasView, action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatchDirect(view, action, x, y, downTime, eventTime)
        }
    }

    private fun dispatchDirect(view: InkCanvasView, action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = .8f
                size = .04f
            }),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun displayHasInk(bitmap: Bitmap): Boolean {
        for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return true
        }
        return false
    }
}
