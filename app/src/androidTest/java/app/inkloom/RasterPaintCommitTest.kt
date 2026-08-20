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
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.engine.v3.tools.RasterLayerPatch
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterPaintCommitTest {
    @Test fun importedStampDepositsIncrementallyWithoutPenUpReplay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "incremental-raster-stamp-tip.png")
        writePng(tipFile, IntArray(64) { Color.BLACK })
        val display = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val front = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val completeReplay = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        var committed: RasterLayerPatch? = null
        var latestStrokes: List<app.inkloom.document.InkStroke> = emptyList()
        val frameTimesMs = mutableListOf<Float>()
        lateinit var view: InkCanvasView
        view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 64f
            sizePressureEnabled = false
            tipAssetPath = tipFile.absolutePath
            tipAssetPaths = listOf(tipFile.absolutePath)
            setRasterLayerBitmaps(mapOf("paint" to display))
            onRasterLayerCommitted = { patch -> committed = patch }
        }
        view.activeStrokeSurface = object : ActiveStrokeCompositor {
            override fun render(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) {
                latestStrokes = packet.strokes
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                view.drawActiveStrokes(Canvas(front), packet.strokes, incrementalStampDraw = true)
                frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            }
            override fun commit(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) = Unit
            override fun cancel() = Unit
        }

        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(32) { index ->
            send(view, MotionEvent.ACTION_MOVE, 95f + index * 7f, 200f, 5L + index)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.flushActiveStrokeFrame() }
            if (index == 15) {
                val transactionPackets = view.rasterIncrementalPatchCount
                view.resetActiveRenderState()
                view.drawActiveStrokes(Canvas(completeReplay), latestStrokes, completeRedraw = true)
                assertEquals(
                    "A system front-buffer replay must never redeposit authoritative dabs",
                    transactionPackets,
                    view.rasterIncrementalPatchCount,
                )
            }
        }

        assertTrue("The low-latency patch must be visible before pen-up", Color.alpha(front.getPixel(150, 200)) > 0)
        assertTrue("A complete Android surface replay must remain visually complete", Color.alpha(completeReplay.getPixel(120, 200)) > 0)
        assertEquals("The stable display must not double-compose live dabs", 0, Color.alpha(display.getPixel(225, 300)))
        val p95 = frameTimesMs.sorted()[(frameTimesMs.size * .95f).toInt().coerceIn(0, frameTimesMs.lastIndex)]
        android.util.Log.i("InkloomPerf", "raster_imported_direct_deposit_p95_ms=$p95")
        assertTrue("Imported direct-deposit frame p95 was ${p95}ms", p95 < 16.7f)
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 45L)

        assertTrue("Multiple packets should have entered the open raster transaction", view.rasterIncrementalPatchCount >= 2)
        assertEquals("Eligible imported stamps must not replay the complete path on pen-up", 0, view.rasterFullReplayCount)
        assertTrue(Color.alpha(display.getPixel(225, 300)) > 0)
        requireNotNull(committed).close()
        front.recycle(); completeReplay.recycle(); display.recycle(); tipFile.delete()
    }

    @Test fun productionTileSurfacePaintsWithoutAnExternalBitmapMirror() {
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val tileSurface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 42f
            inkColor = Color.MAGENTA
            setRasterLayerSurfaces(mapOf("paint" to tileSurface))
            onRasterLayerCommitted = { patch -> committed = patch }
        }

        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertTrue(requireNotNull(committed).hasAlphaInSourceRegion(80, 270, 260, 340))
        assertTrue("The authoritative surface must receive the same committed pigment", Color.alpha(tileSurface.pixelAt(180, 300)) > 0)
        val legacyDrawsBeforeStableFrame = view.legacyLayerCompositionDrawCount
        val stableFrame = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(stableFrame))
        assertTrue("The post-stylus frame must use the production V3 projection", view.v3RasterProjectionDrawCount > 0)
        assertEquals(
            "Stable raster display must not re-enter mixed-scene compatibility composition",
            legacyDrawsBeforeStableFrame,
            view.legacyLayerCompositionDrawCount,
        )
        stableFrame.recycle()
        committed?.close()
    }

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
            override fun render(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) = Unit
            override fun commit(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) = Unit
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
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 42f
            inkColor = Color.MAGENTA
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> rasterCommits++; committed = patch }
            onStrokeCommitted = { vectorCommits++ }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f + index % 3, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertEquals("One raster paint gesture is one durable surface commit", 1, rasterCommits)
        assertEquals("Raster paint must not leak a vector stroke into the document", 0, vectorCommits)
        assertTrue(requireNotNull(committed).hasAlphaInSourceRegion(80, 270, 260, 340))
        assertTrue("Pen-up must transfer a local patch, not the complete raster", requireNotNull(committed).bitmap.byteCount < surface.byteCount)
        committed?.close(); surface.recycle()
    }

    @Test fun rasterPaintCommitStaysWithinOneFrameForAStandardPenStroke() {
        val surface = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 28f
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> patch.close() }
        }
        send(view, MotionEvent.ACTION_DOWN, 120f, 300f, 0L)
        repeat(72) { index -> send(view, MotionEvent.ACTION_MOVE, 125f + index * 7f, 300f + index % 5, 5L + index) }
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        send(view, MotionEvent.ACTION_UP, 635f, 302f, 90L)
        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_standard_pen_commit_ms=$elapsedMs")
        assertEquals("The standard pen belongs to the direct tile-deposit path", 0, view.rasterFullReplayCount)
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
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.ERASER
            activeLayerId = "paint"
            eraserWidth = 240f
            eraserHardness = 1f
            eraserOpacity = 1f
            eraserPressureSize = false
            eraserTipAssetPath = tipFile.absolutePath
            setRasterLayerBitmaps(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> committed = patch }
        }

        send(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
        send(view, MotionEvent.ACTION_UP, 200f, 200f, 5L)

        val result = requireNotNull(committed)
        assertTrue("The authored black tip core must erase the raster", Color.alpha(result.colorAtSource(300, 300)) < 16)
        assertTrue(
            "Pixels inside the round preview proxy but outside the narrow authored tip must be restored",
            Color.alpha(result.colorAtSource(340, 300)) > 240,
        )
        result.close(); surface.recycle(); tipFile.delete()
    }

    @Test fun rasterCommitUsesTheExactTexturizedGrainEvenWhenItsVariantWasNotPrewarmed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "raster-texturized-tip.png")
        val grainFile = File(context.cacheDir, "raster-texturized-grain.png")
        writePng(tipFile, IntArray(64) { Color.BLACK })
        writePng(grainFile, IntArray(64) { index -> if (index % 8 in 4..5) Color.BLACK else Color.WHITE })
        fun commit(movement: Float): RasterLayerPatch {
            val surface = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            var committed: RasterLayerPatch? = null
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
                onRasterLayerCommitted = { patch -> committed = patch }
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
            if (moving.colorAtSource(x, y) != texturized.colorAtSource(x, y)) differingPixels++
        }
        assertTrue(
            "The durable raster must use the page-space phase instead of committing the provisional base tip",
            differingPixels > 100,
        )
        moving.close(); texturized.close(); tipFile.delete(); grainFile.delete()
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

    private fun RasterLayerPatch.hasAlphaInSourceRegion(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (y in top.coerceAtLeast(this.top) until bottom.coerceAtMost(this.bottom)) {
            for (x in left.coerceAtLeast(this.left) until right.coerceAtMost(this.right)) {
                if (Color.alpha(colorAtSource(x, y)) > 0) return true
            }
        }
        return false
    }

    private fun RasterLayerPatch.colorAtSource(x: Int, y: Int): Int =
        if (x in left until right && y in top until bottom) bitmap.getPixel(x - left, y - top) else Color.TRANSPARENT

    private fun writePng(file: File, pixels: IntArray) {
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            bitmap.recycle()
        }
    }
}
