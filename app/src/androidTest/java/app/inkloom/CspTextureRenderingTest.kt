package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CspTextureRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun heightTexturePreservesOpaqueCenterInExportAndLiveCanvas() {
        val tip = File(context.cacheDir, "height-tip.png")
        val grain = File(context.cacheDir, "height-grain.png")
        writePng(tip, IntArray(64) { Color.WHITE }.also {
            for (y in 3..4) for (x in 3..4) it[y * 8 + x] = Color.BLACK
        })
        writePng(grain, IntArray(64) { Color.WHITE })
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF111111,
                width = 240f,
                opacity = 1f,
                flow = 1f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
            )
            val service = DocumentAssetService(context)
            val legacy = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val stroke = base.copy(grainCompositeMode = CspTextureComposite.HEIGHT)
            val exported = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)
            val live = renderLive(stroke)
            try {
                val x = 200
                val exportY = exported.height / 2
                val liveY = live.height / 2
                assertTrue("Legacy whole-tip multiplication must remove the white-grain center", Color.red(legacy.getPixel(x, exportY)) > 245)
                assertTrue("CSP Height must preserve the authored opaque center", Color.red(exported.getPixel(x, exportY)) < 40)
                assertEquals(exported.getPixel(x, exportY), live.getPixel(x, liveY))
            } finally {
                legacy.recycle()
                exported.recycle()
                live.recycle()
            }
        } finally {
            tip.delete()
            grain.delete()
        }
    }

    @Test fun textureDensityPressureIsAppliedPerStampInExportAndLiveCanvas() {
        val tip = File(context.cacheDir, "dynamic-grain-tip.png")
        val grain = File(context.cacheDir, "dynamic-grain.png")
        writePng(tip, IntArray(64) { Color.BLACK })
        writePng(grain, IntArray(64) { Color.WHITE })
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = .1f)),
                colorArgb = 0xFF111111,
                width = 240f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainCompositeMode = CspTextureComposite.MULTIPLY,
            )
            val dynamic = base.copy(
                grainDensityDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .1f),
            )
            val service = DocumentAssetService(context)
            val fixed = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val exported = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(dynamic)), 400)
            val live = renderLive(dynamic)
            try {
                val x = 200
                val exportY = exported.height / 2
                val liveY = live.height / 2
                assertTrue("Full white texture density must remove the opaque tip", Color.red(fixed.getPixel(x, exportY)) > 245)
                assertTrue("Low pressure must reduce texture strength and preserve most ink", Color.red(exported.getPixel(x, exportY)) < 80)
                assertTrue(
                    "Live and export density quantization must remain visually equivalent",
                    kotlin.math.abs(Color.red(exported.getPixel(x, exportY)) - Color.red(live.getPixel(x, liveY))) <= 12,
                )
            } finally {
                fixed.recycle()
                exported.recycle()
                live.recycle()
            }
        } finally {
            tip.delete()
            grain.delete()
        }
    }

    private fun renderLive(stroke: InkStroke): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "csp-height-live")
                layout(0, 0, 400, 566)
            }
            rendered = Bitmap.createBitmap(400, 566, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }

    private fun writePng(file: File, pixels: IntArray) {
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
