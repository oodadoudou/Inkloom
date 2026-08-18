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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Path-perpendicular edge-scalloping profile. Renders the default Ink pen (line-segment path) and
 * measures, along the stroke's arc length, the effective cross-section width and summed alpha.
 *
 * The known renderer defect being quantified: each polyline segment is alpha-composited
 * independently with BUTT caps, so non-collinear joins create scalloped bulges (and collinear
 * overlaps darken). Straight lines are the clean baseline; curves must show more scalloping.
 */
@RunWith(AndroidJUnit4::class)
class CurveScallopingProfileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewportWidth = 900
    private val viewportHeight = 900

    private fun pen(brushId: String, width: Float, points: List<StrokePoint>) = InkStroke(
        id = brushId,
        brushId = brushId,
        points = points,
        width = width,
        spacing = .12f,
        sizePressureEnabled = true,
        sizePressureMinimum = .35f,
        colorArgb = 0xFF000000,
    )

    private fun straight() = (0..300).map { index ->
        StrokePoint(.1f + index * .0025f, .5f, pressure = .6f, timeMs = (index * 2L).toLong())
    }

    private fun circle() = (0..360).map { index ->
        val angle = index / 360f * 2f * PI.toFloat()
        StrokePoint(.5f + .3f * cos(angle), .5f + .3f * sin(angle), pressure = .6f, timeMs = (index * 2L).toLong())
    }

    private fun sCurve() = (0..500).map { index ->
        val t = index / 500f
        StrokePoint(.1f + t * .8f, .5f + .2f * sin(t * (PI.toFloat() * 3f)), pressure = .6f, timeMs = (index * 2L).toLong())
    }

    private fun tightTurn() = (0..120).map { index ->
        val t = index / 120f
        val angle = t * 1.6f * PI.toFloat()
        StrokePoint(.5f + .22f * cos(angle), .5f + .22f * sin(angle), pressure = .6f, timeMs = (index * 2L).toLong())
    }

    private fun pressureRamp() = (0..300).map { index ->
        StrokePoint(.1f + index * .0025f, .5f, pressure = index / 300f, timeMs = (index * 2L).toLong())
    }

    @Test
    fun straightLineIsTheCleanScallopingBaseline() {
        val small = scalloping(pen("system-g-pen", 5f, straight()))
        val medium = scalloping(pen("system-g-pen", 24f, straight()))
        println("SCALLOP straight small=$small")
        println("SCALLOP straight medium=$medium")
        assertTrue("Straight small pen must be clean (pump=${small.pumpAmplitude})", small.pumpAmplitude < .4f)
        assertTrue("Straight medium pen must be clean (pump=${medium.pumpAmplitude})", medium.pumpAmplitude < .4f)
    }

    @Test
    fun curvesScallopMoreThanStraightLines() {
        val straightStats = scalloping(pen("system-g-pen", 5f, straight()))
        val circleStats = scalloping(pen("system-g-pen", 5f, circle()))
        val sStats = scalloping(pen("system-g-pen", 5f, sCurve()))
        val turnStats = scalloping(pen("system-g-pen", 5f, tightTurn()))
        println("SCALLOP straight=$straightStats")
        println("SCALLOP circle=$circleStats")
        println("SCALLOP scurve=$sStats")
        println("SCALLOP tightTurn=$turnStats")
        assertTrue(
            "Non-collinear curve joins must scallop more than a straight line (straight=${straightStats.pumpAmplitude}, circle=${circleStats.pumpAmplitude})",
            circleStats.pumpAmplitude > straightStats.pumpAmplitude,
        )
        assertTrue(
            "Tight turns must scallop (pump=${turnStats.pumpAmplitude})",
            turnStats.pumpAmplitude > .4f,
        )
    }

    @Test
    fun mediumWidthCurvesScallop() {
        val mediumCircle = scalloping(pen("system-g-pen", 24f, circle()))
        val mediumTurn = scalloping(pen("system-g-pen", 24f, tightTurn()))
        println("SCALLOP medium-circle=$mediumCircle")
        println("SCALLOP medium-turn=$mediumTurn")
        assertTrue("Medium circle must scallop (pump=${mediumCircle.pumpAmplitude})", mediumCircle.pumpAmplitude > .15f)
    }

    @Test
    fun pressureRampKeepsPerpendicularProfileStable() {
        val stats = scalloping(pen("system-g-pen", 5f, pressureRamp()))
        println("SCALLOP pressure-ramp=$stats")
        assertTrue("Pressure ramp must have no geometric gaps", stats.gapSamples == 0)
    }

    @Test
    fun migratedDefaultInkIsCleanerThanSegmentPens() {
        val segment = scalloping(pen("system-g-pen", 5f, tightTurn()))
        val ribbon = scalloping(pen("system-pen", 5f, tightTurn()))
        println("SCALLOP migrated segment=$segment ribbon=$ribbon")
        assertTrue(
            "The migrated default Ink (system-pen) must scallop far less than segment pens (segment=${segment.pumpAmplitude}, ribbon=${ribbon.pumpAmplitude})",
            ribbon.pumpAmplitude < segment.pumpAmplitude * .6f,
        )
    }

    private data class ScallopStats(val widthMean: Double, val widthCv: Float, val pumpAmplitude: Float, val crossings: Int, val gapSamples: Int) {
        override fun toString() = "widthMean=${"%.1f".format(widthMean)} widthCv=${"%.2f".format(widthCv)} pump=${"%.2f".format(pumpAmplitude)} crossings=$crossings gaps=$gapSamples"
    }

    private fun scalloping(stroke: InkStroke): ScallopStats {
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val w = viewportWidth
            val h = viewportHeight
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "scallop")
            view.draw(Canvas(bitmap))
        }
        val widths = crossSectionWidths(bitmap, stroke)
        bitmap.recycle()
        if (widths.isEmpty()) return ScallopStats(0.0, 0f, 0f, 0, 0)
        val mean = widths.average()
        val std = widths.map { (it - mean) * (it - mean) }.average().toFloat().let { kotlin.math.sqrt(it) }
        val cv = if (mean > .0001f) std / mean.toFloat() else 0f
        val gapSamples = widths.count { it <= 1 }
        // Periodic width oscillation around a small-window moving average.
        val smoothed = IntArray(widths.size) { index ->
            val from = (index - 4).coerceAtLeast(0)
            val to = (index + 4).coerceAtMost(widths.lastIndex)
            (from..to).map { widths[it] }.average().toInt()
        }
        val deviations = widths.indices.map { kotlin.math.abs(widths[it] - smoothed[it]) }
        val pumpAmplitude = if (mean > .0001f) (deviations.maxOrNull() ?: 0) / mean.toFloat() else 0f
        val crossings = (1 until widths.size).count { index ->
            (widths[index] - smoothed[index] > 0) != (widths[index - 1] - smoothed[index - 1] > 0)
        }
        return ScallopStats(mean, cv, pumpAmplitude, crossings, gapSamples)
    }

    /** Walks the path by arc length and measures the perpendicular cross-section width (ink-pixel
     * count) at every sample. */
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
        val sampleStep = 2f
        val samples = (total / sampleStep).toInt().coerceAtLeast(2)
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
