package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v2.brush.EngineV2BrushEvaluator
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SprayDynamicsRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun pressureAndVelocitySprayDynamicsMatchBetweenCanvasAndExport() {
        val tip = File(context.cacheDir, "spray-dynamics-tip-${System.nanoTime()}.png")
        Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val dynamic = InkStroke(
                points = listOf(
                    StrokePoint(.25f, .48f, pressure = .15f, velocity = .05f, timeMs = 10),
                    StrokePoint(.50f, .52f, pressure = .55f, velocity = .45f, timeMs = 20),
                    StrokePoint(.75f, .48f, pressure = 1f, velocity = 1f, timeMs = 30),
                ),
                width = 180f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                spray = true,
                sprayParticleSize = 70f,
                sprayParticleSizePressureEnabled = true,
                sprayParticleSizePressureMinimum = .2f,
                sprayParticleSizeSecondaryDynamics = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .35f, velocityScale = 5f),
                sprayDensity = 80f,
                sprayDensitySecondaryDynamics = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .25f, velocityScale = 3f),
                spraySpread = 1.4f,
                renderSeed = 931,
            )
            val exported = renderExport(dynamic)
            val live = renderLive(dynamic, exported.height)
            val static = renderExport(
                dynamic.copy(
                    sprayParticleSizePressureEnabled = false,
                    sprayParticleSizeSecondaryDynamics = BrushSecondaryDynamics(),
                    sprayDensitySecondaryDynamics = BrushSecondaryDynamics(),
                ),
            )
            try {
                assertTrue("Live/export spray dynamics diverged", maximumRedDifference(exported, live) <= 3)
                assertNotEquals("Spray dynamics did not change the rendered stroke", pixelSignature(static), pixelSignature(exported))
            } finally {
                exported.recycle()
                live.recycle()
                static.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun procreateSpeedSpacingChangesStampDensityAndMatchesExport() {
        val tip = File(context.cacheDir, "speed-spacing-tip-${System.nanoTime()}.png")
        Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val dynamic = InkStroke(
                points = listOf(
                    StrokePoint(.15f, .5f, velocity = 0f, timeMs = 10),
                    StrokePoint(.35f, .5f, velocity = .25f, timeMs = 20),
                    StrokePoint(.60f, .5f, velocity = .65f, timeMs = 30),
                    StrokePoint(.85f, .5f, velocity = 1f, timeMs = 40),
                ),
                width = 54f,
                engineKind = BrushEngineKind.STAMP_RASTER,
                spacing = 2f,
                spacingSecondaryDynamics = BrushSecondaryDynamics(
                    velocityEnabled = true,
                    velocityMinimum = .2f,
                    velocityCurve = listOf(
                        app.inkloom.document.BrushCurvePoint(0f, 0f),
                        app.inkloom.document.BrushCurvePoint(1f, 1f),
                    ),
                ),
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                renderSeed = 932,
            )
            val exported = renderExport(dynamic)
            val live = renderLive(dynamic, exported.height)
            val constant = renderExport(dynamic.copy(spacingSecondaryDynamics = BrushSecondaryDynamics()))
            val dynamicCount = EngineV2BrushEvaluator.sampleStampPoints(dynamic, 400f, exported.height.toFloat(), dual = false).size
            val constantCount = EngineV2BrushEvaluator.sampleStampPoints(dynamic.copy(spacingSecondaryDynamics = BrushSecondaryDynamics()), 400f, exported.height.toFloat(), dual = false).size
            try {
                assertNotEquals("Speed spacing did not change the sampled stamp count", constantCount, dynamicCount)
                assertTrue("Live/export speed spacing diverged", maximumRedDifference(exported, live) <= 3)
                assertNotEquals("Speed spacing did not change stamp placement", pixelSignature(constant), pixelSignature(exported))
            } finally {
                exported.recycle()
                live.recycle()
                constant.recycle()
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
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "spray-dynamics-live")
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
