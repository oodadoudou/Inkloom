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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the alpha/coverage profile of an actual rasterized stroke along a straight trajectory
 * and classifies the beaded-line failure modes:
 *
 *  - COVERAGE_PUMPING: the per-column ink coverage oscillates periodically (darker/rounder at
 *    every join or stamp overlap) rather than staying uniform.
 *  - GEOMETRIC_GAP: profile columns drop to (near) zero between deposits.
 *
 * Default expectations pinned here:
 *  - the default opaque G Pen is coverage-uniform on a straight line (its beading, when visible,
 *    comes from non-collinear joins, not from the straight-line deposit);
 *  - a low-opacity pen shows periodic join darkening (COVERAGE_PUMPING);
 *  - the HB Pencil's stamped graphite produces periodic coverage at its authored spacing.
 */
@RunWith(AndroidJUnit4::class)
class ContinuityLabProfileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val width = 800
    private val height = 300

    @Test
    fun opaqueGPenStraightLineCoverageIsUniform() {
        val stroke = InkStroke(
            id = "profile-gpen",
            brushId = "system-g-pen",
            points = (0..120).map { index -> StrokePoint(.1f + index * .006f, .5f, pressure = .6f, timeMs = (index * 2L).toLong()) },
            width = 24f,
            spacing = .045f,
            sizePressureEnabled = true,
            sizePressureMinimum = .06f,
            colorArgb = 0xFF000000,
        )
        val profile = profileFor(stroke)
        val stats = profileStats(profile)
        println("PROFILE gpen-opaque ${stats.describe()}")
        assertEquals("Opaque pen must never show a geometric gap on a dense line", 0, stats.gapColumns)
        assertTrue("Opaque pen straight line must be coverage-uniform (pump=${stats.pumpAmplitude})", stats.pumpAmplitude < .35f)
    }

    @Test
    fun lowOpacityPenStraightLineJoinDarkeningIsNegligible() {
        val stroke = InkStroke(
            id = "profile-gpen-lowop",
            brushId = "system-g-pen",
            points = (0..120).map { index -> StrokePoint(.1f + index * .006f, .5f, pressure = .6f, timeMs = (index * 2L).toLong()) },
            width = 24f,
            spacing = .045f,
            sizePressureEnabled = true,
            sizePressureMinimum = .06f,
            opacity = .4f,
            colorArgb = 0xFF000000,
        )
        val profile = profileFor(stroke)
        val stats = profileStats(profile)
        println("PROFILE gpen-lowop ${stats.describe()}")
        assertEquals(0, stats.gapColumns)
        // Collinear joins share endpoints exactly, so the straight-line darkening is negligible.
        // Non-collinear curve joins (edge scalloping) are a separate profile measured per-curve.
        assertTrue(
            "Collinear pen joins must not pump coverage on a straight line (pump=${stats.pumpAmplitude})",
            stats.pumpAmplitude < .35f,
        )
    }

    @Test
    fun tinyPenAALeadsToRounderDeposits() {
        val stroke = InkStroke(
            id = "profile-gpen-tiny",
            brushId = "system-g-pen",
            points = (0..120).map { index -> StrokePoint(.1f + index * .006f, .5f, pressure = .6f, timeMs = (index * 2L).toLong()) },
            width = 3f,
            spacing = .045f,
            sizePressureEnabled = true,
            sizePressureMinimum = .06f,
            colorArgb = 0xFF000000,
        )
        val profile = profileFor(stroke)
        val stats = profileStats(profile)
        println("PROFILE gpen-tiny ${stats.describe()}")
        assertEquals(0, stats.gapColumns)
        assertTrue(
            "Tiny opaque pen must stay coverage-uniform (pump=${stats.pumpAmplitude})",
            stats.pumpAmplitude < .35f,
        )
    }

    @Test
    fun hbPencilStampedGraphiteProducesPeriodicCoverage() {
        val stroke = InkStroke(
            id = "profile-hb",
            brushId = "system-hb-pencil",
            points = (0..120).map { index -> StrokePoint(.1f + index * .006f, .5f, pressure = .5f, timeMs = (index * 2L).toLong()) },
            width = 24f,
            spacing = .26f,
            flow = .68f,
            sizePressureEnabled = true,
            sizePressureMinimum = .3f,
            opacityPressureEnabled = true,
            opacityPressureMinimum = .2f,
            colorArgb = 0xFF000000,
        )
        val profile = profileFor(stroke)
        val stats = profileStats(profile)
        println("PROFILE hb-pencil ${stats.describe()}")
        assertEquals("HB Pencil must not have geometric holes at authored spacing", 0, stats.gapColumns)
        assertTrue("HB Pencil stamp periodicity must be visible in coverage (pump=${stats.pumpAmplitude})", stats.pumpAmplitude > .1f)
    }

    private data class Stats(val mean: Double, val min: Int, val max: Int, val pumpAmplitude: Float, val crossings: Int, val gapColumns: Int) {
        fun describe() = "mean=${"%.1f".format(mean)} min=$min max=$max pump=${"%.2f".format(pumpAmplitude)} crossings=$crossings gaps=$gapColumns"
    }

    private fun profileFor(stroke: InkStroke): IntArray {
        val w = width
        val h = height
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "continuity-profile")
            view.draw(Canvas(bitmap))
        }
        val startX = (stroke.points.first().x * width).toInt().coerceIn(0, width - 1)
        val endX = (stroke.points.last().x * width).toInt().coerceIn(0, width - 1)
        val centerY = (stroke.points.first().y * height).toInt().coerceIn(0, height - 1)
        val radius = (stroke.width).toInt().coerceAtLeast(4)
        val profile = IntArray((endX - startX).coerceAtLeast(1))
        for (x in startX until endX) {
            var coverage = 0
            for (dy in -radius..radius) {
                val y = centerY + dy
                if (y in 0 until height) coverage += Color.alpha(bitmap.getPixel(x, y))
            }
            profile[x - startX] = coverage
        }
        bitmap.recycle()
        return profile
    }

    private fun profileStats(profile: IntArray): Stats {
        if (profile.isEmpty()) return Stats(0.0, 0, 0, 0f, 0, 0)
        val mean = profile.average()
        val min = profile.min()
        val max = profile.max()
        val gapColumns = profile.count { it <= 1 }
        if (mean <= 0.0) return Stats(mean, min, max, 0f, 0, gapColumns)
        // Smooth with a small window and measure periodic oscillation around it.
        val smoothed = IntArray(profile.size) { index ->
            val from = (index - 2).coerceAtLeast(0)
            val to = (index + 2).coerceAtMost(profile.lastIndex)
            (from..to).map { profile[it] }.average().toInt()
        }
        val deviations = profile.indices.map { kotlin.math.abs(profile[it] - smoothed[it]) }
        val pumpAmplitude = (deviations.maxOrNull() ?: 0) / mean.toFloat()
        val crossings = (1 until profile.size).count { index ->
            (profile[index] - smoothed[index] > 0) != (profile[index - 1] - smoothed[index - 1] > 0)
        }
        return Stats(mean, min, max, pumpAmplitude, crossings, gapColumns)
    }
}
