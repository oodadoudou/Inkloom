package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushTipFlip
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
class CspDualBrushRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun applyRgbSwitchesBetweenDrawingColorAndSecondaryMaterialColorOnCanvasAndExport() {
        val tip = File(context.cacheDir, "dual-rgb-material.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).drawRect(4f, 4f, 28f, 28f, Paint().apply { color = Color.rgb(225, 35, 60) })
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF2050E0,
                width = 280f,
                flow = 0f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualFlow = 1f,
                dualCompositeMode = 1,
            )
            val service = DocumentAssetService(context)
            val alphaOnly = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val sourceRgb = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(dualApplyRgb = true))), 400)
            val liveAlphaOnly = renderLiveCanvas(base)
            val liveSourceRgb = renderLiveCanvas(base.copy(dualApplyRgb = true))
            try {
                val alphaPixel = alphaOnly.getPixel(200, alphaOnly.height / 2)
                val rgbPixel = sourceRgb.getPixel(200, sourceRgb.height / 2)
                assertTrue("Alpha-only Dual Brush must use the selected blue drawing color", Color.blue(alphaPixel) > Color.red(alphaPixel))
                assertTrue("Apply RGB must retain the red secondary material", Color.red(rgbPixel) > Color.blue(rgbPixel))
                assertEquals(alphaPixel, liveAlphaOnly.getPixel(200, liveAlphaOnly.height / 2))
                assertEquals(rgbPixel, liveSourceRgb.getPixel(200, liveSourceRgb.height / 2))
            } finally {
                alphaOnly.recycle()
                sourceRgb.recycle()
                liveAlphaOnly.recycle()
                liveSourceRgb.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun densityOnlyScreenChangesCoverageWithoutLighteningTheDrawingColor() {
        val tip = File(context.cacheDir, "dual-screen-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF606060,
                width = 300f,
                opacity = 1f,
                flow = 1f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualSizeScale = 1f,
                dualFlow = 1f,
            )
            val service = DocumentAssetService(context)
            val normal = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(dualCompositeMode = 1))), 400)
            val screen = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base.copy(dualCompositeMode = 7))), 400)
            try {
                val normalPixel = normal.getPixel(200, normal.height / 2)
                val screenPixel = screen.getPixel(200, screen.height / 2)
                assertEquals("Density-only Screen and Normal share union coverage for opaque coincident streams", normalPixel, screenPixel)
                assertEquals(Color.red(screenPixel), Color.green(screenPixel))
                assertEquals(Color.green(screenPixel), Color.blue(screenPixel))
            } finally {
                normal.recycle()
                screen.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun allThirteenCspModesMatchBetweenLiveCanvasAndExportWithAndWithoutRgb() {
        val tip = File(context.cacheDir, "dual-all-modes-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).drawCircle(16f, 16f, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(166, 210, 45, 70) })
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = .65f)),
                colorArgb = 0xFF3060D0,
                width = 260f,
                flow = .62f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualSizeScale = .72f,
                dualFlow = .54f,
            )
            val service = DocumentAssetService(context)
            for (applyRgb in listOf(false, true)) {
                for (mode in 1..13) {
                    val stroke = base.copy(dualCompositeMode = mode, dualApplyRgb = applyRgb)
                    val exported = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)
                    val live = renderLiveCanvas(stroke)
                    try {
                        val points = listOf(200 to live.height / 2, 216 to live.height / 2, 230 to live.height / 2)
                        points.forEach { (x, y) ->
                            assertEquals("mode=$mode applyRgb=$applyRgb x=$x", exported.getPixel(x, y), live.getPixel(x, y))
                        }
                    } finally {
                        exported.recycle()
                        live.recycle()
                    }
                }
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun secondarySizeUsesItsOwnPressureEffectorOnCanvasAndExport() {
        val tip = File(context.cacheDir, "dual-independent-dynamics-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = .1f)),
                colorArgb = 0xFF111111,
                width = 200f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                sizePressureEnabled = true,
                sizePressureMinimum = .1f,
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualSizeScale = 4f,
                dualCompositeMode = 1,
            )
            val inheritedMainWouldShrink = base.copy(
                dualSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .1f),
            )
            val service = DocumentAssetService(context)
            val independent = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val explicitlyPressureControlled = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(inheritedMainWouldShrink)), 400)
            val live = renderLiveCanvas(base)
            try {
                val x = 270
                val exportY = independent.height / 2
                val liveY = live.height / 2
                assertTrue("Disabled secondary pressure must keep the large secondary footprint", Color.red(independent.getPixel(x, exportY)) < 80)
                assertTrue("An explicitly enabled secondary pressure effector must shrink that footprint", Color.red(explicitlyPressureControlled.getPixel(x, exportY)) > 240)
                assertEquals("Live and export passes must evaluate the same independent secondary dynamics", independent.getPixel(x, exportY), live.getPixel(x, liveY))
            } finally {
                independent.recycle()
                explicitlyPressureControlled.recycle()
                live.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun secondarySprayParticleSizeUsesItsOwnPressureEffectorOnCanvasAndExport() {
        val tip = File(context.cacheDir, "dual-spray-dynamics-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f, pressure = .1f)),
                colorArgb = 0xFF111111,
                width = 240f,
                flow = 0f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualFlow = 1f,
                dualSpray = true,
                dualSpraySpread = 0f,
                dualSprayParticleSize = 100f,
                dualSprayParticleSizeSyncBrushSize = true,
                dualCompositeMode = 1,
            )
            val pressureControlled = base.copy(
                dualSprayParticleSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .1f),
            )
            val service = DocumentAssetService(context)
            val independent = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(base)), 400)
            val reduced = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(pressureControlled)), 400)
            val live = renderLiveCanvas(base)
            try {
                val x = 222
                val exportY = independent.height / 2
                val liveY = live.height / 2
                assertTrue("Disabled secondary particle pressure must preserve its full footprint", Color.red(independent.getPixel(x, exportY)) < 80)
                assertTrue("Enabled secondary particle pressure must shrink the particle footprint", Color.red(reduced.getPixel(x, exportY)) > 240)
                assertEquals(independent.getPixel(x, exportY), live.getPixel(x, liveY))
            } finally {
                independent.recycle()
                reduced.recycle()
                live.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun secondaryTextureChangesOnlyTheSecondaryStreamAndMatchesLiveExport() {
        val tip = File(context.cacheDir, "dual-texture-tip.png")
        val grain = File(context.cacheDir, "dual-texture-grain.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(0f, 0f, 16f, 32f, Paint().apply { color = Color.BLACK })
            grain.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF202020,
                width = 180f,
                flow = 0f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                dualBrush = true,
                dualTipAssetPaths = listOf(tip.absolutePath),
                dualFlow = 1f,
                dualCompositeMode = 1,
                dualGrainAssetPath = grain.absolutePath,
                dualGrainDensity = 1f,
                dualGrainScale = 100f,
            )
            val service = DocumentAssetService(context)
            for (perPlot in listOf(true, false)) {
                val stroke = base.copy(dualGrainApplyPerPlot = perPlot)
                val exported = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)
                val untextured = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke.copy(dualGrainAssetPath = null))), 400)
                val live = renderLiveCanvas(stroke)
                try {
                    var texturedPixels = 0
                    var exportedInk = 0L
                    var liveInk = 0L
                    var samples = 0
                    for (y in 220 until 345 step 8) for (x in 110 until 290 step 8) {
                        val exportedRed = Color.red(exported.getPixel(x, y))
                        val liveRed = Color.red(live.getPixel(x, y))
                        exportedInk += 255 - exportedRed
                        liveInk += 255 - liveRed
                        samples++
                        if (exported.getPixel(x, y) != untextured.getPixel(x, y)) texturedPixels++
                    }
                    assertTrue("Secondary texture must modulate secondary coverage", texturedPixels > 3)
                    assertTrue(
                        "Live and export must retain equivalent secondary texture coverage",
                        kotlin.math.abs(exportedInk - liveInk) <= samples * 18L,
                    )
                } finally {
                    exported.recycle()
                    untextured.recycle()
                    live.recycle()
                }
            }
        } finally {
            tip.delete()
            grain.delete()
        }
    }

    @Test fun fixedHorizontalFlipMirrorsAnAsymmetricImportedTip() {
        val tip = File(context.cacheDir, "asymmetric-flip-tip.png")
        Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawRect(4f, 4f, 22f, 28f, Paint().apply { color = Color.BLACK })
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val stroke = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF111111,
                width = 240f,
                opacity = 1f,
                flow = 1f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
            )
            val service = DocumentAssetService(context)
            val normal = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)
            val flipped = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke.copy(tipFlipHorizontalMode = BrushTipFlip.FLIP))), 400)
            try {
                val y = normal.height / 2
                assertTrue("Unflipped tip must place its ink left of center", Color.red(normal.getPixel(182, y)) < Color.red(normal.getPixel(218, y)))
                assertTrue("Flipped tip must place its ink right of center", Color.red(flipped.getPixel(218, y)) < Color.red(flipped.getPixel(182, y)))
            } finally {
                normal.recycle()
                flipped.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun liveCanvasMatchesExportedHorizontalFlip() {
        val tip = File(context.cacheDir, "live-asymmetric-flip-tip.png")
        Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawRect(4f, 4f, 22f, 28f, Paint().apply { color = Color.BLACK })
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val stroke = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF111111,
                width = 240f,
                opacity = 1f,
                flow = 1f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                tipFlipHorizontalMode = BrushTipFlip.FLIP,
            )
            val live = renderLiveCanvas(stroke)
            val exported = DocumentAssetService(context).renderPageBitmap(
                InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
                400,
            )
            try {
                val liveY = live.height / 2
                val exportY = exported.height / 2
                assertTrue("Live canvas must mirror the imported tip", Color.red(live.getPixel(218, liveY)) < Color.red(live.getPixel(182, liveY)))
                assertEquals(exported.getPixel(182, exportY), live.getPixel(182, liveY))
                assertEquals(exported.getPixel(218, exportY), live.getPixel(218, liveY))
            } finally {
                live.recycle()
                exported.recycle()
            }
        } finally {
            tip.delete()
        }
    }

    @Test fun darkenTipCompositionIsIsolatedFromEarlierPageInk() {
        val tip = File(context.cacheDir, "darken-isolation-tip.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val under = InkStroke(points = listOf(StrokePoint(.5f, .5f)), colorArgb = 0xFFFF0000, width = 300f, sizePressureEnabled = false)
            val top = InkStroke(points = listOf(StrokePoint(.5f, .5f)), colorArgb = 0xFFFFFF00, width = 180f, opacity = .5f, flow = 1f, sizePressureEnabled = false, tipAssetPath = tip.absolutePath, tipAssetPaths = listOf(tip.absolutePath))
            val service = DocumentAssetService(context)
            val normal = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(under, top)), 400)
            val darken = service.renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(under, top.copy(blendTipsWithDarken = true))), 400)
            try {
                assertEquals("A single isolated Darken stamp must composite onto earlier ink normally", normal.getPixel(200, normal.height / 2), darken.getPixel(200, darken.height / 2))
            } finally {
                normal.recycle()
                darken.recycle()
            }
        } finally {
            tip.delete()
        }
    }


    private fun renderLiveCanvas(stroke: InkStroke): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "csp-flip-live-test")
                layout(0, 0, 400, 566)
            }
            rendered = Bitmap.createBitmap(400, 566, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }
}
