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
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateTiltRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun gradationAndBleedTriggerOnlyAfterTiltThresholdAndMatchExport() {
        val tip = File(context.cacheDir, "procreate-tilt-${System.nanoTime()}.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(16f, 16f, 15f, android.graphics.Paint().apply { color = Color.BLACK })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            fun stroke(tilt: Float) = InkStroke(
                points = listOf(
                    StrokePoint(.2f, .5f, tilt = tilt, timeMs = 1),
                    StrokePoint(.8f, .5f, tilt = tilt, timeMs = 2),
                ),
                width = 120f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                spacing = .15f,
                tiltGradation = .8f,
                tiltGradationStart = .5f,
                tiltBleed = .8f,
                tiltBleedStart = .5f,
                sizePressureEnabled = false,
                renderSeed = 713,
            )
            val below = renderExport(stroke((Math.PI * .2).toFloat()))
            val flatStroke = stroke((Math.PI / 2).toFloat())
            val tilted = renderExport(flatStroke)
            val live = renderLive(flatStroke, tilted.height)
            try {
                assertNotEquals("Tilt effects did not change rendered pixels", signature(below), signature(tilted))
                val maximumDifference = maximumRedDifference(tilted, live)
                assertTrue("Live/export tilt rendering diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                below.recycle(); tilted.recycle(); live.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun tiltSizeCompressionKeepsGrainWavelengthIndependentFromTiltSizeAndMatchesExport() {
        val tip = File(context.cacheDir, "procreate-compression-tip-${System.nanoTime()}.png")
        val grain = File(context.cacheDir, "procreate-compression-grain-${System.nanoTime()}.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(16f, 16f, 15f, android.graphics.Paint().apply { color = Color.BLACK })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            val pixels = IntArray(32 * 32) { index ->
                val x = index % 32
                val y = index / 32
                if ((x / 4 + y / 4) % 2 == 0) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(pixels, 0, 32, 0, 0, 32, 32)
            grain.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(
                    StrokePoint(.2f, .5f, tilt = (Math.PI / 2).toFloat(), timeMs = 1),
                    StrokePoint(.8f, .5f, tilt = (Math.PI / 2).toFloat(), timeMs = 2),
                ),
                width = 120f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainScale = 100f,
                grainMovement = 0f,
                grainZoom = 0f,
                spacing = .15f,
                sizePressureEnabled = false,
                renderSeed = 719,
            )
            val followsSize = renderExport(base)
            val compressedStroke = base.copy(tiltSizeCompression = true)
            val compressed = renderExport(compressedStroke)
            val live = renderLive(compressedStroke, compressed.height)
            try {
                assertNotEquals("Size compression did not change the grain wavelength", signature(followsSize), signature(compressed))
                val maximumDifference = maximumRedDifference(compressed, live)
                assertTrue("Live/export compressed grain diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                followsSize.recycle(); compressed.recycle(); live.recycle()
            }
        } finally {
            tip.delete(); grain.delete()
        }
    }

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var result: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { pagePaperColor = Color.WHITE; setDocumentStrokes(listOf(stroke), "tilt-render"); layout(0, 0, 400, height) }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.contentHashCode()

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        return maximum
    }
}
