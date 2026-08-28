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
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmudgeToolTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 200, 200); block() }
        }
        return result
    }

    private fun event(time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val e = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(e) } } finally { e.recycle() }
    }

    @Test fun draggingBlendPublishesOneRasterPigmentTransaction() {
        val seed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            for (y in 70 until 130) for (x in 35 until 90) setPixel(x, y, Color.RED)
        }
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var rasterCommits = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 400f
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { rasterCommits++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 100f, 100f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 60L, MotionEvent.ACTION_UP, 140f, 100f)
        assertEquals(1, rasterCommits)
        assertTrue("Blend must transport existing pigment", Color.alpha(surface.pixelAt(118, 100)) > 0)
    }

    @Test fun blendOnlyModifiesTheActiveLayer() {
        val activeSeed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            for (y in 70 until 130) for (x in 35 until 90) setPixel(x, y, Color.RED)
        }
        val inactiveSeed = activeSeed.copy(Bitmap.Config.ARGB_8888, false)
        val active = PixelTileBitmapAdapter.import(activeSeed)
        val inactive = PixelTileBitmapAdapter.import(inactiveSeed)
        activeSeed.recycle(); inactiveSeed.recycle()
        val inactiveRevision = inactive.revision
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 400f
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to active, "notes" to inactive))
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 140f, 100f)

        assertTrue(active.revision > 1L)
        assertEquals(inactiveRevision, inactive.revision)
    }

    @Test fun rasterLayerBlendCommitsOnePixelSurface() {
        val seed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 70 until 130) for (x in 30 until 80) seed.setPixel(x, y, Color.RED)
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var rasterCommits = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 320f
            activeLayerId = "raster"
            setRasterLayerSurfaces(mapOf("raster" to surface))
            onRasterLayerCommitted = { patch ->
                assertEquals("raster", patch.layerId)
                rasterCommits++
            }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 105f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 105f, 100f)

        assertEquals("One blend gesture creates one raster transaction", 1, rasterCommits)
        assertTrue(Color.alpha(surface.pixelAt(105, 100)) > 0)
    }

    @Test fun blendingBlankPaperDoesNotCreatePigment() {
        var committed = 0
        val surface = PixelTileSurface(200, 200)
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 20f, 100f)
        for (step in 1..16) send(view, step * 10L, MotionEvent.ACTION_MOVE, 20f + step * 10f, 100f)
        send(view, 180L, MotionEvent.ACTION_UP, 180f, 100f)
        assertEquals("A blend tool cannot add pigment to blank paper", 0, committed)
    }

    @Test fun blendAndLiquifyKeepLargePageInputInsideAFrameBudget() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { tool ->
            val seed = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888).apply {
                for (y in 80 until 140) for (x in 320 until 650) setPixel(x, y, Color.RED)
            }
            val surface = PixelTileBitmapAdapter.import(seed)
            seed.recycle()
            val view = canvas {
                layout(0, 0, 1000, 1000)
                this.tool = tool
                fingerDrawingEnabled = true
                baseWidth = 96f
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
            }
            val frameTimes = ArrayList<Long>(90)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                fun dispatch(time: Long, action: Int, x: Float, y: Float) {
                    event(time, action, x, y).also { input ->
                        try { view.onTouchEvent(input) } finally { input.recycle() }
                    }
                }
                dispatch(0L, MotionEvent.ACTION_DOWN, 380f, 100f)
                repeat(90) { index ->
                    dispatch(10L + index, MotionEvent.ACTION_MOVE, 382f + index * 2.2f, 100f + index % 7)
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.flushRealtimeToolFrame()
                    frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                }
                dispatch(120L, MotionEvent.ACTION_UP, 580f, 104f)
            }
            val sorted = frameTimes.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            android.util.Log.i("InkloomPerf", "${tool.name.lowercase()}_large_page_p95_ms=$p95Ms")
            assertTrue("${tool.name} p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
        }
    }
}
