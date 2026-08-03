package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CspAntiAlias
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
class CspAntiAliasRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun stampedModesRemainDistinctAndLiveMatchesExport() {
        val tip = File(context.cacheDir, "anti-alias-render-tip-${System.nanoTime()}.png")
        val pixels = IntArray(64) { Color.WHITE }.also {
            for (y in 2..5) for (x in 2..5) it[y * 8 + x] = if (x in 3..4 && y in 3..4) Color.BLACK else Color.rgb(191, 191, 191)
        }
        writePng(tip, pixels)
        try {
            val renders = listOf(CspAntiAlias.NONE, CspAntiAlias.WEAK, CspAntiAlias.MEDIUM, CspAntiAlias.STRONG).map { mode ->
                val stroke = InkStroke(
                    points = listOf(StrokePoint(.5f, .5f)),
                    width = 320f,
                    sizePressureEnabled = false,
                    tipAssetPath = tip.absolutePath,
                    tipAssetPaths = listOf(tip.absolutePath),
                    antiAliasMode = mode,
                )
                val exported = renderExport(stroke)
                val live = renderLive(stroke, exported.height)
                assertTrue("Live/export mode $mode diverged", maximumRedDifference(exported, live) <= 3)
                live.recycle()
                exported
            }

            assertEquals(4, renders.map(::pixelSignature).distinct().size)
            renders.forEach(Bitmap::recycle)
        } finally {
            tip.delete()
        }
    }

    @Test fun lineFallbackStrictlyDistinguishesOffFromEnabled() {
        val base = InkStroke(
            points = listOf(StrokePoint(.35f, .35f), StrokePoint(.65f, .61f)),
            width = 47f,
            sizePressureEnabled = false,
        )
        val off = renderExport(base.copy(antiAliasMode = CspAntiAlias.NONE))
        val enabled = renderExport(base.copy(antiAliasMode = CspAntiAlias.MEDIUM))
        try {
            assertTrue("Line fallback ignored CSP AntiAlias", pixelSignature(off) != pixelSignature(enabled))
        } finally {
            off.recycle()
            enabled.recycle()
        }
    }

    @Test fun secondaryTipKeepsItsOwnModeWhenUsingMainTipFallback() {
        val tip = File(context.cacheDir, "dual-anti-alias-fallback-${System.nanoTime()}.png")
        val pixels = IntArray(64) { Color.WHITE }.also { it[27] = Color.rgb(191, 191, 191) }
        writePng(tip, pixels)
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                width = 320f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                antiAliasMode = CspAntiAlias.NONE,
                dualBrush = true,
            )
            val off = renderExport(base.copy(dualAntiAliasMode = CspAntiAlias.NONE))
            val strong = renderExport(base.copy(dualAntiAliasMode = CspAntiAlias.STRONG))
            try {
                assertTrue("Secondary tip inherited the main AntiAlias mode", pixelSignature(off) != pixelSignature(strong))
            } finally {
                off.recycle()
                strong.recycle()
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
                setDocumentStrokes(listOf(stroke), "anti-alias-live-${stroke.antiAliasMode}")
                layout(0, 0, 400, height)
            }
            rendered = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        require(first.width == second.width && first.height == second.height)
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

    private fun writePng(file: File, pixels: IntArray) {
        val bitmap = Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
