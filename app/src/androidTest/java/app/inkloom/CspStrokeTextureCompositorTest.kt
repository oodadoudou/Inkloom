package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.CspStrokeTextureCompositor
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspTextureMaterial
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CspStrokeTextureCompositorTest {
    @Test fun wholeStrokeTextureUsesPageCoordinatesAndPreservesRgb() {
        val bitmap = Bitmap.createBitmap(8, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val material = CspTextureMaterial(2, 1, intArrayOf(Color.BLACK, Color.WHITE))

        CspStrokeTextureCompositor.applyInPlace(
            bitmap, 8, 2, 0, 0, 8, 2, material,
            1f, 100f, 0f, 0f, 0f, 0,
            0f, 0f, false, false, CspTextureComposite.MULTIPLY,
            false, 0f, CspStrokeTextureCompositor.Workspace(),
        )

        assertEquals(Color.RED, bitmap.getPixel(0, 0) or -0x1000000)
        assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(7, 0)))
        bitmap.recycle()
    }

    @Test fun independentlyRenderedCropsMatchOneContinuousWholeStrokeTexture() {
        val material = CspTextureMaterial(
            4,
            2,
            intArrayOf(
                Color.BLACK, Color.rgb(64, 64, 64), Color.rgb(160, 160, 160), Color.WHITE,
                Color.WHITE, Color.rgb(192, 192, 192), Color.rgb(96, 96, 96), Color.BLACK,
            ),
        )
        fun apply(bitmap: Bitmap, originX: Int, width: Int) {
            CspStrokeTextureCompositor.applyInPlace(
                bitmap, width, 8, originX, 3, 32, 16, material,
                .82f, 137f, 23f, .17f, .31f, 1,
                8f, 19f, true, true, CspTextureComposite.OVERLAY,
                false, 0f, CspStrokeTextureCompositor.Workspace(),
            )
        }
        val whole = Bitmap.createBitmap(20, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val left = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val right = Bitmap.createBitmap(11, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        apply(whole, 5, 20)
        apply(left, 5, 9)
        apply(right, 14, 11)
        for (y in 0 until 8) for (x in 0 until 20) {
            val cropped = if (x < 9) left.getPixel(x, y) else right.getPixel(x - 9, y)
            assertEquals("Texture restarted at crop boundary x=$x y=$y", whole.getPixel(x, y), cropped)
        }
        whole.recycle(); left.recycle(); right.recycle()
    }

    @Test fun applyByPlotAccumulatesTextureButWholeStrokeAppliesItOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tip = solidPng(File(context.cacheDir, "texture-for-plot-tip.png"), 32, 32, Color.BLACK)
        val grain = solidPng(File(context.cacheDir, "texture-for-plot-grain.png"), 8, 8, Color.rgb(128, 128, 128))
        val view = InkCanvasView(context).apply { layout(0, 0, 400, 400) }
        val base = InkStroke(
            id = "texture-for-plot",
            engineKind = BrushEngineKind.STAMP_RASTER,
            points = listOf(StrokePoint(.5f, .5f, 1f)),
            tipAssetPath = tip.absolutePath,
            tipAssetPaths = listOf(tip.absolutePath),
            grainAssetPath = grain.absolutePath,
            grainDensity = 1f,
            grainCompositeMode = CspTextureComposite.MULTIPLY,
            width = 200f,
            flow = 1f,
            stampCount = 2,
        )
        fun render(applyPerPlot: Boolean): Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).also {
            view.resetActiveRenderState()
            view.drawActiveStrokes(Canvas(it), listOf(base.copy(grainApplyPerPlot = applyPerPlot)))
        }

        val perPlot = render(true)
        val wholeStroke = render(false)
        val perPlotAlpha = Color.alpha(perPlot.getPixel(200, 200))
        val wholeStrokeAlpha = Color.alpha(wholeStroke.getPixel(200, 200))
        assertTrue("Repeated textured plots must accumulate alpha", perPlotAlpha > wholeStrokeAlpha + 35)
        assertTrue("A single whole-stroke texture pass should retain half-density grain", wholeStrokeAlpha in 115..140)
        val service = DocumentAssetService(context)
        val exportedPerPlot = service.renderPageBitmap(InkPage(width = 400f, height = 400f, background = PageBackground.Paper(), strokes = listOf(base.copy(grainApplyPerPlot = true))), 400)
        val exportedWholeStroke = service.renderPageBitmap(InkPage(width = 400f, height = 400f, background = PageBackground.Paper(), strokes = listOf(base.copy(grainApplyPerPlot = false))), 400)
        val perPlotLuminance = Color.red(exportedPerPlot.getPixel(200, 200))
        val wholeStrokeLuminance = Color.red(exportedWholeStroke.getPixel(200, 200))
        assertTrue("Export must preserve per-plot accumulation: per=$perPlotLuminance whole=$wholeStrokeLuminance", perPlotLuminance + 35 < wholeStrokeLuminance)
        val liveSourceRed = Color.red(wholeStroke.getPixel(200, 200))
        val paperRed = Color.red(exportedWholeStroke.getPixel(0, 0))
        val expectedExportRed = (liveSourceRed * wholeStrokeAlpha + paperRed * (255 - wholeStrokeAlpha) + 127) / 255
        assertTrue("Live and export must share whole-stroke texture alpha: expected=$expectedExportRed export=$wholeStrokeLuminance", kotlin.math.abs(expectedExportRed - wholeStrokeLuminance) <= 3)
        perPlot.recycle()
        wholeStroke.recycle()
        exportedPerPlot.recycle()
        exportedWholeStroke.recycle()
    }

    @Test fun incrementalWholeStrokeTextureIsPixelIdenticalToCompleteReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tip = solidPng(File(context.cacheDir, "whole-texture-parity-tip.png"), 32, 24, Color.BLACK)
        val grain = solidPng(File(context.cacheDir, "whole-texture-parity-grain.png"), 8, 8, Color.rgb(112, 112, 112))
        val points = List(32) { index ->
            StrokePoint(.12f + index / 31f * .76f, .5f + kotlin.math.sin(index * .38f) * .12f, .55f + index / 31f * .4f, timeMs = index.toLong())
        }
        val stroke = InkStroke(
            id = "whole-texture-parity",
            engineKind = BrushEngineKind.STAMP_RASTER,
            points = points,
            tipAssetPath = tip.absolutePath,
            tipAssetPaths = listOf(tip.absolutePath),
            grainAssetPath = grain.absolutePath,
            grainDensity = .76f,
            grainApplyPerPlot = false,
            grainCompositeMode = CspTextureComposite.OVERLAY,
            width = 92f,
            spacing = .14f,
            flow = .84f,
        )
        val view = InkCanvasView(context).apply { layout(0, 0, 480, 360) }
        val complete = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
        val incremental = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
        try {
            view.resetActiveRenderState()
            view.drawActiveStrokes(Canvas(complete), listOf(stroke))
            view.resetActiveRenderState()
            for (count in 2..points.size) {
                view.drawActiveStrokes(
                    Canvas(incremental),
                    listOf(stroke.copy(points = points.take(count))),
                    incrementalStampDraw = true,
                )
            }
            val completePixels = IntArray(complete.width * complete.height)
            val incrementalPixels = IntArray(incremental.width * incremental.height)
            complete.getPixels(completePixels, 0, complete.width, 0, 0, complete.width, complete.height)
            incremental.getPixels(incrementalPixels, 0, incremental.width, 0, 0, incremental.width, incremental.height)
            assertTrue(
                "Incremental whole-stroke texture diverged at ${completePixels.indices.count { completePixels[it] != incrementalPixels[it] }} pixels",
                completePixels.contentEquals(incrementalPixels),
            )
        } finally {
            complete.recycle(); incremental.recycle(); tip.delete(); grain.delete()
        }
    }

    @Test fun growingWholeStrokeTextureStaysWithinTheInteractiveFrameBudget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val tip = solidPng(File(context.cacheDir, "whole-texture-perf-tip.png"), 48, 48, Color.BLACK)
            val grain = solidPng(File(context.cacheDir, "whole-texture-perf-grain.png"), 16, 16, Color.rgb(144, 144, 144))
            val view = InkCanvasView(context).apply { layout(0, 0, 1600, 1000) }
            val target = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
            try {
                val points = List(160) { index ->
                    StrokePoint(.06f + index / 159f * .88f, .5f + kotlin.math.sin(index * .11f) * .035f, 1f, timeMs = index.toLong())
                }
                val base = InkStroke(
                    id = "whole-texture-performance",
                    engineKind = BrushEngineKind.STAMP_RASTER,
                    points = points.take(2),
                    tipAssetPath = tip.absolutePath,
                    tipAssetPaths = listOf(tip.absolutePath),
                    grainAssetPath = grain.absolutePath,
                    grainDensity = .72f,
                    grainApplyPerPlot = false,
                    width = 76f,
                    spacing = .13f,
                    flow = .9f,
                )
                // Decode the authored materials and grow reusable workspaces before measurement.
                view.resetActiveRenderState()
                view.drawActiveStrokes(Canvas(target), listOf(base.copy(points = points.take(40))), incrementalStampDraw = true)
                val frameTimes = ArrayList<Long>(120)
                for (count in 41..160) {
                    val started = SystemClock.elapsedRealtimeNanos()
                    view.drawActiveStrokes(Canvas(target), listOf(base.copy(points = points.take(count))), incrementalStampDraw = true)
                    frameTimes += SystemClock.elapsedRealtimeNanos() - started
                }
                val sorted = frameTimes.sorted()
                val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
                android.util.Log.i("InkloomPerf", "whole_stroke_texture_p95_ms=$p95Ms")
                assertTrue("Whole-stroke texture p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
            } finally {
                target.recycle()
                tip.delete()
                grain.delete()
            }
        }
    }

    private fun solidPng(file: File, width: Int, height: Int, color: Int): File {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(color)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return file
    }
}
