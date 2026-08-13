package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeTileKey
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.engine.v3.scene.SparseTileScene
import app.inkloom.model.EditorTool
import app.inkloom.model.SymmetryMode
import app.inkloom.ui.InkCanvasView
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasTileCacheTest {
    @Test fun sparse8192SceneRetainsOnlyOccupiedGpuTiles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scene = SparseTileScene(4_000_000)
            val strokes = listOf(
                InkStroke(id = "top-left", width = 12f, points = listOf(StrokePoint(.02f, .02f), StrokePoint(.04f, .04f))),
                InkStroke(id = "bottom-right", width = 12f, points = listOf(StrokePoint(.96f, .96f), StrokePoint(.98f, .98f))),
            )
            val output = RenderNode("Inkloom.V3.SparseTileTest").apply { setPosition(0, 0, 8192, 8192) }
            val recording = output.beginRecording()
            try {
                assertTrue(recording.isHardwareAccelerated)
                assertTrue(scene.draw(recording, strokes, 8192, 8192) { canvas, candidates ->
                    candidates.forEach { stroke ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.BLACK
                            strokeWidth = stroke.width
                        }
                        val points = stroke.points
                        if (points.size >= 2) canvas.drawLine(
                            points.first().x * 8192f,
                            points.first().y * 8192f,
                            points.last().x * 8192f,
                            points.last().y * 8192f,
                            paint,
                        )
                    }
                })
            } finally {
                output.endRecording()
            }
            val snapshot = scene.debugSnapshot()
            assertTrue("Sparse scene allocated ${snapshot.hardwareTileCount} tiles", snapshot.hardwareTileCount in 2..8)
            assertEquals(2, snapshot.hardwareStrokeCount)
            assertEquals(0, snapshot.tiles.size)
            assertTrue(snapshot.approximateGpuDisplayListBytes > 0L)
            scene.close()
            output.discardDisplayList()
        }
    }

    @Test fun fiftyLayer8192SceneKeepsGpuDisplayListsSparseAndBounded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scene = SparseTileScene(4_000_000)
            val strokes = List(50) { index ->
                val column = index % 5
                val row = index / 5
                val x = .02f + column * .012f
                val y = .02f + row * .009f
                InkStroke(
                    id = "layer-stroke-$index",
                    layerId = "layer-$index",
                    width = 10f,
                    points = listOf(StrokePoint(x, y), StrokePoint(x + .006f, y + .004f)),
                )
            }
            val output = RenderNode("Inkloom.V3.FiftyLayerSparseSceneTest").apply { setPosition(0, 0, 8192, 8192) }
            val recording = output.beginRecording()
            try {
                assertTrue(recording.isHardwareAccelerated)
                assertTrue(scene.draw(recording, strokes, 8192, 8192) { canvas, candidates ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        strokeWidth = 10f
                    }
                    candidates.forEach { stroke ->
                        canvas.drawLine(
                            stroke.points.first().x * 8192f,
                            stroke.points.first().y * 8192f,
                            stroke.points.last().x * 8192f,
                            stroke.points.last().y * 8192f,
                            paint,
                        )
                    }
                })
            } finally {
                output.endRecording()
            }
            val snapshot = scene.debugSnapshot()
            assertEquals(50, snapshot.hardwareStrokeCount)
            assertTrue("50 sparse layers allocated ${snapshot.hardwareTileCount} tiles", snapshot.hardwareTileCount in 1..12)
            assertEquals("Production GPU rendering must not retain software tiles", 0, snapshot.tiles.size)
            assertTrue(
                "Sparse 50-layer display lists retained ${snapshot.approximateGpuDisplayListBytes} bytes",
                snapshot.approximateGpuDisplayListBytes in 1 until 16L * 1024L * 1024L,
            )
            scene.close()
            output.discardDisplayList()
        }
    }

    @Test fun softStrokeTilesRenderWithAnExpandedTransientGutter() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scene = SparseTileScene(4_000_000)
            val output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            val clips = mutableListOf<android.graphics.Rect>()
            val stroke = InkStroke(
                id = "gutter",
                width = 180f,
                blur = 80f,
                points = listOf(StrokePoint(.5f, .5f)),
            )
            try {
                assertTrue(scene.draw(Canvas(output), listOf(stroke), 1024, 1024) { canvas, _ ->
                    clips += canvas.clipBounds
                })
                assertTrue(
                    "A brush with a wide influence radius must render beyond a 512px tile core: $clips",
                    clips.any { it.width() > 512 || it.height() > 512 },
                )
            } finally {
                scene.close()
                output.recycle()
            }
        }
    }

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
            view.flushActiveStrokeFrame()

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
                view.flushActiveStrokeFrame()
                view.dispatchStylus(MotionEvent.ACTION_UP, 760f, y, 1_900L + stroke * 1_000L)
            }

            assertEquals(2, surface.commitCount)
            assertTrue("Every contact must reset the transient surface", surface.cancelCount >= 2)
        }
    }

    @Test fun symmetryMirrorsKeepStableIdsAcrossLiveFrames() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.PEN
                symmetryMode = SymmetryMode.QUADRANT
            }
            val surface = RecordingActiveSurface(view)
            view.activeStrokeSurface = surface
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 180f, 220f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 260f, 300f, 20L)
            view.flushActiveStrokeFrame()
            val firstIds = surface.latest.map(InkStroke::id)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 340f, 360f, 30L)
            view.flushActiveStrokeFrame()
            val secondIds = surface.latest.map(InkStroke::id)

            assertEquals(4, firstIds.size)
            assertEquals("Mirrored ink must append to existing front-buffer strokes", firstIds, secondIds)
            assertEquals("No frame may introduce duplicate transient strokes", secondIds.size, secondIds.toSet().size)
            view.dispatchStylus(MotionEvent.ACTION_UP, 400f, 400f, 40L)
        }
    }

    @Test fun rapidCommittedStrokesNeverReplayHistoricalInkWhenAllocatingSparseTiles() {
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

                assertTrue(
                    "A newly occupied sparse tile may render its first stroke, but must not replay growing history: $fullTileRenders",
                    fullTileRenders.all { it <= 1 },
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun stampedBrushStreamsEveryCheckpointAndCommitsVisibleInk() {
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
                val activeSurface = RecordingActiveSurface(view, live)
                view.activeStrokeSurface = activeSurface
                view.prepareBrushAssetsNow()
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 120f, 260f, 10L)
                repeat(96) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 128f + point * 8f, 260f + (point % 9) * 6f, 20L + point)
                    if (point % 16 == 15) view.flushActiveStrokeFrame()
                }
                view.dispatchStylus(MotionEvent.ACTION_UP, 900f, 300f, 140L)
                view.draw(Canvas(committed))

                val committedPixels = IntArray(1024 * 1024)
                committed.getPixels(committedPixels, 0, 1024, 0, 0, 1024, 1024)
                val committedVisible = committedPixels.count { (it ushr 24) > 0 }
                assertTrue("Every requested live checkpoint must reach the active surface", activeSurface.renderCount >= 6)
                assertEquals("Pen-up must commit through the same V2 surface", 1, activeSurface.commitCount)
                assertTrue("The final active packet must retain the complete modelled stroke", activeSurface.latest.singleOrNull()?.points?.size ?: 0 > 90)
                assertTrue("The committed imported stroke must remain visible", committedVisible > 500)
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
                    if (point % 16 == 15) view.flushActiveStrokeFrame()
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
                view.flushActiveStrokeFrame()
                view.dispatchStylus(MotionEvent.ACTION_UP, 82f, 180f, 2L)
                output.eraseColor(android.graphics.Color.TRANSPARENT)

                val frameTimes = ArrayList<Long>(480)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 90f, 420f, 10L)
                repeat(480) { point ->
                    val cycle = point / 160
                    val within = point % 160
                    val travel = if (cycle % 2 == 0) within else 159 - within
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 112f + travel * 5f, 420f + kotlin.math.sin(point * .12f) * 34f, 20L + point)
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.flushActiveStrokeFrame()
                    frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                }
                view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 450f, 540L)

                val sorted = frameTimes.sorted()
                val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
                android.util.Log.i("InkloomPerf", "imported_soft_brush_480_point_p95_ms=$p95Ms")
                assertTrue("Imported soft-brush p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
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
    ) : ActiveStrokeCompositor {
        var renderCount = 0
        var commitCount = 0
        var cancelCount = 0
        var latest: List<InkStroke> = emptyList()

        override fun render(strokes: List<InkStroke>) {
            renderCount++
            latest = strokes
            bitmap?.let {
                it.eraseColor(android.graphics.Color.TRANSPARENT)
                view.drawActiveStrokes(Canvas(it), strokes)
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
                assertTrue(view.enterFreeTransform())
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
