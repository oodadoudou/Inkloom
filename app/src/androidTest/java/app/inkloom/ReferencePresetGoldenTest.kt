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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic golden corpus for the two reference presets.
 *
 * INK (system-pen, continuous ribbon): must stay geometrically continuous (no holes) and low
 * scalloping across straight/circle/S-curve/tight-turn/pressure-ramp inputs at small and medium
 * widths, and render deterministically (same StrokeInput -> same raster).
 *
 * PENCIL (system-hb-pencil, stamped graphite): must have texture (coverage is NOT uniform -- it is
 * a textured pencil) while staying hole-free on a straight line, and respond to tilt.
 */
@RunWith(AndroidJUnit4::class)
class ReferencePresetGoldenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewportWidth = 900
    private val viewportHeight = 900

    private fun ink(width: Float, points: List<StrokePoint>) = InkStroke(
        id = "system-pen", brushId = "system-pen", points = points,
        width = width, spacing = .12f, sizePressureEnabled = true, sizePressureMinimum = .35f, colorArgb = 0xFF000000,
    )

    private fun pencil(points: List<StrokePoint>) = InkStroke(
        id = "system-hb-pencil", brushId = "system-hb-pencil", points = points,
        width = 24f, spacing = .26f, flow = .68f, sizePressureEnabled = true, sizePressureMinimum = .3f,
        opacityPressureEnabled = true, opacityPressureMinimum = .2f, colorArgb = 0xFF000000,
    )

    private fun straight() = (0..300).map { index -> StrokePoint(.1f + index * .0025f, .5f, pressure = .6f, timeMs = (index * 2L).toLong()) }
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
    private fun pressureRamp() = (0..300).map { index -> StrokePoint(.1f + index * .0025f, .5f, pressure = index / 300f, timeMs = (index * 2L).toLong()) }

    @Test
    fun inkGoldenCorpusStaysContinuousAndLowScalloping() {
        val cases = mapOf(
            "straight-5" to ink(5f, straight()),
            "circle-5" to ink(5f, circle()),
            "scurve-5" to ink(5f, sCurve()),
            "tightTurn-5" to ink(5f, tightTurn()),
            "pressureRamp-5" to ink(5f, pressureRamp()),
            "straight-24" to ink(24f, straight()),
            "circle-24" to ink(24f, circle()),
        )
        cases.forEach { (name, stroke) ->
            val stats = scalloping(stroke)
            println("GOLDEN ink-$name $stats")
            assertTrue("$name must have no geometric holes (gaps=${stats.gaps})", stats.gaps <= 2)
            assertTrue("$name must stay low scalloping (pump=${stats.pump})", stats.pump < .6f)
        }
    }

    @Test
    fun inkRendersDeterministically() {
        val a = raster(ink(5f, sCurve()))
        val b = raster(ink(5f, sCurve()))
        assertEquals("Same Ink StrokeInput must render identically", signature(a), signature(b))
        a.recycle()
        b.recycle()
    }

    @Test
    fun pencilHasTextureAndNoHolesAndRendersDeterministically() {
        val stats = scalloping(pencil(straight()))
        println("GOLDEN pencil $stats")
        assertTrue(
            "A textured pencil must show coverage variation (pump=${stats.pump})",
            stats.pump > .05f,
        )
        assertTrue("Pencil must have no geometric holes (gaps=${stats.gaps})", stats.gaps <= 2)
        val a = raster(pencil(straight()))
        val b = raster(pencil(straight()))
        assertEquals("Same Pencil StrokeInput must render identically", signature(a), signature(b))
        a.recycle()
        b.recycle()
    }

    private data class Stats(val coverageMean: Double, val pump: Float, val crossings: Int, val gaps: Int) {
        override fun toString() = "coverageMean=${"%.1f".format(coverageMean)} pump=${"%.2f".format(pump)} crossings=$crossings gaps=$gaps"
    }

    private fun raster(stroke: InkStroke): Bitmap {
        val w = viewportWidth
        val h = viewportHeight
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "golden")
            view.draw(Canvas(bitmap))
        }
        return bitmap!!
    }

    private fun scalloping(stroke: InkStroke): Stats {
        val bitmap = raster(stroke)
        val profile = crossSectionAlpha(bitmap, stroke)
        bitmap.recycle()
        if (profile.isEmpty()) return Stats(0.0, 0f, 0, profile.size)
        val mean = profile.average()
        if (mean <= .0001) return Stats(mean, 0f, 0, profile.count { it <= 4 })
        val smoothed = IntArray(profile.size) { index ->
            val from = (index - 4).coerceAtLeast(0)
            val to = (index + 4).coerceAtMost(profile.lastIndex)
            (from..to).map { profile[it] }.average().toInt()
        }
        val deviations = profile.indices.map { abs(profile[it] - smoothed[it]) }
        val pump = (deviations.maxOrNull() ?: 0) / mean.toFloat()
        val crossings = (1 until profile.size).count { index ->
            (profile[index] - smoothed[index] > 0) != (profile[index - 1] - smoothed[index - 1] > 0)
        }
        return Stats(mean, pump, crossings, profile.count { it <= 4 })
    }

    /** Summed alpha along the path-perpendicular strip at every arc-length sample. */
    private fun crossSectionAlpha(bitmap: Bitmap, stroke: InkStroke): IntArray {
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
        val maxRadius = (stroke.width * .8f).toInt().coerceAtLeast(8)
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
            var coverage = 0
            for (offset in -maxRadius..maxRadius) {
                val px = (cx - ty * offset).toInt()
                val py = (cy + tx * offset).toInt()
                if (px in 0 until bitmap.width && py in 0 until bitmap.height) coverage += Color.alpha(bitmap.getPixel(px, py))
            }
            result[sample] = coverage
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()
}
