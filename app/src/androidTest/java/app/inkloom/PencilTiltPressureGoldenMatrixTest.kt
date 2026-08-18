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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PENCIL_RENDERER_V4 deterministic pressure x tilt golden matrix.
 *
 * Renders the HB Pencil across a grid of (pressure, tilt) pairs and checks that each cell is
 * deterministic (same StrokeInput -> same raster), that pressure primarily drives deposition
 * (mean coverage rises with pressure), and that tilt widens the footprint (width grows with tilt).
 * Unknown-tilt neutral semantics are also pinned: a first sample with no established tilt sensor
 * must render identically to an established zero-tilt sample, not as a numeric tilt extreme.
 */
@RunWith(AndroidJUnit4::class)
class PencilTiltPressureGoldenMatrixTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewportWidth = 700
    private val viewportHeight = 700

    private fun hbPencil(pressure: Float, tilt: Float, sensorFlags: Int = Int.MAX_VALUE) = InkStroke(
        id = "system-hb-pencil",
        brushId = "system-hb-pencil",
        points = (0..220).map { index ->
            StrokePoint(
                .15f + index * .0032f,
                .5f,
                pressure = pressure,
                tilt = tilt,
                sensorFlags = sensorFlags,
                timeMs = (index * 2L).toLong(),
            )
        },
        width = 22f,
        spacing = .26f,
        flow = .68f,
        sizePressureEnabled = false,
        opacityPressureEnabled = true,
        opacityPressureMinimum = .15f,
        graphiteTiltWidth = 1.2f,
        graphiteTiltWidthStart = .2f,
        graphiteTiltGrain = .9f,
        graphiteTiltGrainStart = .2f,
        proceduralPaperGrainEnabled = true,
        proceduralPaperGrainScale = 40f,
        proceduralPaperGrainDensity = .3f,
        colorArgb = 0xFF000000,
    )

    @Test
    fun pressureXiltMatrixIsDeterministic() {
        listOf(.2f to .1f, .5f to .8f, .8f to 1.3f).forEach { (pressure, tilt) ->
            val a = raster(hbPencil(pressure, tilt))
            val b = raster(hbPencil(pressure, tilt))
            try {
                assertEquals("Pressure=$pressure tilt=$tilt must render deterministically", signature(a), signature(b))
            } finally {
                a.recycle()
                b.recycle()
            }
        }
    }

    @Test
    fun pressurePrimarilyDrivesGraphiteDeposition() {
        val light = raster(hbPencil(pressure = .25f, tilt = .8f))
        val heavy = raster(hbPencil(pressure = .9f, tilt = .8f))
        try {
            val lightMean = meanCoverage(light)
            val heavyMean = meanCoverage(heavy)
            assertTrue(
                "Higher pressure must deposit more graphite (light=$lightMean heavy=$heavyMean)",
                heavyMean > lightMean * 1.15f,
            )
        } finally {
            light.recycle()
            heavy.recycle()
        }
    }

    @Test
    fun tiltWidensTheStampedFootprint() {
        val upright = raster(hbPencil(pressure = .6f, tilt = .1f))
        val tilted = raster(hbPencil(pressure = .6f, tilt = 1.3f))
        try {
            // The stroke is horizontal, so tilt widens the footprint along the vertical
            // (perpendicular) axis.
            val uprightThickness = darkThickness(upright)
            val tiltedThickness = darkThickness(tilted)
            assertTrue(
                "Tilt must widen the footprint perpendicular to the stroke (upright=$uprightThickness tilted=$tiltedThickness)",
                tiltedThickness > uprightThickness + 1,
            )
        } finally {
            upright.recycle()
            tilted.recycle()
        }
    }

    @Test
    fun unknownTiltStaysNeutralOnFirstSample() {
        // A stroke whose first sample has no established tilt must not treat tilt as a numeric
        // extreme: unknown-tilt and established-zero-tilt footprints should match.
        val neutral = raster(hbPencil(pressure = .6f, tilt = 0f, sensorFlags = 0))
        val establishedZero = raster(hbPencil(pressure = .6f, tilt = 0f))
        try {
            val a = signature(neutral)
            val b = signature(establishedZero)
            assertEquals("Unknown tilt must stay neutral, not act as an extreme", a, b)
        } finally {
            neutral.recycle()
            establishedZero.recycle()
        }
    }

    private fun raster(stroke: InkStroke): Bitmap {
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, viewportWidth, viewportHeight) }
            bitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "pencil-matrix")
            view.draw(Canvas(bitmap))
        }
        return bitmap!!
    }

    private fun meanCoverage(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.sumOf { Color.alpha(it) }.toDouble() / pixels.size
    }

    private fun darkThickness(bitmap: Bitmap): Int {
        var minimum = bitmap.height
        var maximum = -1
        val centerX = bitmap.width / 2
        for (y in 0 until bitmap.height) {
            var dark = false
            for (x in (centerX - 10)..(centerX + 10)) {
                if (x in 0 until bitmap.width && Color.alpha(bitmap.getPixel(x, y)) > 24) {
                    dark = true
                    break
                }
            }
            if (dark) {
                minimum = minOf(minimum, y)
                maximum = maxOf(maximum, y)
            }
        }
        return if (maximum < minimum) 0 else maximum - minimum + 1
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()
}
