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
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.LayerIds
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

    @Test fun draggingBlendMixesExistingPigmentWithoutAddingAStroke() {
        var removed = 0
        var replacements = 0
        var mixedStrokes = emptyList<InkStroke>()
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 400f
            activeLayerId = LayerIds.INK
            setDocumentStrokes(
                listOf(
                    InkStroke(id = "red", colorArgb = 0xFFFF0000, width = 30f, layerId = LayerIds.INK, points = listOf(StrokePoint(.3f, .49f), StrokePoint(.7f, .49f))),
                    InkStroke(id = "blue", colorArgb = 0xFF0000FF, width = 30f, layerId = LayerIds.INK, points = listOf(StrokePoint(.3f, .51f), StrokePoint(.7f, .51f))),
                ),
                "blend-page",
            )
            onStrokesReplaced = { deleted, changed, _ -> removed = deleted.size; replacements = changed.size; mixedStrokes = changed }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 100f, 100f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 60L, MotionEvent.ACTION_UP, 140f, 100f)
        assertEquals(2, removed)
        assertEquals("Blend edits the two existing strokes; it must not paint a third stroke", 2, replacements)
        assertTrue("Blend must create local mixed pigment", mixedStrokes.flatMap(InkStroke::points).any { it.pigmentArgb != null })
    }

    @Test fun blendOnlyModifiesTheActiveLayer() {
        var changed = emptyList<InkStroke>()
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 400f
            activeLayerId = "paint"
            setDocumentStrokes(
                listOf(
                    InkStroke(id = "notes", colorArgb = 0xFF000000, width = 30f, layerId = "notes", points = listOf(StrokePoint(.3f, .5f), StrokePoint(.7f, .5f))),
                    InkStroke(id = "paint", colorArgb = 0xFFFF0000, width = 30f, layerId = "paint", points = listOf(StrokePoint(.3f, .5f), StrokePoint(.7f, .5f))),
                ),
                "blend-layers",
            )
            onStrokesReplaced = { _, replacements, _ -> changed = replacements }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 140f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 140f, 100f)

        assertEquals(listOf("paint"), changed.map(InkStroke::id))
    }

    @Test fun rasterLayerBlendCommitsOnePixelSurfaceAndDoesNotRewriteVectorStrokes() {
        val surface = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 70 until 130) for (x in 30 until 80) surface.setPixel(x, y, Color.RED)
        var rasterCommits = 0
        var vectorReplacements = 0
        var committedSurface: Bitmap? = null
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            baseWidth = 320f
            activeLayerId = "raster"
            setRasterLayerBitmaps(mapOf("raster" to surface))
            onRasterLayerCommitted = { layerId, bitmap ->
                assertEquals("raster", layerId)
                rasterCommits++
                committedSurface = bitmap
            }
            onStrokesReplaced = { _, _, _ -> vectorReplacements++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 60f, 100f)
        send(view, 20L, MotionEvent.ACTION_MOVE, 105f, 100f)
        send(view, 40L, MotionEvent.ACTION_UP, 105f, 100f)

        assertEquals("One blend gesture creates one raster transaction", 1, rasterCommits)
        assertEquals(0, vectorReplacements)
        assertTrue(Color.alpha(requireNotNull(committedSurface).getPixel(105, 100)) > 0)
        committedSurface?.recycle()
        surface.recycle()
    }

    @Test fun blendingBlankPaperDoesNotCreatePigment() {
        var committed = 0
        val view = canvas {
            tool = EditorTool.SMUDGE
            fingerDrawingEnabled = true
            onStrokeCommitted = { committed++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 20f, 100f)
        for (step in 1..16) send(view, step * 10L, MotionEvent.ACTION_MOVE, 20f + step * 10f, 100f)
        send(view, 180L, MotionEvent.ACTION_UP, 180f, 100f)
        assertEquals("A blend tool cannot add pigment to blank paper", 0, committed)
    }

    @Test fun blendAndLiquifyKeepLargePageInputInsideAFrameBudget() {
        val page = List(3_000) { index ->
            val column = index % 60
            val row = index / 60
            InkStroke(
                id = "perf-$index",
                width = 5f,
                layerId = LayerIds.INK,
                points = listOf(
                    StrokePoint((column + .1f) / 60f, (row + .25f) / 50f),
                    StrokePoint((column + .9f) / 60f, (row + .75f) / 50f),
                ),
            )
        }
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { tool ->
            val view = canvas {
                layout(0, 0, 1000, 1000)
                this.tool = tool
                fingerDrawingEnabled = true
                baseWidth = 96f
                activeLayerId = LayerIds.INK
                setDocumentStrokes(page, "${tool.name.lowercase()}-performance")
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
