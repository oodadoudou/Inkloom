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
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.TextureGrainSettings
import app.inkloom.brushpresetv5.WetMixSettings
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.engine.v5.storage.RasterLayerCommit
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterPaintCommitTest {
    @Test fun texturedWetPaintUsesItsAssetTipInTheProductionPathWithinFrameBudget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "v5-textured-wet-tip.png")
        writePng(tipFile, IntArray(64) { index -> if (index % 8 < 4) Color.BLACK else Color.WHITE })
        val surface = PixelTileSurface(600, 600)
        var committed: RasterLayerPatch? = null
        var rejection: String? = null
        val frameTimesMs = mutableListOf<Float>()
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(
                size = 80f,
                family = BrushFamily.WET_PAINT,
                tipAssetPaths = listOf(tipFile.absolutePath),
                wetMix = WetMixSettings(dilution = .25f, charge = .8f, attack = .9f, pull = .3f),
            )
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
            onPaintCapabilityRejected = { rejection = it }
        }

        send(view, MotionEvent.ACTION_DOWN, 100f, 200f, 0L)
        repeat(32) { index ->
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            send(view, MotionEvent.ACTION_MOVE, 106f + index * 5f, 200f, 5L + index)
            view.awaitV5StrokeWorkerIdleForTest()
            frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }
        send(view, MotionEvent.ACTION_UP, 266f, 200f, 45L)

        val p95 = frameTimesMs.sorted()[(frameTimesMs.size * .95f).toInt().coerceIn(0, frameTimesMs.lastIndex)]
        android.util.Log.i("InkloomPerf", "raster_textured_wet_p95_ms=$p95")
        assertTrue("Textured wet production p95 was ${p95}ms", p95 < 16.7f)
        assertEquals("The textured wet family must not be rejected", null, rejection)
        assertTrue("Textured wet stroke must commit", committed != null)
        assertTrue("Textured wet stroke must publish visible pigment", surface.tileCount > 0)
        committed?.close()
        tipFile.delete()
    }

    @Test fun presetMasterOpacityIsAppliedOnceInsteadOfSquared() {
        val surface = PixelTileSurface(600, 600)
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 48f, opacity = .5f, hardness = 1f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }

        sendStylus(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
        sendStylus(view, MotionEvent.ACTION_UP, 200f, 200f, 8L)

        val alpha = Color.alpha(surface.pixelAt(300, 300))
        assertTrue("50% master opacity must stay near 50%, not be squared: alpha=$alpha", alpha in 116..140)
    }

    @Test fun reconfiguringTheActiveProductionBrushChangesCommittedAlpha() {
        val brush = requireNotNull(SystemBrushCatalog.byId("system-monoline"))
        fun drawAt(opacity: Float): Int {
            val surface = PixelTileSurface(600, 600)
            val view = createCanvas {
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                applyBrushConfiguration(
                    EditorTool.PEN,
                    AppSettings(),
                    brush,
                    48f,
                    opacity,
                    Color.BLACK,
                    EraserPreset.NORMAL_FREE,
                )
            }
            sendStylus(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
            sendStylus(view, MotionEvent.ACTION_UP, 200f, 200f, 8L)
            return Color.alpha(surface.pixelAt(300, 300))
        }

        val opaque = drawAt(1f)
        val translucent = drawAt(.2f)
        assertTrue("Full opacity must remain visibly opaque: $opaque", opaque >= 245)
        assertTrue("The active brush master opacity must affect document pixels: $translucent", translucent in 42..62)
    }

    @Test fun factoryAirbrushesUseTheV5ParticlePathBeforePenUp() {
        listOf("system-soft-airbrush", "system-spray-airbrush").forEachIndexed { brushIndex, brushId ->
            val brush = requireNotNull(SystemBrushCatalog.byId(brushId))
            val surface = PixelTileSurface(600, 600)
            val display = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            var committed = false
            val frameTimesMs = mutableListOf<Float>()
            val view = createCanvas {
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { committed = true }
                applyBrushConfiguration(
                    EditorTool.PEN,
                    AppSettings(),
                    brush,
                    brush.defaultSize,
                    brush.defaultOpacity,
                    Color.BLACK,
                    EraserPreset.NORMAL_FREE,
                )
            }
            val gestureCountBefore = view.v5DryGestureCount
            val start = brushIndex * 1_000L
            sendStylus(view, MotionEvent.ACTION_DOWN, 70f, 150f, start)
            repeat(24) { index ->
                val frameStarted = android.os.SystemClock.elapsedRealtimeNanos()
                sendStylus(view, MotionEvent.ACTION_MOVE, 78f + index * 9f, 150f, start + index + 1L)
                view.awaitV5StrokeWorkerIdleForTest()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    display.eraseColor(Color.TRANSPARENT)
                    view.draw(Canvas(display))
                }
                frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - frameStarted) / 1_000_000f
            }
            view.awaitV5StrokeWorkerIdleForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                display.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(display))
            }

            assertEquals("$brushId must be owned by one V5 gesture", gestureCountBefore + 1, view.v5DryGestureCount)
            assertTrue("$brushId must present deposited pixels before pen-up", display.hasVisibleAlpha())
            assertEquals("$brushId must not publish document pixels before pen-up", 0, surface.tileCount)
            val p95 = frameTimesMs.sorted()[(frameTimesMs.size * 95 / 100).coerceAtMost(frameTimesMs.lastIndex)]
            android.util.Log.i("InkloomPerf", "v5_airbrush=$brushId live_p95_ms=$p95")
            assertTrue("$brushId live p95 was ${p95}ms", p95 < 16.7f)

            sendStylus(view, MotionEvent.ACTION_UP, 300f, 150f, start + 30L)
            assertTrue("$brushId must publish sparse tiles on pen-up", surface.tileCount > 0)
            assertTrue(committed)
            display.recycle()
        }
    }

    @Test fun eightKProductionPenDownRemainsSparseWithoutAFullPageMirror() {
        val surface = PixelTileSurface(8192, 8192)
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 24f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
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
        val display = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        var committed: RasterLayerPatch? = null
        val frameTimesMs = mutableListOf<Float>()
        lateinit var view: InkCanvasView
        view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 64f, tipAssetPaths = listOf(tipFile.absolutePath))
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(32) { index ->
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            send(view, MotionEvent.ACTION_MOVE, 95f + index * 7f, 200f, 5L + index)
            view.awaitV5StrokeWorkerIdleForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                display.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(display))
            }
            frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            display.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(display))
        }

        assertTrue("The exact V5 active delta must be visible before pen-up", Color.alpha(display.getPixel(150, 200)) > 0)
        val activePixels = IntArray(display.width * display.height).also {
            display.getPixels(it, 0, display.width, 0, 0, display.width, display.height)
        }
        assertEquals("Private dabs must not publish before pen-up", 0, Color.alpha(surface.pixelAt(225, 300)))
        val p95 = frameTimesMs.sorted()[(frameTimesMs.size * .95f).toInt().coerceIn(0, frameTimesMs.lastIndex)]
        android.util.Log.i("InkloomPerf", "raster_imported_direct_deposit_p95_ms=$p95")
        assertTrue("Imported direct-deposit frame p95 was ${p95}ms", p95 < 16.7f)
        send(view, MotionEvent.ACTION_UP, 312f, 200f, 45L)

        display.eraseColor(Color.TRANSPARENT)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(display)) }
        val landedPixels = IntArray(display.width * display.height).also {
            display.getPixels(it, 0, display.width, 0, 0, display.width, display.height)
        }
        display.eraseColor(Color.TRANSPARENT)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(display)) }
        val stablePixels = IntArray(display.width * display.height).also {
            display.getPixels(it, 0, display.width, 0, 0, display.width, display.height)
        }
        assertTrue("The active and landed generations must be pixel-identical", activePixels.contentEquals(landedPixels))
        assertTrue("Releasing the generation barrier must not flash or replace pixels", landedPixels.contentEquals(stablePixels))

        assertTrue(Color.alpha(surface.pixelAt(225, 300)) > 0)
        requireNotNull(committed).close()
        display.recycle(); tipFile.delete()
    }

    @Test fun ordinaryRasterPaintHasOneLivePresentationOwnerAndLeavesStablePixelsFixed() {
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        Canvas(seed).drawCircle(90f, 300f, 24f, android.graphics.Paint().apply { color = Color.BLUE })
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var committed: RasterLayerPatch? = null
        lateinit var view: InkCanvasView
        view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 36f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
        }
        send(view, MotionEvent.ACTION_DOWN, 230f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 300f, 200f, 5L)
        view.awaitV5StrokeWorkerIdleForTest()
        val duringContact = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(duringContact)) }
        val frameDamage = requireNotNull(view.lastV5FrameInvalidation)
        assertTrue("Active paint damage must remain smaller than the document", frameDamage.dirtyBounds.width < surface.logicalWidth)
        assertTrue("Active paint damage must remain smaller than the document", frameDamage.dirtyBounds.height < surface.logicalHeight)
        assertEquals("V5 dry ink owns the production gesture", 1, view.v5DryGestureCount)
        assertTrue("The private V5 sparse delta must be visible before pen-up", Color.alpha(duringContact.getPixel(260, 200)) > 0)
        assertEquals("Previously published pixels must remain byte-identical during another stroke", Color.BLUE, duringContact.getPixel(60, 200))
        assertEquals(
            "Normal opaque raster layers must bypass CPU projection and retain their tile RenderNodes",
            0,
            view.lastRasterProjectionRecomposedTileCount,
        )
        assertEquals(
            "The immutable committed surface must not publish before pen-up",
            0,
            Color.alpha(surface.pixelAt(390, 300)),
        )

        send(view, MotionEvent.ACTION_UP, 320f, 200f, 10L)
        val landed = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(landed)) }
        assertEquals("Landing a stroke must not alter an unrelated committed pixel", Color.BLUE, landed.getPixel(60, 200))
        assertTrue("The stroke becomes visible in the stable document only after commit", Color.alpha(landed.getPixel(280, 200)) > 0)
        requireNotNull(committed).close()
        duringContact.recycle(); landed.recycle()
    }

    @Test fun wetPaintDepositsDuringContactAndPenUpDoesNotReplayTheStroke() {
        val surface = PixelTileSurface(1200, 900)
        val preview = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        var committed: RasterLayerCommit? = null
        val frameTimesMs = mutableListOf<Float>()
        val transportTimesMs = mutableListOf<Float>()
        val presentationTimesMs = mutableListOf<Float>()
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(
                size = 52f,
                hardness = .7f,
                family = BrushFamily.WET_PAINT,
                wetMix = WetMixSettings(
                dilution = .45f,
                charge = .75f,
                attack = .8f,
                pull = .35f,
                ),
            )
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit }
        }
        send(view, MotionEvent.ACTION_DOWN, 100f, 300f, 0L)
        repeat(48) { index ->
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val transportStarted = android.os.SystemClock.elapsedRealtimeNanos()
            send(view, MotionEvent.ACTION_MOVE, 108f + index * 10f, 300f + index % 3, 5L + index)
            transportTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - transportStarted) / 1_000_000f
            view.awaitV5StrokeWorkerIdleForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                preview.eraseColor(Color.TRANSPARENT)
                val presentationStarted = android.os.SystemClock.elapsedRealtimeNanos()
                view.draw(Canvas(preview))
                presentationTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - presentationStarted) / 1_000_000f
            }
            frameTimesMs += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            preview.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(preview))
        }
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
        assertTrue("Wet input + presentation p95 was ${p95}ms", p95 < 16.7f)
        assertTrue("Wet input transport p95 was ${transportP95}ms", transportP95 < 12f)
        assertTrue("Wet pen-up commit was ${penUpMs}ms", penUpMs < 16.7f)
        // Materialization belongs to the persistence worker and is deliberately outside the
        // input/pen-up latency gate. Production InkloomApp already dispatches it off-main.
        requireNotNull(committed).capturePatch().close()
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
            installV5TestInk(size = 42f, color = Color.MAGENTA)
            setRasterLayerSurfaces(mapOf("paint" to tileSurface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
        }

        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertTrue(requireNotNull(committed).hasAlphaInSourceRegion(80, 270, 260, 340))
        assertTrue("The authoritative surface must receive the same committed pigment", Color.alpha(tileSurface.pixelAt(180, 300)) > 0)
        val stableFrame = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(stableFrame))
        assertTrue("The post-stylus frame must use the production V5 projection", view.v5RasterProjectionDrawCount > 0)
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
            installV5TestInk(
                size = 120f,
                hardness = .6f,
                family = BrushFamily.WET_PAINT,
                wetMix = WetMixSettings(dilution = .9f, charge = .6f, attack = .7f, pull = 1f),
            )
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }
        send(view, MotionEvent.ACTION_DOWN, 100f, 200f, 0L)
        repeat(8) { index ->
            send(view, MotionEvent.ACTION_MOVE, 115f + index * 15f, 200f, 5L + index)
        }
        view.awaitV5StrokeWorkerIdleForTest()
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
        var committed: RasterLayerPatch? = null
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 42f, color = Color.MAGENTA)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> rasterCommits++; committed = commit.capturePatch() }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        repeat(20) { index -> send(view, MotionEvent.ACTION_MOVE, 85f + index * 12f, 200f + index % 3, 5L + index) }
        send(view, MotionEvent.ACTION_UP, 330f, 200f, 30L)

        assertEquals("One raster paint gesture is one durable surface commit", 1, rasterCommits)
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
            installV5TestInk(size = 28f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { }
        }
        send(view, MotionEvent.ACTION_DOWN, 120f, 300f, 0L)
        repeat(72) { index -> send(view, MotionEvent.ACTION_MOVE, 125f + index * 7f, 300f + index % 5, 5L + index) }
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        send(view, MotionEvent.ACTION_UP, 635f, 302f, 90L, awaitPublication = false)
        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_standard_pen_commit_ms=$elapsedMs")
        assertTrue("Raster pen-up commit was ${elapsedMs}ms", elapsedMs < 16.7f)
        settlePublishedStroke(view)
    }

    @Test fun queuedPublicationRetainsEachContactsImmutableGeneration() {
        val surface = PixelTileSurface(600, 600)
        val committed = mutableListOf<RasterLayerCommit>()
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 24f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { committed += it }
        }
        // Keep main-thread publication queued while the worker finishes three contacts.
        // This models a busy presentation thread without relying on scheduler timing.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(3) { index ->
                val x = 80f + index * 100f
                for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(0L, index * 10L + action, action, x, 200f, 0)
                    try { view.onTouchEvent(event) } finally { event.recycle() }
                }
                view.awaitV5StrokeWorkerIdleForTest()
            }
            assertEquals(3L, surface.revision)
            assertTrue("Callbacks remain queued while main is occupied", committed.isEmpty())
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        assertEquals(listOf(1L, 2L, 3L), committed.map { it.snapshot.revision })
        val bounds = PixelRect(0, 0, 600, 600)
        val finalPixels = committed.last().snapshot.copyPremultipliedPixels(bounds)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(4L, committed.last().snapshot.revision)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(5L, committed.last().snapshot.revision)
        org.junit.Assert.assertArrayEquals(finalPixels, committed.last().snapshot.copyPremultipliedPixels(bounds))
    }

    @Test fun rapidConsecutiveContactsDoNotWaitForThePreviousPublicationOnDown() {
        val surface = PixelTileSurface(1200, 900)
        var commits = 0
        val view = createCanvas {
            layout(0, 0, 800, 600)
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 24f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commits++ }
        }
        val downTimes = ArrayList<Float>(32)

        repeat(32) { index ->
            val x = 90f + (index % 8) * 82f
            val y = 90f + (index / 8) * 120f
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            send(view, MotionEvent.ACTION_DOWN, x, y, index * 4L, awaitPublication = false)
            downTimes += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            send(view, MotionEvent.ACTION_UP, x + 2f, y + 2f, index * 4L + 2L, awaitPublication = false)
        }
        settlePublishedStroke(view)

        val ordered = downTimes.sorted()
        val p95 = ordered[(ordered.size * .95f).toInt().coerceAtMost(ordered.lastIndex)]
        android.util.Log.i("InkloomPerf", "v5_rapid_contact_down_p95_ms=$p95")
        assertTrue("A new DOWN must never wait for the prior publish: p95=${p95}ms", p95 < 8.33f)
        assertEquals("Every physical contact publishes once", 32, commits)
        assertEquals(32L, surface.revision)
    }

    @Test fun oneV5PaintGestureIsOneByteExactUndoAndRedoTransaction() {
        val surface = PixelTileSurface(600, 600)
        val view = createCanvas {
            tool = EditorTool.PEN
            activeLayerId = "paint"
            installV5TestInk(size = 42f, color = Color.MAGENTA)
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }

        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 200f, 200f, 5L)
        send(view, MotionEvent.ACTION_UP, 280f, 200f, 10L)
        val page = PixelRect(0, 0, 600, 600)
        val committed = surface.snapshot().copyPremultipliedPixels(page)
        assertTrue("The gesture must publish visible pixels", committed.any { it.toInt() != 0 })

        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        val undone = surface.snapshot().copyPremultipliedPixels(page)
        assertTrue("Undo must remove the complete gesture atomically", undone.all { it.toInt() == 0 })

        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        val redone = surface.snapshot().copyPremultipliedPixels(page)
        assertTrue("Redo must restore the exact committed tile bytes", committed.contentEquals(redone))
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
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
        }

        send(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
        send(view, MotionEvent.ACTION_UP, 200f, 200f, 5L)

        val result = requireNotNull(committed)
        assertTrue("The authored black tip core must erase the raster", Color.alpha(result.colorAtSource(300, 300)) < 16)
        assertTrue(
            "Pixels outside the authored narrow tip must remain untouched in the document surface",
            Color.alpha(surface.pixelAt(400, 300)) > 240,
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
                installV5TestInk(
                    size = 400f,
                    tipAssetPaths = listOf(tipFile.absolutePath),
                    textureGrain = TextureGrainSettings(
                        assetPath = grainFile.absolutePath,
                        density = 1f,
                        scale = 100f,
                        movement = movement,
                    ),
                )
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
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

    @Test fun fillAfterPenUpPresentsTheNewPixelsOnTheFirstFrame() {
        val surface = PixelTileSurface(600, 600)
        val view = createCanvas {
            activeLayerId = "paint"
            layerOrder = listOf("paint")
            pagePaperColor = Color.WHITE
            installV5TestInk(size = 48f)
            setRasterLayerSurfaces(mapOf("paint" to surface))
        }
        // Commit a stroke without presenting a frame, retaining its landed preview.
        sendStylus(view, MotionEvent.ACTION_DOWN, 200f, 200f, 0L)
        sendStylus(view, MotionEvent.ACTION_UP, 200f, 200f, 8L)
        val mask = app.inkloom.engine.v5.surface.MaskTileSurface(600, 600, initialDefaultAlpha = 255)
        val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(view.paintFillIntoActiveRaster(mask.snapshot(), Color.BLUE.toUInt().toLong(), 1f, 0))
                assertEquals(Color.BLUE, surface.pixelAt(75, 75))
                view.draw(Canvas(output))
                assertEquals("The first submitted frame must include the fill, not the previous pen-up preview",
                    Color.BLUE, output.getPixel(50, 50))
                view.draw(Canvas(output))
                assertEquals(Color.BLUE, output.getPixel(50, 50))
            }
        } finally {
            output.recycle()
        }
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

    private fun send(
        view: InkCanvasView,
        action: Int,
        x: Float,
        y: Float,
        time: Long,
        awaitPublication: Boolean = true,
    ) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .1f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
        if (awaitPublication && action == MotionEvent.ACTION_UP) settlePublishedStroke(view)
    }

    private fun sendStylus(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
        if (action == MotionEvent.ACTION_UP) settlePublishedStroke(view)
    }

    private fun settlePublishedStroke(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
    }

    private fun Bitmap.hasVisibleAlpha(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { Color.alpha(it) > 0 }
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
