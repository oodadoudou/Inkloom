package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.ProcreateColorDynamics
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorJitterRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun deterministicPerTipColorJitterMatchesLiveCanvasAndExport() {
        val tip = File(context.cacheDir, "color-jitter-tip.png")
        writePng(tip, IntArray(64) { Color.WHITE }.also { pixels -> for (y in 2..5) for (x in 2..5) pixels[y * 8 + x] = Color.BLACK })
        try {
            val base = InkStroke(
                renderSeed = 9127,
                points = listOf(StrokePoint(.25f, .5f, timeMs = 10), StrokePoint(.5f, .5f, timeMs = 20), StrokePoint(.75f, .5f, timeMs = 30)),
                colorArgb = 0xFF336699,
                width = 140f,
                spacing = .3f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
            )
            val jittered = base.copy(
                colorJitterHueDegrees = 80f,
                colorJitterSaturation = 35f,
                colorJitterValue = 20f,
                colorJitterHueDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
                colorJitterSaturationDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
                colorJitterValueDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
            )
            val exported = renderExport(jittered)
            val live = renderLive(jittered, exported.height)
            val plain = renderExport(base)
            try {
                assertEquals(pixelSignature(exported), pixelSignature(live))
                assertNotEquals(pixelSignature(plain), pixelSignature(exported))
            } finally { exported.recycle(); live.recycle(); plain.recycle() }
        } finally { tip.delete() }
    }

    @Test fun ProcreateStampStrokePressureTiltAndSecondaryColorMatchLiveCanvasAndExport() {
        val tip = File(context.cacheDir, "procreate-color-dynamics-tip.png")
        writePng(tip, IntArray(64) { Color.WHITE }.also { pixels -> for (y in 1..6) for (x in 1..6) pixels[y * 8 + x] = Color.BLACK })
        try {
            val stroke = InkStroke(
                renderSeed = 9231,
                points = listOf(
                    StrokePoint(.2f, .5f, pressure = .15f, tilt = .1f, timeMs = 10),
                    StrokePoint(.5f, .5f, pressure = .55f, tilt = .6f, timeMs = 20),
                    StrokePoint(.8f, .5f, pressure = 1f, tilt = 1.2f, timeMs = 30),
                ),
                colorArgb = 0xFFCC3322,
                secondaryColorArgb = 0xFF2255DD,
                width = 120f,
                spacing = .22f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                procreateColorDynamics = ProcreateColorDynamics(
                    stampHue = .45f, stampSaturation = .3f, stampLightness = .2f, stampDarkness = .15f, stampSecondary = .25f,
                    strokeHue = .2f, strokeSaturation = .15f, strokeSecondary = .2f,
                    pressureHue = .15f, pressureSaturation = .4f, pressureBrightness = .2f, pressureSecondary = .5f,
                    tiltHue = -.1f, tiltSaturation = .2f, tiltBrightness = -.15f, tiltSecondary = .35f, tiltStart = .2f,
                ),
            )
            val exported = renderExport(stroke)
            val live = renderLive(stroke, exported.height)
            val plain = renderExport(stroke.copy(procreateColorDynamics = ProcreateColorDynamics()))
            try {
                assertEquals(pixelSignature(exported), pixelSignature(live))
                assertNotEquals(pixelSignature(plain), pixelSignature(exported))
            } finally { exported.recycle(); live.recycle(); plain.recycle() }
        } finally { tip.delete() }
    }

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { pagePaperColor = Color.WHITE; setDocumentStrokes(listOf(stroke), "color-jitter-live"); layout(0, 0, 400, height) }
            rendered = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }

    private fun pixelSignature(bitmap: Bitmap): Int { val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); return pixels.contentHashCode() }

    private fun writePng(file: File, pixels: IntArray) { Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle() } }
}
