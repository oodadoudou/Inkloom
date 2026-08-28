package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
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
                    val surface = PixelTileSurface(SIZE, SIZE)
                    val view = InkCanvasView(instrumentation.targetContext).apply {
                        layout(0, 0, SIZE, SIZE)
                        activeLayerId = "paint"
                        setRasterLayerSurfaces(mapOf("paint" to surface))
                        onRasterLayerCommitted = { }
                        applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize, brush.defaultOpacity, Color.BLACK, EraserPreset.NORMAL_FREE)
                        prepareBrushAssetsNow()
                    }
                    view.draw(Canvas(stable)) // Materialize clean committed tiles before timing.

                    // Debug ART compilation makes the very first renderer invocation noisier than
                    // steady-state input. Keep a bounded cold-contact gate, then enforce the real
                    // one-frame contract on the immediately following stroke.
                    val cold = measureStroke(view, live, 260f, 10L)
                    val steady = listOf(
                        measureStroke(view, live, 430f, 1_000L),
                        measureStroke(view, live, 590f, 2_000L),
                        measureStroke(view, live, 750f, 3_000L),
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

    private fun measureStroke(view: InkCanvasView, live: Bitmap, centerY: Float, startTime: Long): StrokeTiming {
        val frameTimes = ArrayList<Long>(POINTS)
        var terminalX = 110f
        var terminalY = centerY
        view.dispatchStylus(MotionEvent.ACTION_DOWN, 110f, centerY, startTime)
        repeat(POINTS) { index ->
            val cycle = index / 80
            val within = index % 80
            val travel = if (cycle % 2 == 0) within else 79 - within
            terminalX = 120f + travel * 9.5f
            terminalY = centerY + kotlin.math.sin(index * .16f) * 52f
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            view.dispatchStylus(
                MotionEvent.ACTION_MOVE,
                terminalX,
                terminalY,
                startTime + 10L + index,
            )
            view.awaitV5StrokeWorkerIdleForTest()
            live.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(live))
            frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
        }
        val commitStarted = android.os.SystemClock.elapsedRealtimeNanos()
        view.dispatchStylus(MotionEvent.ACTION_UP, terminalX, terminalY, startTime + POINTS + 20L)
        view.awaitV5StrokeWorkerIdleForTest()
        return StrokeTiming(frameTimes, (android.os.SystemClock.elapsedRealtimeNanos() - commitStarted) / 1_000_000f)
    }

    private data class StrokeTiming(val frameTimes: List<Long>, val commitMs: Float)

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
