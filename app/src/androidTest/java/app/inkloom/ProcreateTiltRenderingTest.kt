package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
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

    @Test fun pressureAndTiltRoundnessSquashTipWithLiveExportParity() {
        val tip = File(context.cacheDir, "procreate-roundness-${System.nanoTime()}.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, android.graphics.Paint().apply { color = Color.BLACK; isAntiAlias = true })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            fun stroke(pressure: Float, tilt: Float) = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = pressure, tilt = tilt, timeMs = 1)),
                width = 180f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                thickness = 1f,
                thicknessPressureEnabled = true,
                thicknessPressureMinimum = .3f,
                thicknessPressureCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)),
                thicknessSecondaryDynamics = BrushSecondaryDynamics(
                    tiltEnabled = true,
                    tiltMinimum = .5f,
                    tiltCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)),
                ),
                sizePressureEnabled = false,
                renderSeed = 733,
            )
            val round = renderExport(stroke(0f, 0f))
            val squashedStroke = stroke(1f, (Math.PI / 2).toFloat())
            val squashed = renderExport(squashedStroke)
            val live = renderLive(squashedStroke, squashed.height)
            try {
                assertTrue("Pressure/Tilt Roundness did not squash the tip", darkWidth(squashed) < darkWidth(round) / 2)
                val maximumDifference = maximumRedDifference(squashed, live)
                assertTrue("Live/export roundness diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                round.recycle(); squashed.recycle(); live.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun horizontalAndVerticalRoundnessJitterVaryPerStampAndMatchExport() {
        val tip = File(context.cacheDir, "procreate-roundness-jitter-${System.nanoTime()}.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, android.graphics.Paint().apply { color = Color.BLACK; isAntiAlias = true })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(
                    StrokePoint(.18f, .5f, timeMs = 1),
                    StrokePoint(.82f, .5f, timeMs = 2),
                ),
                width = 120f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                spacing = .3f,
                sizePressureEnabled = false,
                renderSeed = 739,
            )
            val unchanged = renderExport(base)
            val jitteredStroke = base.copy(
                shapeRoundnessHorizontalJitter = .8f,
                shapeRoundnessVerticalJitter = .6f,
            )
            val jittered = renderExport(jitteredStroke)
            val live = renderLive(jitteredStroke, jittered.height)
            try {
                assertNotEquals("Roundness jitter did not change rendered pixels", signature(unchanged), signature(jittered))
                val maximumDifference = maximumRedDifference(jittered, live)
                assertTrue("Live/export roundness jitter diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                unchanged.recycle(); jittered.recycle(); live.recycle()
            }
        } finally {
            tip.delete()
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

    private fun darkWidth(bitmap: Bitmap): Int {
        var minimum = bitmap.width
        var maximum = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (Color.red(bitmap.getPixel(x, y)) < 128) {
            minimum = minOf(minimum, x)
            maximum = maxOf(maximum, x)
        }
        return if (maximum < minimum) 0 else maximum - minimum + 1
    }
}
