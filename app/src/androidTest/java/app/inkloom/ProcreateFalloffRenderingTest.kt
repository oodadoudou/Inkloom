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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateFalloffRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun falloffFadesFromStrokeStartAndMatchesLiveExport() {
        val stroke = InkStroke(
            points = (0..20).map { index -> StrokePoint(.1f + index * .04f, .5f, timeMs = index.toLong()) },
            width = 80f,
            strokeFalloff = .75f,
            flow = 1f,
            sizePressureEnabled = false,
            renderSeed = 727,
        )
        val export = DocumentAssetService(context).renderPageBitmap(
            InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
            400,
        )
        lateinit var live: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "falloff-render")
                layout(0, 0, export.width, export.height)
            }
            live = Bitmap.createBitmap(export.width, export.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            assertTrue("Falloff start should remain visibly darker", averageRed(export, 35, 70) + 30 < averageRed(export, 300, 350))
            assertTrue("Live/export falloff rendering diverged", maximumRedDifference(export, live) <= 3)
        } finally {
            export.recycle(); live.recycle()
        }
    }

    private fun averageRed(bitmap: Bitmap, left: Int, right: Int): Int {
        var sum = 0L
        var count = 0L
        for (y in bitmap.height / 2 - 30 until bitmap.height / 2 + 30) for (x in left until right) {
            sum += Color.red(bitmap.getPixel(x, y)); count++
        }
        return (sum / count.coerceAtLeast(1)).toInt()
    }

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        }
        return maximum
    }
}
