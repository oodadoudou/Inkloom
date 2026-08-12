package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateAlphaThresholdTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun thresholdConvertsAuthoredTipCoverageToBinaryAndLiveMatchesExport() {
        val tip = File(context.cacheDir, "alpha-threshold-tip.png")
        val pixels = IntArray(64) { Color.WHITE }
        for (y in 1..6) for (x in 1..6) pixels[y * 8 + x] = Color.rgb(150, 150, 150)
        for (y in 3..4) for (x in 3..4) pixels[y * 8 + x] = Color.BLACK
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            tip.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val soft = requireNotNull(BrushTipTextureDecoder.decode(tip.absolutePath, 1f, null, 0f, antiAliasMode = CspAntiAlias.MEDIUM))
            val binary = requireNotNull(BrushTipTextureDecoder.decode(tip.absolutePath, 1f, null, 0f, antiAliasMode = CspAntiAlias.MEDIUM, alphaThresholdEnabled = true, alphaThreshold = .55f))
            try {
                assertTrue(Color.alpha(soft.getPixel(1, 1)) in 1..254)
                assertEquals(0, Color.alpha(binary.getPixel(1, 1)))
                assertEquals(255, Color.alpha(binary.getPixel(3, 3)))
                assertTrue((0 until binary.height).all { y -> (0 until binary.width).all { x -> Color.alpha(binary.getPixel(x, y)) in setOf(0, 255) } })
            } finally {
                soft.recycle(); binary.recycle()
            }

            val base = InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF111111,
                width = 260f,
                flow = 1f,
                opacity = 1f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                antiAliasMode = CspAntiAlias.MEDIUM,
            )
            val thresholded = base.copy(alphaThresholdEnabled = true, alphaThreshold = .55f)
            val softExport = renderExport(base)
            val binaryExport = renderExport(thresholded)
            val live = renderLive(thresholded, binaryExport.height)
            try {
                assertNotEquals(signature(softExport), signature(binaryExport))
                assertEquals(signature(binaryExport), signature(live))
            } finally {
                softExport.recycle(); binaryExport.recycle(); live.recycle()
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
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "alpha-threshold-live")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()
}
