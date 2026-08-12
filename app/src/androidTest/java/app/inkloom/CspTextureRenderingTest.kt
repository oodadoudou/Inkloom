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
import app.inkloom.document.ProcreateFiltering
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

    @Test fun procreateDepthMinimumBoundsPressureAndMatchesLiveCanvas() {
        val tip = File(context.cacheDir, "depth-minimum-tip.png")
        val grain = File(context.cacheDir, "depth-minimum-grain.png")
        writePng(tip, IntArray(64) { Color.BLACK })
        writePng(grain, IntArray(64) { Color.WHITE })
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = 0f)),
                colorArgb = 0xFF111111,
                width = 240f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainDensityDynamics = BrushParameterDynamics(pressureEnabled = true),
            )
            val boundedStroke = base.copy(grainDensityMinimum = .4f)
            val unbounded = DocumentAssetService(context).renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val bounded = DocumentAssetService(context).renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(boundedStroke)), 400)
            val live = renderLive(boundedStroke)
            try {
                val x = 200
                val exportY = bounded.height / 2
                val liveY = live.height / 2
                assertTrue("Depth Minimum must retain more grain contrast at zero pressure", Color.red(bounded.getPixel(x, exportY)) > Color.red(unbounded.getPixel(x, unbounded.height / 2)) + 30)
                assertTrue("Live/export depth minimum diverged", kotlin.math.abs(Color.red(bounded.getPixel(x, exportY)) - Color.red(live.getPixel(x, liveY))) <= 12)
            } finally {
                unbounded.recycle(); bounded.recycle(); live.recycle()
            }
        } finally {
            tip.delete(); grain.delete()
        }
    }

    @Test fun procreateMovingAndTexturizedGrainUseDifferentCoordinateSpacesWithLiveExportParity() {
        val tip = File(context.cacheDir, "grain-coordinate-tip.png")
        val grain = File(context.cacheDir, "grain-coordinate-source.png")
        writePng(tip, IntArray(64) { Color.BLACK })
        writePng(grain, IntArray(64) { index -> if (index % 8 in 4..5) Color.BLACK else Color.WHITE })
        try {
            fun stroke(x: Float, movement: Float) = InkStroke(
                renderSeed = 17,
                points = listOf(StrokePoint(x, .5f, pressure = 1f)),
                colorArgb = 0xFF111111,
                width = 400f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainScale = 100f,
                grainMovement = movement,
                grainZoom = 0f,
            )
            fun renderExport(stroke: InkStroke) = DocumentAssetService(context).renderPageBitmap(
                InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400,
            )

            val movingAt100 = renderExport(stroke(.25f, 0f))
            val movingAt125 = renderExport(stroke(.3125f, 0f))
            val fixedAt100 = renderExport(stroke(.25f, 1f))
            val fixedStrokeAt125 = stroke(.3125f, 1f)
            val fixedAt125 = renderExport(fixedStrokeAt125)
            val fixedLiveAt125 = renderLive(fixedStrokeAt125)
            try {
                assertEquals(
                    "Moving grain must retain the same tip-local texture as the dab moves",
                    0,
                    patchDifference(movingAt100, 100, movingAt100.height / 2, movingAt125, 125, movingAt125.height / 2, 36),
                )
                assertEquals(
                    "Texturized grain begins at the same phase when the page coordinate is one complete wavelength",
                    0,
                    patchDifference(movingAt100, 100, movingAt100.height / 2, fixedAt100, 100, fixedAt100.height / 2, 36),
                )
                assertTrue(
                    "Texturized grain must stay fixed behind a moved tip instead of repeating its local phase",
                    patchDifference(movingAt125, 125, movingAt125.height / 2, fixedAt125, 125, fixedAt125.height / 2, 36) > 100,
                )
                assertEquals(
                    "Live and export renderers must use the same page-space grain phase",
                    0,
                    patchDifference(fixedAt125, 125, fixedAt125.height / 2, fixedLiveAt125, 125, fixedLiveAt125.height / 2, 36),
                )
            } finally {
                movingAt100.recycle(); movingAt125.recycle(); fixedAt100.recycle(); fixedAt125.recycle(); fixedLiveAt125.recycle()
            }
        } finally {
            tip.delete(); grain.delete()
        }
    }

    @Test fun procreateGrainFilteringModesRemainDistinctAndImprovedMatchesLiveExport() {
        val tip = File(context.cacheDir, "grain-filter-tip.png")
        val grain = File(context.cacheDir, "grain-filter-source.png")
        writePng(tip, IntArray(64) { Color.BLACK })
        writePng(grain, IntArray(64) { index ->
            val x = index % 8; val y = index / 8
            if ((x + y * 3) % 5 < 2) Color.BLACK else Color.WHITE
        })
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF202020,
                width = 320f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainScale = 73f,
                grainRotationDegrees = 19f,
            )
            fun export(mode: Int) = DocumentAssetService(context).renderPageBitmap(
                InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(grainFilteringMode = mode))), 400,
            )
            val nearest = export(ProcreateFiltering.NONE)
            val classic = export(ProcreateFiltering.CLASSIC)
            val improvedStroke = base.copy(grainFilteringMode = ProcreateFiltering.IMPROVED)
            val improved = export(ProcreateFiltering.IMPROVED)
            val live = renderLive(improvedStroke, improved.height)
            try {
                assertTrue(pixelSignature(nearest) != pixelSignature(classic))
                assertTrue(pixelSignature(classic) != pixelSignature(improved))
                assertEquals(improved.width, live.width)
                assertTrue(
                    "Improved filtering must use the same grain pixels in live and export; differing patch pixels=${patchDifference(improved, improved.width / 2, improved.height / 2, live, live.width / 2, live.height / 2, 120)}",
                    patchDifference(improved, improved.width / 2, improved.height / 2, live, live.width / 2, live.height / 2, 120) <= 4,
                )
            } finally {
                nearest.recycle(); classic.recycle(); improved.recycle(); live.recycle()
            }
        } finally {
            tip.delete(); grain.delete()
        }
    }

    private fun renderLive(stroke: InkStroke, outputHeight: Int = 566): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "csp-height-live")
                layout(0, 0, 400, outputHeight)
            }
            rendered = Bitmap.createBitmap(400, outputHeight, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }

    private fun writePng(file: File, pixels: IntArray) {
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun patchDifference(first: Bitmap, firstX: Int, firstY: Int, second: Bitmap, secondX: Int, secondY: Int, radius: Int): Int {
        var different = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            if (first.getPixel(firstX + dx, firstY + dy) != second.getPixel(secondX + dx, secondY + dy)) different++
        }
        return different
    }

    private fun pixelSignature(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.contentHashCode()
    }
}
