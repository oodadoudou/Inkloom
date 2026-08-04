package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CspDualBrushBlendMode
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

    @Test fun screenModeIsAppliedInsideTheDualStrokeBeforePageComposition() {
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
                assertTrue("Screen must lighten the overlap of the two brush streams", Color.red(screenPixel) > Color.red(normalPixel))
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

    @Test fun unsupportedCspModesUseExplicitNormalFallback() {
        assertEquals(android.graphics.BlendMode.SRC_OVER, CspDualBrushBlendMode.resolve(4))
        assertEquals(android.graphics.BlendMode.SRC_OVER, CspDualBrushBlendMode.resolve(13))
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
