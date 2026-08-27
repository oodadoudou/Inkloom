package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.document.ProcreateWetMix
import app.inkloom.document.CspColorMixMode
import app.inkloom.document.WetMixDynamics
import app.inkloom.engine.v5.tools.RasterWetPaintMixer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterWetPaintMixerTest {
    @Test fun procreateDilutionControlsHowMuchExistingPigmentParticipates() {
        fun result(dilution: Float): Int {
            val base = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val patch = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val stroke = InkStroke(
                procreateWetMix = ProcreateWetMix(dilution = dilution, charge = 1f, attack = 1f),
                points = listOf(StrokePoint(.5f, .5f, pressure = 1f)),
            )
            RasterWetPaintMixer.composite(base, patch, 0, 0, listOf(stroke))
            return base.getPixel(6, 6).also { patch.recycle(); base.recycle() }
        }
        val thick = result(0f)
        val watery = result(1f)
        assertTrue(Color.red(thick) > Color.red(watery))
        assertTrue(Color.blue(watery) > Color.blue(thick))
    }

    @Test fun wetPaintMixesFreshColorWithExistingLayerPigment() {
        val base = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val patch = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val wet = InkStroke(
            watercolor = true,
            colorMix = .5f,
            paintMix = .8f,
            points = listOf(StrokePoint(.25f, .25f, pressure = 1f), StrokePoint(.75f, .75f, pressure = 1f)),
        )
        assertTrue(RasterWetPaintMixer.composite(base, patch, 16, 16, listOf(wet)))
        val mixed = base.getPixel(32, 32)
        assertTrue("Mixed pigment retains red from the brush", Color.red(mixed) > 40)
        assertTrue("Mixed pigment retains blue from the layer", Color.blue(mixed) > 40)
        assertTrue("Wet paint must not collapse to normal pure-red source-over", Color.blue(mixed) > Color.green(mixed))
        patch.recycle(); base.recycle()
    }

    @Test fun amountOfPaintControlsFreshPigmentRatioAndDensityControlsAlphaDeposit() {
        fun result(amount: Float, density: Float): Int {
            val base = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(100, 0, 0, 255)) }
            val depositedAlpha = (WetMixDynamics.alphaMultiplier(true, amount, density) * 255f).toInt()
            val patch = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(depositedAlpha, 255, 0, 0)) }
            val stroke = InkStroke(watercolor = true, colorMix = amount, paintMix = density, points = listOf(StrokePoint(.5f, .5f)))
            RasterWetPaintMixer.composite(base, patch, 0, 0, listOf(stroke))
            return base.getPixel(4, 4).also { patch.recycle(); base.recycle() }
        }
        val lowPaint = result(.15f, .8f)
        val highPaint = result(.9f, .8f)
        assertTrue(Color.red(highPaint) > Color.red(lowPaint))
        val lowDensity = result(.8f, .1f)
        val highDensity = result(.8f, 1f)
        assertTrue(Color.alpha(highDensity) > Color.alpha(lowDensity))
    }

    @Test fun smearIgnoresDensityAndDepositsTheRealtimePickupColor() {
        fun result(density: Float): Int {
            val base = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val patch = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            val stroke = InkStroke(
                watercolor = true,
                cspColorMixMode = CspColorMixMode.SMEAR,
                colorMix = .2f,
                paintMix = density,
                points = listOf(StrokePoint(.5f, .5f, pigmentArgb = Color.GREEN.toUInt().toLong())),
            )
            RasterWetPaintMixer.composite(base, patch, 0, 0, listOf(stroke))
            return base.getPixel(4, 4).also { patch.recycle(); base.recycle() }
        }
        assertTrue(result(0f) == result(1f))
        assertTrue(Color.green(result(0f)) > Color.blue(result(0f)))
    }

    @Test fun recursiveRenderingModesIncreaseExistingPigmentInteraction() {
        fun result(mode: ProcreateRenderingMode): Int {
            val base = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val patch = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val stroke = InkStroke(
                watercolor = true,
                colorMix = .55f,
                paintMix = .8f,
                procreateRenderingMode = mode,
                points = listOf(StrokePoint(.5f, .5f)),
            )
            RasterWetPaintMixer.composite(base, patch, 0, 0, listOf(stroke))
            return base.getPixel(4, 4).also { patch.recycle(); base.recycle() }
        }
        val heavy = result(ProcreateRenderingMode.HEAVY_GLAZE)
        val uniform = result(ProcreateRenderingMode.UNIFORM_BLENDING)
        val intense = result(ProcreateRenderingMode.INTENSE_BLENDING)

        assertTrue(Color.blue(heavy) < Color.blue(uniform))
        assertTrue(Color.blue(uniform) < Color.blue(intense))
    }
}
