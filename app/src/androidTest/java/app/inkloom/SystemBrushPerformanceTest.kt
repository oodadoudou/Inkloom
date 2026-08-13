package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.engine.v3.render.ActiveStrokeTailPlanner
import app.inkloom.model.EditorTool
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBrushPerformanceTest {
    @Test fun classicPs19HasNoPeriodicJoinBeads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val points = (0..80).map { index -> StrokePoint(.1f + index * .01f, .5f, pressure = .55f, timeMs = index.toLong()) }
            val stroke = InkStroke(
                brushId = "system-ps19",
                points = points,
                colorArgb = 0xFF111111,
                width = 19f,
                flow = .55f,
                sizePressureEnabled = false,
                opacityPressureEnabled = false,
                flowPressureEnabled = false,
            )
            val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
            try {
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 1000, 1000)
                    setDocumentStrokes(listOf(stroke), "ps19-continuous")
                }
                view.draw(Canvas(bitmap))
                val alphas = (140..860 step 5).map { x -> Color.alpha(bitmap.getPixel(x, 500)) }
                assertTrue("Continuous PS19 centerline alpha varied like overlapping round stamps: ${alphas.minOrNull()}..${alphas.maxOrNull()}", (alphas.maxOrNull() ?: 0) - (alphas.minOrNull() ?: 0) <= 2)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun representativeFactoryBrushesKeepLiveAndCommitWorkInsideAFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val failures = mutableListOf<String>()
            listOf(
                "system-g-pen",
                "system-technical-pencil",
                "system-h-pencil",
                "system-hb-pencil",
                "system-2b-pencil",
                "system-6b-pencil",
                "system-ps19",
                "system-soft-airbrush",
                "system-decoration-sparkle",
            ).forEach { brushId ->
                val brush = requireNotNull(SystemBrushCatalog.byId(brushId))
                val live = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                val stable = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                try {
                    val seed = InkStroke(
                        id = "seed-$brushId",
                        points = listOf(StrokePoint(.05f, .92f), StrokePoint(.95f, .92f)),
                    )
                    val view = InkCanvasView(instrumentation.targetContext).apply {
                        layout(0, 0, SIZE, SIZE)
                        setDocumentStrokes(listOf(seed), "system-perf-$brushId")
                        applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize, brush.defaultOpacity, Color.BLACK, EraserPreset.NORMAL_FREE)
                        prepareBrushAssetsNow()
                    }
                    view.draw(Canvas(stable)) // Materialize clean committed tiles before timing.
                    view.activeStrokeSurface = IncrementalBenchmarkSurface(view, live)

                    // Debug ART compilation makes the very first renderer invocation noisier than
                    // steady-state input. Keep a bounded cold-contact gate, then enforce the real
                    // one-frame contract on the immediately following stroke.
                    val cold = measureStroke(view, 260f, 10L)
                    val steady = listOf(
                        measureStroke(view, 430f, 1_000L),
                        measureStroke(view, 590f, 2_000L),
                        measureStroke(view, 750f, 3_000L),
                    )
                    val sorted = steady.flatMap(StrokeTiming::frameTimes).sorted()
                    val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
                    val commits = steady.map(StrokeTiming::commitMs).sorted()
                    val medianCommitMs = commits[commits.size / 2]
                    val worstCommitMs = commits.last()
                    android.util.Log.i("InkloomPerf", "system_brush=$brushId cold_commit_ms=${cold.commitMs} live_p95_ms=$p95Ms median_commit_ms=$medianCommitMs worst_commit_ms=$worstCommitMs")
                    if (cold.commitMs >= COLD_COMMIT_BUDGET_MS) failures += "$brushId cold commit ${cold.commitMs}ms"
                    if (p95Ms >= FRAME_BUDGET_MS) failures += "$brushId live p95 ${p95Ms}ms"
                    if (medianCommitMs >= FRAME_BUDGET_MS) failures += "$brushId median commit ${medianCommitMs}ms"
                    if (worstCommitMs >= WORST_COMMIT_BUDGET_MS) failures += "$brushId worst commit ${worstCommitMs}ms"
                } finally {
                    live.recycle()
                    stable.recycle()
                }
            }
            assertTrue(failures.joinToString(separator = "; "), failures.isEmpty())
        }
    }

    private fun measureStroke(view: InkCanvasView, centerY: Float, startTime: Long): StrokeTiming {
        val frameTimes = ArrayList<Long>(POINTS)
        view.dispatchStylus(MotionEvent.ACTION_DOWN, 110f, centerY, startTime)
        repeat(POINTS) { index ->
            val cycle = index / 80
            val within = index % 80
            val travel = if (cycle % 2 == 0) within else 79 - within
            view.dispatchStylus(
                MotionEvent.ACTION_MOVE,
                120f + travel * 9.5f,
                centerY + kotlin.math.sin(index * .16f) * 52f,
                startTime + 10L + index,
            )
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            view.flushActiveStrokeFrame()
            frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
        }
        val commitStarted = android.os.SystemClock.elapsedRealtimeNanos()
        view.dispatchStylus(MotionEvent.ACTION_UP, 500f, centerY + 20f, startTime + POINTS + 20L)
        return StrokeTiming(frameTimes, (android.os.SystemClock.elapsedRealtimeNanos() - commitStarted) / 1_000_000f)
    }

    private data class StrokeTiming(val frameTimes: List<Long>, val commitMs: Float)

    private class IncrementalBenchmarkSurface(
        private val view: InkCanvasView,
        private val bitmap: Bitmap,
    ) : ActiveStrokeCompositor {
        private val planner = ActiveStrokeTailPlanner()

        override fun render(strokes: List<InkStroke>) {
            val plan = planner.incremental(strokes)
            if (plan.clearBeforeDraw) bitmap.eraseColor(Color.TRANSPARENT)
            if (plan.strokes.isNotEmpty()) {
                view.drawActiveStrokes(Canvas(bitmap), plan.strokes, plan.incrementalStampDraw)
            }
        }

        override fun commit(strokes: List<InkStroke>) = render(strokes)

        override fun cancel() {
            bitmap.eraseColor(Color.TRANSPARENT)
            planner.reset()
            view.resetActiveRenderState()
        }
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .78f
            size = .01f
        })
        MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try {
                onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private companion object {
        const val SIZE = 1024
        const val POINTS = 240
        const val FRAME_BUDGET_MS = 16.7f
        const val COLD_COMMIT_BUDGET_MS = 75f
        const val WORST_COMMIT_BUDGET_MS = 40f
    }
}
