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
import app.inkloom.engine.v3.diagnostics.ContinuityLab
import app.inkloom.ui.InkCanvasView
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Autocorrelation analysis for the HB Pencil: distinguishes the authored graphite texture (fine,
 * aperiodic noise inside each stamp) from visible fixed-spacing dab repetition (a dominant
 * autocorrelation peak at the dab-to-dab spacing). A textured pencil is NOT required to have
 * uniform coverage; the defect being detected is repetition AT THE SPACING FREQUENCY, which reads
 * as a periodic beaded line.
 */
@RunWith(AndroidJUnit4::class)
class PencilPeriodicityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val width = 800
    private val height = 300

    private fun hbPencil() = InkStroke(
        id = "system-hb-pencil",
        brushId = "system-hb-pencil",
        points = (0..160).map { index -> StrokePoint(.1f + index * .005f, .5f, pressure = .5f, timeMs = (index * 2L).toLong()) },
        width = 24f,
        spacing = .26f,
        flow = .68f,
        sizePressureEnabled = true,
        sizePressureMinimum = .3f,
        opacityPressureEnabled = true,
        opacityPressureMinimum = .2f,
        colorArgb = 0xFF000000,
    )

    @Test
    fun pencilTextureMasksFixedSpacingRepetitionComparedToPlainTipControl() {
        withCircularTip { tip ->
            val pencil = hbPencil()
            val plain = hbPencil().copy(
                brushId = "system-pencil-plain-control",
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                hardness = 1f,
            )
            // A plain hard-round stamped control at the same spacing shows the raw fixed-spacing
            // artifact; the graphite-textured pencil must not let that spacing frequency dominate.
            val pencilSpacingCorr = spacingFrequencyCorrelation(pencil)
            val plainSpacingCorr = spacingFrequencyCorrelation(plain)
            println("PENCIL pencilSpacingCorr=${"%.3f".format(pencilSpacingCorr)} plainSpacingCorr=${"%.3f".format(plainSpacingCorr)}")
            assertTrue(
                "The plain-tip control must show strong fixed-spacing repetition (corr=${"%.3f".format(plainSpacingCorr)})",
                plainSpacingCorr > .25f,
            )
            assertTrue(
                "The graphite texture must weaken the fixed-spacing peak relative to a plain tip (pencil=${"%.3f".format(pencilSpacingCorr)}, plain=${"%.3f".format(plainSpacingCorr)})",
                pencilSpacingCorr < plainSpacingCorr,
            )
        }
    }

    /** Autocorrelation coefficient at the authored dab-spacing lag (in profile columns = 1 px). */
    private fun spacingFrequencyCorrelation(stroke: InkStroke): Double {
        val profile = profileFor(stroke)
        val mean = profile.average()
        val series = profile.map { it - mean }
        val energy = series.sumOf { it.toDouble() * it }
        if (energy <= 0.0) return 0.0
        val deposits = ContinuityLab.deposits(stroke, 1600f, 1200f)
        val gaps = deposits.filter { it.gapToNextPx >= 0f }.map { it.gapToNextPx }
        val spacingLag = gaps.average().toInt().coerceAtLeast(1)
        var correlation = 0.0
        for (index in 0 until profile.size - spacingLag) {
            correlation += series[index].toDouble() * series[index + spacingLag]
        }
        return correlation / energy
    }

    private fun profileFor(stroke: InkStroke): IntArray {
        val w = width
        val h = height
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "pencil-periodicity")
            view.draw(Canvas(bitmap))
        }
        val startX = (stroke.points.first().x * w).toInt().coerceIn(0, w - 1)
        val endX = (stroke.points.last().x * w).toInt().coerceIn(0, w - 1)
        val centerY = (stroke.points.first().y * h).toInt().coerceIn(0, h - 1)
        val radius = (stroke.width).toInt().coerceAtLeast(4)
        val profile = IntArray((endX - startX).coerceAtLeast(1))
        for (x in startX until endX) {
            var coverage = 0
            for (dy in -radius..radius) {
                val y = centerY + dy
                if (y in 0 until h) coverage += Color.alpha(bitmap.getPixel(x, y))
            }
            profile[x - startX] = coverage
        }
        bitmap.recycle()
        return profile
    }

    private fun withCircularTip(block: (java.io.File) -> Unit) {
        val tip = java.io.File(context.cacheDir, "pencil-control-${System.nanoTime()}.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, android.graphics.Paint().apply { color = Color.BLACK; isAntiAlias = true })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            block(tip)
        } finally {
            tip.delete()
        }
    }
}
