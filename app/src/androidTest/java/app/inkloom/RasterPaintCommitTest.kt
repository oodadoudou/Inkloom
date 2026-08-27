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
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterPaintCommitTest {
    @Test fun eightKProductionPenDownRemainsSparseWithoutAFullPageMirror() {
        val surface = PixelTileSurface(8192, 8192)
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 24f
            inkColor = Color.BLACK
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> committed = patch }
        }

        assertEquals("Activating an 8K raster layer must allocate no page pixels", 0L, surface.retainedByteSize)
        send(view, MotionEvent.ACTION_DOWN, 120f, 200f, 0L)
        repeat(8) { index -> send(view, MotionEvent.ACTION_MOVE, 123f + index * 3f, 200f + index % 2, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 148f, 201f, 20L)

        assertTrue("An 8K gesture must retain only touched tiles: ${surface.tileCount}", surface.tileCount in 1..24)
        assertTrue(
            "Sparse retained bytes must stay far below an 8K ARGB page",
            surface.retainedByteSize < 8192L * 8192L * 4L / 100L,
        )
        val patchBytes = requireNotNull(committed).bitmap.byteCount
        assertTrue("The durable callback must also be a local patch: $patchBytes bytes", patchBytes < 8_000_000)
        committed?.close()
    }

    @Test fun importedStampDepositsIncrementallyWithoutPenUpReplay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "incremental-raster-stamp-tip.png")
        writePng(tipFile, IntArray(64) { Color.BLACK })
        val surface = PixelTileSurface(600, 600)
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
            setRasterLayerSurfaces(mapOf("paint" to surface))
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
        assertEquals("Private dabs must not publish before pen-up", 0, Color.alpha(surface.pixelAt(225, 300)))
        val p95 = frameTimesMs.sorted()[(frameTimesMs.size * .95f).toInt().coerceIn(0, frameTimesMs.lastIndex)]
        android.util.Log.i("InkloomPerf", "raster_imported_direct_deposit_p95_ms=$p95")
        assertTrue("Imported direct-deposit frame p95 was ${p95}ms", p95 < 16.7f)
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 45L)

        assertTrue("Multiple packets should have entered the open raster transaction", view.rasterIncrementalPatchCount >= 2)
        assertEquals("Eligible imported stamps must not replay the complete path on pen-up", 0, view.rasterFullReplayCount)
        assertTrue(Color.alpha(surface.pixelAt(225, 300)) > 0)
        requireNotNull(committed).close()
        front.recycle(); completeReplay.recycle(); tipFile.delete()
    }

    @Test fun ordinaryRasterPaintHasOneLivePresentationOwnerAndLeavesStablePixelsFixed() {
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        Canvas(seed).drawCircle(90f, 300f, 24f, android.graphics.Paint().apply { color = Color.BLUE })
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        val front = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        var activeInk = false
        var committed: RasterLayerPatch? = null
        lateinit var view: InkCanvasView
        view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 36f
            sizePressureEnabled = false
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> committed = patch }
        }
        view.activeStrokeSurface = object : ActiveStrokeCompositor {
            override val hasActiveInk: Boolean get() = activeInk
            override fun render(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) {
                activeInk = true
                view.drawActiveStrokes(Canvas(front), packet.strokes, incrementalStampDraw = true)
            }
            override fun commit(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) { activeInk = true }
            override fun onDocumentFramePresented(packetSequence: Long) { activeInk = false }
            override fun cancel() { activeInk = false; front.eraseColor(Color.TRANSPARENT) }
        }

        send(view, MotionEvent.ACTION_DOWN, 230f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 300f, 200f, 5L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.flushActiveStrokeFrame() }
        assertTrue("The active stroke must be visible in the low-latency owner", Color.alpha(front.getPixel(260, 200)) > 0)

        val duringContact = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(duringContact)) }
        assertEquals("Previously published pixels must remain byte-identical during another stroke", Color.BLUE, duringContact.getPixel(60, 200))
        assertEquals(
            "Normal opaque raster layers must bypass CPU projection and retain their tile RenderNodes",
            0,
            view.lastRasterProjectionRecomposedTileCount,
        )
        assertEquals(
            "The stable child must not also project front-buffer-owned pigment",
            0,
            Color.alpha(duringContact.getPixel(260, 200)),
        )

        send(view, MotionEvent.ACTION_UP, 320f, 200f, 10L)
        val landed = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(landed)) }
        assertEquals("Landing a stroke must not alter an unrelated committed pixel", Color.BLUE, landed.getPixel(60, 200))
        assertTrue("The stroke becomes visible in the stable document only after commit", Color.alpha(landed.getPixel(280, 200)) > 0)
        requireNotNull(committed).close()
        front.recycle(); duringContact.recycle(); landed.recycle()
    }

    @Test fun wetPaintDepositsDuringContactAndPenUpDoesNotReplayTheStroke() {
        val surface = PixelTileSurface(1200, 900)
        val preview = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        var frontBufferRenders = 0
        var committed: RasterLayerPatch? = null
        val frameTimesMs = mutableListOf<Float>()
        val transportTimesMs = mutableListOf<Float>()
        val presentationTimesMs = mutableListOf<Float>()
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 52f
            brushHardness = .7f
            sizePressureEnabled = false
            procreateWetMix = app.inkloom.document.ProcreateWetMix(
                dilution = .45f,
                charge = .75f,
                attack = .8f,
                pull = .35f,
                blur = .12f,
            )
            waterEdgeEnabled = true
            waterEdgeRadius = 4f
            waterEdgeOpacity = .35f
            waterEdgeBlur = 2f
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> committed = patch }
        }
        view.activeStrokeSurface = object : ActiveStrokeCompositor {
            override fun render(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) { frontBufferRenders++ }
            override fun commit(packet: app.inkloom.engine.v3.render.StrokeRenderPacket) = Unit
            override fun cancel() = Unit
        }

        send(view, MotionEvent.ACTION_DOWN, 100f, 300f, 0L)
        repeat(48) { index ->
            send(view, MotionEvent.ACTION_MOVE, 108f + index * 10f, 300f + index % 3, 5L + index)
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transportStarted = android.os.SystemClock.elapsedRealtimeNanos()
                view.flushActiveStrokeFrame()
                transportTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - transportStarted) / 1_000_000f
                preview.eraseColor(Color.TRANSPARENT)
                val presentationStarted = android.os.SystemClock.elapsedRealtimeNanos()
                view.draw(Canvas(preview))
                presentationTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - presentationStarted) / 1_000_000f
            }
            frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }
        assertEquals("Destination-dependent wet pigment must not also enter the source-over front buffer", 0, frontBufferRenders)
        assertTrue("Wet pigment must be visible before pen-up", Color.alpha(preview.getPixel(300, 300)) > 0)

        val started = android.os.SystemClock.elapsedRealtimeNanos()
        send(view, MotionEvent.ACTION_UP, 590f, 302f, 70L)
        val penUpMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        val p95 = frameTimesMs.sorted()[(frameTimesMs.size * .95f).toInt().coerceIn(0, frameTimesMs.lastIndex)]
        val transportP95 = transportTimesMs.sorted()[(transportTimesMs.size * .95f).toInt().coerceIn(0, transportTimesMs.lastIndex)]
        val presentationP95 = presentationTimesMs.sorted()[(presentationTimesMs.size * .95f).toInt().coerceIn(0, presentationTimesMs.lastIndex)]
        android.util.Log.i(
            "InkloomPerf",
            "raster_wet_live_p95_ms=$p95 transport_p95_ms=$transportP95 presentation_p95_ms=$presentationP95 raster_wet_commit_ms=$penUpMs",
        )
        assertTrue("Wet paint must be deposited in multiple retained transaction patches", view.rasterIncrementalPatchCount >= 4)
        assertEquals("Pen-up must publish retained wet tiles instead of replaying the whole stroke", 0, view.rasterFullReplayCount)
        assertTrue("Wet live frame p95 was ${p95}ms", p95 < 33.4f)
        assertTrue("Wet pen-up commit was ${penUpMs}ms", penUpMs < 33.4f)
        requireNotNull(committed).close()
        preview.recycle()
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
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(seed).drawCircle(150f, 300f, 24f, android.graphics.Paint().apply { color = Color.RED })
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        val wholePage = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val original = surface.snapshot().copyPremultipliedPixels(wholePage)
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 120f
            brushHardness = .6f
            procreateWetMix = app.inkloom.document.ProcreateWetMix(dilution = .9f, charge = .6f, attack = .7f, pull = 1f, blur = .25f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
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
        val preview = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(preview)) }
        assertTrue(
            "Pull must be visible from the private transaction while contact is still down",
            Color.alpha(preview.getPixel(170, 200)) > 0,
        )
        preview.recycle()

        send(view, MotionEvent.ACTION_CANCEL, 220f, 200f, 20L)
        val restored = surface.snapshot().copyPremultipliedPixels(wholePage)
        assertEquals(original.toList(), restored.toList())
    }

    @Test fun penPaintsTheActiveRasterSurfaceWithoutAddingAVectorStroke() {
        val surface = PixelTileSurface(600, 600)
        var rasterCommits = 0
        var vectorCommits = 0
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 42f
            inkColor = Color.MAGENTA
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { patch -> rasterCommits++; committed = patch }
            onStrokeCommitted = { vectorCommits++ }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f + index % 3, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertEquals("One raster paint gesture is one durable surface commit", 1, rasterCommits)
        assertEquals("Raster paint must not leak a vector stroke into the document", 0, vectorCommits)
        assertTrue(requireNotNull(committed).hasAlphaInSourceRegion(80, 270, 260, 340))
        assertTrue("Pen-up must transfer a local patch, not the complete raster", requireNotNull(committed).bitmap.byteCount < 600 * 600 * 4)
        committed?.close()
    }

    @Test fun rasterPaintCommitStaysWithinOneFrameForAStandardPenStroke() {
        val surface = PixelTileSurface(1200, 900)
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            baseWidth = 28f
            setRasterLayerSurfaces(mapOf("paint" to surface))
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
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.ERASER
            activeLayerId = "paint"
            eraserWidth = 240f
            eraserHardness = 1f
            eraserOpacity = 1f
            eraserPressureSize = false
            eraserTipAssetPath = tipFile.absolutePath
            setRasterLayerSurfaces(mapOf("paint" to surface))
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
        result.close(); tipFile.delete()
    }

    @Test fun rasterCommitUsesTheExactTexturizedGrainEvenWhenItsVariantWasNotPrewarmed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "raster-texturized-tip.png")
        val grainFile = File(context.cacheDir, "raster-texturized-grain.png")
        writePng(tipFile, IntArray(64) { Color.BLACK })
        writePng(grainFile, IntArray(64) { index -> if (index % 8 in 4..5) Color.BLACK else Color.WHITE })
        fun commit(movement: Float): RasterLayerPatch {
            val surface = PixelTileSurface(600, 600)
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
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { patch -> committed = patch }
            }
            send(view, MotionEvent.ACTION_DOWN, 125f, 200f, 0L)
            send(view, MotionEvent.ACTION_UP, 125f, 200f, 5L)
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
