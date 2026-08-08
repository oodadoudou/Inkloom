package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeTileKey
import app.inkloom.engine.v2.render.EngineV2ActiveStrokeSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasTileCacheTest {
    @Test fun longLiveStrokeCoalescesIntoOneV2SurfaceSubmissionPerFrame() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val view = InkCanvasView(context).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.PEN
            }
            val surface = RecordingActiveSurface(view)
            view.activeStrokeSurface = surface
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, 200f, 10L)
            repeat(250) { point ->
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 100f + point * 3f, 200f + (point % 7), 20L + point)
            }
            view.flushEngineV2ActiveFrame()

            assertEquals("All MOVE events before one display frame must coalesce", 1, surface.renderCount)
            assertTrue("The V2 packet must contain the modeled stroke", surface.latest.sumOf { it.points.size } > 100)
            view.dispatchStylus(MotionEvent.ACTION_UP, 850f, 200f, 300L)
        }
    }

    @Test fun consecutiveLongStrokesUseOnlyTheV2SurfaceLifecycle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.PEN
            }
            val surface = RecordingActiveSurface(view)
            view.activeStrokeSurface = surface
            repeat(2) { stroke ->
                val y = 200f + stroke * 180f
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, y, 1_000L + stroke * 1_000L)
                repeat(80) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 100f + point * 8f, y, 1_020L + stroke * 1_000L + point)
                }
                view.flushEngineV2ActiveFrame()
                view.dispatchStylus(MotionEvent.ACTION_UP, 760f, y, 1_900L + stroke * 1_000L)
            }

            assertEquals(2, surface.commitCount)
            assertTrue("Every contact must reset the transient surface", surface.cancelCount >= 2)
        }
    }

    @Test fun rapidCommittedStrokesAppendToCleanTilesWithoutReplayingOlderInk() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.PEN
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                // Materialize a clean empty tile surface before the first contact.
                view.draw(Canvas(bitmap))
                val fullTileRenders = mutableListOf<Int>()
                view.onTileRendered = { _, strokeCount -> fullTileRenders += strokeCount }

                repeat(6) { stroke ->
                    val y = 120f + stroke * 125f
                    view.dispatchStylus(MotionEvent.ACTION_DOWN, 120f, y, 1_000L + stroke * 100L)
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 700f, y + 8f, 1_020L + stroke * 100L)
                    view.dispatchStylus(MotionEvent.ACTION_UP, 820f, y + 10f, 1_040L + stroke * 100L)
                    view.draw(Canvas(bitmap))
                }

                assertTrue("Rapid additions must not trigger a full historical tile replay: $fullTileRenders", fullTileRenders.isEmpty())
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun stampedBrushPreviewMatchesItsCommittedStroke() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val tipFile = File.createTempFile("inkloom-live-tip", ".png", context.cacheDir)
            val sourceTip = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val live = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            val committed = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                Canvas(sourceTip).drawCircle(12f, 12f, 10f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                FileOutputStream(tipFile).use { sourceTip.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val view = InkCanvasView(context).apply {
                    layout(0, 0, 1024, 1024)
                    tool = EditorTool.PEN
                    tipAssetPath = tipFile.absolutePath
                    tipAssetPaths = listOf(tipFile.absolutePath)
                    baseWidth = 92f
                    brushSpacing = .22f
                }
                view.activeStrokeSurface = RecordingActiveSurface(view, live)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 120f, 260f, 10L)
                repeat(96) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 128f + point * 8f, 260f + (point % 9) * 6f, 20L + point)
                    if (point % 16 == 15) view.flushEngineV2ActiveFrame()
                }
                view.dispatchStylus(MotionEvent.ACTION_UP, 900f, 300f, 140L)
                view.draw(Canvas(committed))

                val livePixels = IntArray(1024 * 1024)
                val committedPixels = IntArray(1024 * 1024)
                live.getPixels(livePixels, 0, 1024, 0, 0, 1024, 1024)
                committed.getPixels(committedPixels, 0, 1024, 0, 0, 1024, 1024)
                assertTrue("A stamped brush must not visibly settle or change on pen-up", livePixels.contentEquals(committedPixels))
            } finally {
                sourceTip.recycle()
                live.recycle()
                committed.recycle()
                tipFile.delete()
            }
        }
    }

    @Test fun vectorBrushPreviewMatchesItsCommittedStrokeAcrossCheckpoints() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val live = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            val committed = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                val view = InkCanvasView(context).apply {
                    layout(0, 0, 1024, 1024)
                    tool = EditorTool.PEN
                    baseWidth = 28f
                }
                view.activeStrokeSurface = RecordingActiveSurface(view, live)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 120f, 520f, 10L)
                repeat(96) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 128f + point * 8f, 520f + (point % 9) * 3f, 20L + point)
                    if (point % 16 == 15) view.flushEngineV2ActiveFrame()
                }
                view.dispatchStylus(MotionEvent.ACTION_UP, 900f, 550f, 140L)
                view.draw(Canvas(committed))

                val livePixels = IntArray(1024 * 1024)
                val committedPixels = IntArray(1024 * 1024)
                live.getPixels(livePixels, 0, 1024, 0, 0, 1024, 1024)
                committed.getPixels(committedPixels, 0, 1024, 0, 0, 1024, 1024)
                val visiblePixels = livePixels.indices.count { index ->
                    (livePixels[index] ushr 24) > 24 || (committedPixels[index] ushr 24) > 24
                }
                val shapeDifferences = livePixels.indices.count { index ->
                    ((livePixels[index] ushr 24) > 24) != ((committedPixels[index] ushr 24) > 24)
                }
                assertTrue(
                    "Vector stroke shape changed at $shapeDifferences of $visiblePixels visible pixels",
                    shapeDifferences <= (visiblePixels * .02f).toInt().coerceAtLeast(16),
                )
            } finally {
                live.recycle()
                committed.recycle()
            }
        }
    }

    @Test fun importedSoftBrushKeepsInteractiveFrameTimesOnTheRenderHotPath() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val tipFile = File.createTempFile("inkloom-performance-tip", ".png", context.cacheDir)
            val sourceTip = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                Canvas(sourceTip).drawCircle(16f, 16f, 14f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                FileOutputStream(tipFile).use { sourceTip.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val view = InkCanvasView(context).apply {
                    layout(0, 0, 1024, 1024)
                    tool = EditorTool.PEN
                    tipAssetPath = tipFile.absolutePath
                    tipAssetPaths = listOf(tipFile.absolutePath)
                    baseWidth = 74f
                    brushSpacing = .12f
                    brushBlur = .2f
                }
                view.activeStrokeSurface = RecordingActiveSurface(view, output)
                // Warm the decoded brush asset before measuring the same path used after selection.
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 180f, 1L)
                view.flushEngineV2ActiveFrame()
                view.dispatchStylus(MotionEvent.ACTION_UP, 82f, 180f, 2L)
                output.eraseColor(android.graphics.Color.TRANSPARENT)

                val frameTimes = ArrayList<Long>(120)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 90f, 420f, 10L)
                repeat(120) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 96f + point * 7f, 420f + (point % 11) * 3f, 20L + point)
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.flushEngineV2ActiveFrame()
                    frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                }
                view.dispatchStylus(MotionEvent.ACTION_UP, 940f, 450f, 200L)

                val sorted = frameTimes.sorted()
                val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
                assertTrue("Imported soft-brush p95 frame was ${p95Ms}ms", p95Ms < 100f)
            } finally {
                sourceTip.recycle()
                output.recycle()
                tipFile.delete()
            }
        }
    }

    private class RecordingActiveSurface(
        private val view: InkCanvasView,
        private val bitmap: Bitmap? = null,
    ) : EngineV2ActiveStrokeSurface {
        var renderCount = 0
        var commitCount = 0
        var cancelCount = 0
        var latest: List<InkStroke> = emptyList()

        override fun render(strokes: List<InkStroke>) {
            renderCount++
            latest = strokes
            bitmap?.let {
                it.eraseColor(android.graphics.Color.TRANSPARENT)
                view.drawEngineV2ActiveStrokes(Canvas(it), strokes)
            }
        }

        override fun commit(strokes: List<InkStroke>) {
            commitCount++
            latest = strokes
        }

        override fun cancel() {
            cancelCount++
            latest = emptyList()
            bitmap?.eraseColor(android.graphics.Color.TRANSPARENT)
        }
    }

    @Test fun strokeCrossingTileSeamsRendersWithoutPixelGaps() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                setDocumentStrokes(
                    listOf(
                        InkStroke(
                            id = "seam",
                            width = 24f,
                            points = listOf(StrokePoint(.05f, .5f), StrokePoint(.95f, .5f)),
                        ),
                    ),
                    "tile:seam",
                )
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                for (x in 64..960 step 16) {
                    assertTrue("Missing upper seam pixel at $x", bitmap.getPixel(x, 511) ushr 24 != 0)
                    assertTrue("Missing lower seam pixel at $x", bitmap.getPixel(x, 512) ushr 24 != 0)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun erasingOnFiveThousandStrokePageRerendersOnlyLocalTiles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val strokes = List(5_000) { index ->
                val column = index % 100
                val row = index / 100
                InkStroke(
                    id = "stroke-$index",
                    width = 2f,
                    points = listOf(StrokePoint((column + .5f) / 100f, (row + .5f) / 50f)),
                )
            }
            val renderedTiles = mutableListOf<Pair<StrokeTileKey, Int>>()
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.ERASER
                eraserWidth = 5f
                setDocumentStrokes(strokes, "tile:large")
                onTileRendered = { key, count -> renderedTiles += key to count }
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val initialTileCount = renderedTiles.size
                renderedTiles.clear()

                view.dispatchStylus(MotionEvent.ACTION_DOWN, 107.5f, 112.5f, 10L)
                view.dispatchStylus(MotionEvent.ACTION_UP, 107.5f, 112.5f, 20L)
                view.draw(Canvas(bitmap))

                assertTrue("Expected a multi-tile initial cache", initialTileCount > 1)
                assertTrue("Local erase rerendered ${renderedTiles.size} of $initialTileCount tiles", renderedTiles.size in 1 until initialTileCount)
                assertTrue("Local erase replayed ${renderedTiles.sumOf { it.second }} strokes", renderedTiles.sumOf { it.second } < strokes.size)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun movingInkOnFiveThousandStrokePageInvalidatesOldAndNewTilesOnly() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val strokes = List(5_000) { index ->
                val column = index % 100
                val row = index / 100
                InkStroke(
                    id = "move-$index",
                    width = 2f,
                    points = listOf(StrokePoint((column + .5f) / 100f, (row + .5f) / 50f)),
                )
            }
            val renderedTiles = mutableListOf<Pair<StrokeTileKey, Int>>()
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.SELECT
                setDocumentStrokes(strokes, "tile:move-large")
                onTileRendered = { key, count -> renderedTiles += key to count }
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val initialTileCount = renderedTiles.size
                renderedTiles.clear()

                view.drawLasso(.095f, .09f, .115f, .13f)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 107.5f, 112.5f, 60L)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 700f, 700f, 70L)
                view.dispatchStylus(MotionEvent.ACTION_UP, 700f, 700f, 80L)
                view.draw(Canvas(bitmap))

                assertTrue("Local move rerendered ${renderedTiles.size} of $initialTileCount tiles", renderedTiles.size in 2 until initialTileCount)
                assertTrue("Local move replayed ${renderedTiles.sumOf { it.second }} strokes", renderedTiles.sumOf { it.second } < strokes.size)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun InkCanvasView.drawLasso(left: Float, top: Float, right: Float, bottom: Float) {
        dispatchStylus(MotionEvent.ACTION_DOWN, left * width, top * height, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * width, top * height, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * width, bottom * height, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, left * width, bottom * height, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, left * width, top * height, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .8f
            size = .01f
            touchMajor = 1f
        }
        MotionEvent.obtain(
            10L,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        ).also { event ->
            try {
                onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }
}
