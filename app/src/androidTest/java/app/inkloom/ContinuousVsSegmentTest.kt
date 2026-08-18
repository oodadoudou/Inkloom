package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compares the default pen's independent BUTT-capped segment compositing (drawLineSegment, used
 * by system-pen) against the single-stroke continuous ribbon (drawContinuousRoundStroke, used by
 * system-ps19) with IDENTICAL StrokeInput. Measures edge scalloping, coverage pumping and relative
 * render cost. The two rasters are produced by the same view and same stroke state; only the
 * brushId selects which raster semantic runs.
 */
@RunWith(AndroidJUnit4::class)
class ContinuousVsSegmentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewportWidth = 900
    private val viewportHeight = 900

    private fun stroke(brushId: String, width: Float, points: List<StrokePoint>) = InkStroke(
        id = "compare",
        brushId = brushId,
        points = points,
        width = width,
        spacing = .12f,
        sizePressureEnabled = true,
        sizePressureMinimum = .35f,
        colorArgb = 0xFF000000,
    )

    private fun sCurve() = (0..500).map { index ->
        val t = index / 500f
        StrokePoint(.1f + t * .8f, .5f + .2f * sin(t * (PI.toFloat() * 3f)), pressure = .6f, timeMs = (index * 2L).toLong())
    }

    private fun tightTurn() = (0..120).map { index ->
        val t = index / 120f
        val angle = t * 1.6f * PI.toFloat()
        StrokePoint(.5f + .22f * cos(angle), .5f + .22f * sin(angle), pressure = .6f, timeMs = (index * 2L).toLong())
    }

    @Test
    fun continuousRibbonScallopsFarLessThanSegmentsOnCurves() {
        val width = 5f
        val segment = scalloping(stroke("system-g-pen", width, sCurve()))
        val ribbon = scalloping(stroke("system-pen", width, sCurve()))
        println("COMPARE scurve segment=$segment ribbon=$ribbon")
        assertTrue(
            "Continuous ribbon must scallop far less than segment pens (segment=${segment.pumpAmplitude}, ribbon=${ribbon.pumpAmplitude})",
            ribbon.pumpAmplitude < segment.pumpAmplitude * .6f,
        )
    }

    @Test
    fun continuousRibbonRemovesSegmentGapsOnTightTurns() {
        val width = 5f
        val segment = scalloping(stroke("system-g-pen", width, tightTurn()))
        val ribbon = scalloping(stroke("system-pen", width, tightTurn()))
        println("COMPARE turn segment=$segment ribbon=$ribbon")
        assertTrue(
            "Segment path must show scalloping on tight turns (pump=${segment.pumpAmplitude})",
            segment.pumpAmplitude > .4f,
        )
        assertTrue(
            "Continuous ribbon must be cleaner than segments (ribbon=${ribbon.pumpAmplitude})",
            ribbon.pumpAmplitude < segment.pumpAmplitude,
        )
    }

    @Test
    fun continuousRibbonIsNotSlowerThanSegments() {
        val width = 24f
        val points = sCurve()
        val segmentMs = renderCost(stroke("system-g-pen", width, points), 60)
        val ribbonMs = renderCost(stroke("system-pen", width, points), 60)
        println("COMPARE perf segment=${"%.1f".format(segmentMs)}ms/60 ribbon=${"%.1f".format(ribbonMs)}ms/60")
        assertTrue(
            "Continuous ribbon must not be meaningfully slower (segment=${segmentMs}ms, ribbon=${ribbonMs}ms)",
            ribbonMs < segmentMs * 2.5f,
        )
    }

    private fun renderCost(stroke: InkStroke, rounds: Int): Float {
        val w = viewportWidth
        val h = viewportHeight
        var elapsed = 0L
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            repeat(3) { view.setDocumentStrokes(listOf(stroke), "perf-$it"); view.draw(Canvas(bitmap)) }
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            repeat(rounds) { view.setDocumentStrokes(listOf(stroke), "perf"); view.draw(Canvas(bitmap)) }
            elapsed = android.os.SystemClock.elapsedRealtimeNanos() - started
            bitmap.recycle()
        }
        return elapsed / 1_000_000f
    }

    private data class ScallopStats(val widthMean: Double, val pumpAmplitude: Float, val crossings: Int, val gapSamples: Int) {
        override fun toString() = "widthMean=${"%.1f".format(widthMean)} pump=${"%.2f".format(pumpAmplitude)} crossings=$crossings gaps=$gapSamples"
    }

    private fun scalloping(stroke: InkStroke): ScallopStats {
        val w = viewportWidth
        val h = viewportHeight
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "compare")
            view.draw(Canvas(bitmap))
        }
        val widths = crossSectionWidths(bitmap, stroke)
        bitmap.recycle()
        if (widths.isEmpty()) return ScallopStats(0.0, 0f, 0, 0)
        val mean = widths.average()
        if (mean <= .0001) return ScallopStats(mean, 0f, 0, widths.count { it <= 1 })
        val smoothed = IntArray(widths.size) { index ->
            val from = (index - 4).coerceAtLeast(0)
            val to = (index + 4).coerceAtMost(widths.lastIndex)
            (from..to).map { widths[it] }.average().toInt()
        }
        val deviations = widths.indices.map { abs(widths[it] - smoothed[it]) }
        val pumpAmplitude = (deviations.maxOrNull() ?: 0) / mean.toFloat()
        val crossings = (1 until widths.size).count { index ->
            (widths[index] - smoothed[index] > 0) != (widths[index - 1] - smoothed[index - 1] > 0)
        }
        return ScallopStats(mean, pumpAmplitude, crossings, widths.count { it <= 1 })
    }

    private fun crossSectionWidths(bitmap: Bitmap, stroke: InkStroke): IntArray {
        val points = stroke.points
        if (points.size < 2) return IntArray(0)
        val arc = FloatArray(points.size)
        for (index in 1 until points.size) {
            arc[index] = arc[index - 1] + hypot(
                ((points[index].x - points[index - 1].x) * viewportWidth).toDouble(),
                ((points[index].y - points[index - 1].y) * viewportHeight).toDouble(),
            ).toFloat()
        }
        val total = arc.last()
        if (total <= .0001f) return IntArray(0)
        val maxRadius = (stroke.width * .6f).toInt().coerceAtLeast(6)
        val samples = (total / 2f).toInt().coerceAtLeast(2)
        val result = IntArray(samples)
        var arcIndex = 0
        for (sample in 0 until samples) {
            val target = total * sample / (samples - 1)
            while (arcIndex < points.lastIndex - 1 && arc[arcIndex + 1] < target) arcIndex++
            val point = points[arcIndex]
            val prev = points[(arcIndex - 1).coerceAtLeast(0)]
            val next = points[(arcIndex + 1).coerceAtMost(points.lastIndex)]
            var tx = (next.x - prev.x) * viewportWidth
            var ty = (next.y - prev.y) * viewportHeight
            val length = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(.001f)
            tx /= length
            ty /= length
            val cx = point.x * viewportWidth
            val cy = point.y * viewportHeight
            var width = 0
            for (offset in -maxRadius..maxRadius) {
                val px = (cx - ty * offset).toInt()
                val py = (cy + tx * offset).toInt()
                if (px in 0 until bitmap.width && py in 0 until bitmap.height && Color.alpha(bitmap.getPixel(px, py)) > 40) width++
            }
            result[sample] = width
        }
        return result
    }
}
