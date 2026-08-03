package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WetMixDynamicsRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun pressureDrivenWetMixMatchesBetweenCanvasAndExport() {
        val dynamic = InkStroke(
            points = listOf(
                StrokePoint(.2f, .48f, pressure = .05f, timeMs = 10),
                StrokePoint(.5f, .52f, pressure = .5f, timeMs = 20),
                StrokePoint(.8f, .48f, pressure = 1f, timeMs = 30),
            ),
            colorArgb = 0xFF111111,
            width = 110f,
            sizePressureEnabled = false,
            watercolor = true,
            colorMix = .85f,
            colorMixPressureEnabled = true,
            colorMixPressureMinimum = 0f,
            paintMix = .75f,
            paintMixPressureEnabled = true,
            paintMixPressureMinimum = 0f,
            renderSeed = 411,
        )
        val exported = renderExport(dynamic)
        val live = renderLive(dynamic, exported.height)
        val static = renderExport(dynamic.copy(colorMixPressureEnabled = false, paintMixPressureEnabled = false))
        try {
            assertTrue("Live/export wet-mix dynamics diverged", maximumRedDifference(exported, live) <= 3)
            assertNotEquals("Wet-mix pressure dynamics did not affect pigment deposition", pixelSignature(static), pixelSignature(exported))
        } finally {
            exported.recycle()
            live.recycle()
            static.recycle()
        }
    }

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "wet-mix-dynamics-live")
                layout(0, 0, 400, height)
            }
            rendered = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        }
        return maximum
    }

    private fun pixelSignature(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.contentHashCode()
    }
}
