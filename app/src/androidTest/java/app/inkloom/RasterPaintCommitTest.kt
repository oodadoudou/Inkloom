package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterPaintCommitTest {
    @Test fun wetPullUpdatesRasterBeforePenUpAndCancelRestoresIt() {
        val surface = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(surface).drawCircle(150f, 300f, 24f, android.graphics.Paint().apply { color = Color.RED })
        val original = IntArray(surface.width * surface.height).also { surface.getPixels(it, 0, surface.width, 0, 0, surface.width, surface.height) }
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 120f
            brushHardness = .6f
            procreateWetMix = app.inkloom.document.ProcreateWetMix(dilution = .9f, charge = .6f, attack = .7f, pull = 1f, blur = .25f)
            setRasterLayerBitmaps(mapOf("paint" to surface))
        }
        view.activeStrokeSurface = object : ActiveStrokeCompositor {
            override fun render(strokes: List<app.inkloom.document.InkStroke>) = Unit
            override fun commit(strokes: List<app.inkloom.document.InkStroke>) = Unit
            override fun cancel() = Unit
        }

        send(view, MotionEvent.ACTION_DOWN, 100f, 200f, 0L)
        repeat(8) { index ->
            send(view, MotionEvent.ACTION_MOVE, 115f + index * 15f, 200f, 5L + index)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.flushActiveStrokeFrame() }
        }
        val alphaXs = (0 until surface.width).filter { x -> Color.alpha(surface.getPixel(x, 300)) > 0 }
        assertTrue(
            "Pull must move pigment while the contact is still down; row alpha range=${alphaXs.firstOrNull()}..${alphaXs.lastOrNull()}",
            Color.alpha(surface.getPixel(255, 300)) > 0,
        )

        send(view, MotionEvent.ACTION_CANCEL, 220f, 200f, 20L)
        val restored = IntArray(surface.width * surface.height).also { surface.getPixels(it, 0, surface.width, 0, 0, surface.width, surface.height) }
        assertEquals(original.toList(), restored.toList())
        surface.recycle()
    }

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

    @Test fun importedEraserCommitsItsAuthoredTipInsteadOfTheLiveRoundProxy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "raster-imported-eraser-tall-tip.png")
        val pixels = IntArray(8 * 32) { index ->
            val x = index % 8
            if (x in 2..5) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixels, 8, 32, Bitmap.Config.ARGB_8888).also { tip ->
            tipFile.outputStream().use { output -> check(tip.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            tip.recycle()
        }
        val surface = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        var committed: Bitmap? = null
        val view = createCanvas {
            tool = EditorTool.ERASER
            activeLayerId = "paint"
            eraserWidth = 240f
            eraserHardness = 1f
            eraserOpacity = 1f
            eraserPressureSize = false
            eraserTipAssetPath = tipFile.absolutePath
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { _, bitmap -> committed = bitmap }
        }

        send(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
        send(view, MotionEvent.ACTION_UP, 200f, 200f, 5L)

        val result = requireNotNull(committed)
        assertTrue("The authored black tip core must erase the raster", Color.alpha(result.getPixel(300, 300)) < 16)
        assertTrue(
            "Pixels inside the round preview proxy but outside the narrow authored tip must be restored",
            Color.alpha(result.getPixel(340, 300)) > 240,
        )
        result.recycle(); surface.recycle(); tipFile.delete()
    }

    @Test fun rasterCommitUsesTheExactTexturizedGrainEvenWhenItsVariantWasNotPrewarmed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "raster-texturized-tip.png")
        val grainFile = File(context.cacheDir, "raster-texturized-grain.png")
        writePng(tipFile, IntArray(64) { Color.BLACK })
        writePng(grainFile, IntArray(64) { index -> if (index % 8 in 4..5) Color.BLACK else Color.WHITE })
        fun commit(movement: Float): Bitmap {
            val surface = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            var committed: Bitmap? = null
            val view = createCanvas {
                tool = EditorTool.PEN
                activeLayerId = "paint"
                baseWidth = 400f
                sizePressureEnabled = false
                tipAssetPath = tipFile.absolutePath
                tipAssetPaths = listOf(tipFile.absolutePath)
                grainAssetPath = grainFile.absolutePath
                grainDensity = 1f
                grainScale = 100f
                grainMovement = movement
                grainZoom = 0f
                setRasterLayerBitmaps(mapOf("paint" to surface))
                onRasterLayerCommitted = { _, bitmap -> committed = bitmap }
            }
            send(view, MotionEvent.ACTION_DOWN, 125f, 200f, 0L)
            send(view, MotionEvent.ACTION_UP, 125f, 200f, 5L)
            surface.recycle()
            return requireNotNull(committed)
        }

        val moving = commit(0f)
        val texturized = commit(1f)
        var differingPixels = 0
        for (y in 250..350) for (x in 138..238) {
            if (moving.getPixel(x, y) != texturized.getPixel(x, y)) differingPixels++
        }
        assertTrue(
            "The durable raster must use the page-space phase instead of committing the provisional base tip",
            differingPixels > 100,
        )
        moving.recycle(); texturized.recycle(); tipFile.delete(); grainFile.delete()
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

    private fun writePng(file: File, pixels: IntArray) {
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            bitmap.recycle()
        }
    }
}
