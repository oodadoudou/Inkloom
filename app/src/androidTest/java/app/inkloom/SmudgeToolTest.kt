package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
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
import app.inkloom.engine.v5.presentation.InvalidationReason
import app.inkloom.engine.v5.material.PressureResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
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

    private fun event(time: Long, action: Int, x: Float, y: Float, stylus: Boolean = false, pressure: Float = 1f): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0,
            if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val e = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(e) } } finally { e.recycle() }
    }

    @Test fun borrowedRoundTipWidthPressureChangesTheSmudgeFootprint() {
        fun raster(pressure: Float): IntArray {
            val seed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
                for (y in 25 until 175) for (x in 20 until 110) setPixel(x, y, Color.RED)
            }
            val surface = PixelTileBitmapAdapter.import(seed)
            seed.recycle()
            val view = canvas {
                tool = EditorTool.SMUDGE; activeLayerId = "paint"; baseWidth = 120f
                smudgeWidthPressure = PressureResponse(true, .15f)
                setRasterLayerSurfaces(mapOf("paint" to surface))
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                listOf(Triple(MotionEvent.ACTION_DOWN, 80f, 90f),
                    Triple(MotionEvent.ACTION_MOVE, 125f, 90f),
                    Triple(MotionEvent.ACTION_UP, 150f, 90f)).forEachIndexed { index, (action, x, y) ->
                    event(index * 8L, action, x, y, stylus = true, pressure = pressure).also {
                        try { view.onTouchEvent(it) } finally { it.recycle() }
                    }
                }
                view.flushRealtimeToolFrame()
            }
            return surface.snapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, 200, 200))
        }
        assertFalse(raster(.2f).contentEquals(raster(1f)))
    }

    @Test fun rasterToolsPreserveEveryHistoricalPointAcrossFrameSchedules() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { tool ->
            listOf(0f, 1f).forEach { strength ->
                val separate = correctionRaster(tool, strength, strength, batching = 0)
                assertArrayEquals("$tool must retain all points queued before one frame", separate,
                    correctionRaster(tool, strength, strength, batching = 1))
                assertArrayEquals("$tool must consume Android historical samples", separate,
                    correctionRaster(tool, strength, strength, batching = 2))
            }
        }
    }

    @Test fun emptyRasterToolContactsHaveOneExplicitTerminalReason() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { selectedTool ->
            val reasons = mutableListOf<String>()
            var commits = 0
            val view = canvas {
                tool = selectedTool; fingerDrawingEnabled = true; activeLayerId = "empty"
                setRasterLayerSurfaces(mapOf("empty" to PixelTileSurface(200, 200)))
                onPaintCapabilityRejected = { reasons += it }
                onRasterLayerCommitted = { commits++ }
            }
            send(view, 0, MotionEvent.ACTION_DOWN, 50f, 100f)
            send(view, 8, MotionEvent.ACTION_MOVE, 150f, 100f)
            send(view, 16, MotionEvent.ACTION_UP, 150f, 100f)
            assertEquals(0, commits)
            assertEquals("Every completed contact has one terminal result", 1, reasons.size)
            assertTrue(reasons.single().contains("no pixel changes"))
        }
    }

    @Test fun rasterToolsExecuteBothIndependentStabilizersAndFreezeThemAtDown() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { tool ->
            val raw = correctionRaster(tool, 0f, 0f)
            val global = correctionRaster(tool, 1f, 0f)
            val brush = correctionRaster(tool, 0f, 1f)
            val both = correctionRaster(tool, 1f, 1f)
            assertFalse("$tool global setting must reach pixels", raw.contentEquals(global))
            assertArrayEquals("The same amount uses the same filter in either slot", global, brush)
            assertFalse("$tool both stages must execute", both.contentEquals(global))
            assertArrayEquals("Settings changed during a contact apply on the next DOWN", both,
                correctionRaster(tool, 1f, 1f, changeWhileDown = true))
        }
    }

    @Test fun cancelRestoresRasterAndTheNextContactStartsWithFreshCorrectionState() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { tool ->
            assertArrayEquals(correctionRaster(tool, 1f, 1f),
                correctionRaster(tool, 1f, 1f, cancelBefore = true))
        }
    }

    private fun correctionRaster(tool: EditorTool, global: Float, brush: Float, batching: Int = 0,
        changeWhileDown: Boolean = false, cancelBefore: Boolean = false): IntArray {
        val seed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            for (y in 35 until 160) for (x in 25 until 110) setPixel(x, y,
                if ((x / 7 + y / 9) % 2 == 0) Color.RED else Color.BLUE)
        }
        val original = IntArray(40000).also { seed.getPixels(it, 0, 200, 0, 0, 200, 200) }
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        val view = canvas {
            this.tool = tool; baseWidth = 100f; activeLayerId = "paint"
            globalStrokeStabilization = global; rasterToolStrokeStabilization = brush
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }
        val points = (0..24).map { i -> 55f + i * 3f to (90f + i * .8f + if (i % 2 == 0) 3f else -3f) }
        fun pixels(): IntArray = PixelTileBitmapAdapter.render(surface.snapshot()).let { bitmap ->
            try { IntArray(40000).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 200) } }
            finally { bitmap.recycle() }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun dispatch(time: Long, action: Int, point: Pair<Float, Float>) {
                event(time, action, point.first, point.second, stylus = true).also {
                    try { view.onTouchEvent(it) } finally { it.recycle() }
                }
            }
            if (cancelBefore) {
                dispatch(0, MotionEvent.ACTION_DOWN, points.first())
                dispatch(8, MotionEvent.ACTION_MOVE, points[8]); view.flushRealtimeToolFrame()
                dispatch(16, MotionEvent.ACTION_CANCEL, points[8])
                assertArrayEquals("Cancel must restore the original pigment", original, pixels())
            }
            dispatch(100, MotionEvent.ACTION_DOWN, points.first())
            if (changeWhileDown) { view.globalStrokeStabilization = 0f; view.rasterToolStrokeStabilization = 0f }
            if (batching == 2) {
                val move = event(108, MotionEvent.ACTION_MOVE, points[1].first, points[1].second, stylus = true)
                try {
                    for (i in 2..points.lastIndex) move.addBatch(100L + i * 8,
                        arrayOf(MotionEvent.PointerCoords().apply {
                            x = points[i].first; y = points[i].second; pressure = 1f; size = .1f
                        }), 0)
                    view.onTouchEvent(move)
                } finally { move.recycle() }
            } else for (i in 1..points.lastIndex) {
                dispatch(100L + i * 8, MotionEvent.ACTION_MOVE, points[i])
                if (batching == 0) view.flushRealtimeToolFrame()
            }
            dispatch(310, MotionEvent.ACTION_UP, points.last())
        }
        return pixels()
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

    @Test fun blendAndLiquifyUseBoundedV5DamageForLiveAndCommittedFrames() {
        listOf(EditorTool.SMUDGE, EditorTool.LIQUIFY).forEach { selectedTool ->
            val seed = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
                for (y in 120 until 280) for (x in 80 until 180) setPixel(x, y, Color.RED)
            }
            val surface = PixelTileBitmapAdapter.import(seed)
            seed.recycle()
            val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val view = canvas {
                layout(0, 0, 400, 400)
                tool = selectedTool
                fingerDrawingEnabled = true
                baseWidth = 96f
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
            }

            send(view, 0L, MotionEvent.ACTION_DOWN, 140f, 200f)
            send(view, 10L, MotionEvent.ACTION_MOVE, 220f, 200f)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.flushRealtimeToolFrame()
                view.draw(Canvas(output))
            }
            val live = requireNotNull(view.lastV5FrameInvalidation)
            assertEquals(InvalidationReason.ACTIVE_DELTA, live.reason)
            assertTrue("$selectedTool live damage must stay local", live.dirtyBounds.width < 400f && live.dirtyBounds.height < 400f)

            send(view, 20L, MotionEvent.ACTION_UP, 220f, 200f)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(output)) }
            val committed = requireNotNull(view.lastV5FrameInvalidation)
            assertEquals(InvalidationReason.DOCUMENT_COMMIT, committed.reason)
            assertTrue("$selectedTool commit damage must stay local", committed.dirtyBounds.width < 400f && committed.dirtyBounds.height < 400f)
            output.recycle()
        }
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
