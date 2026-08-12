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
class ProcreatePathJitterRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun lateralAndLongitudinalJitterRemainDeterministicAndMatchLiveExport() {
        val tip = File(context.cacheDir, "procreate-path-jitter-${System.nanoTime()}.png")
        Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(12f, 12f, 11f, android.graphics.Paint().apply { color = Color.BLACK })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            val base = InkStroke(
                points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f, timeMs = 10)),
                width = 55f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                spacing = .35f,
                renderSeed = 733,
            )
            val plain = renderExport(base)
            val jitteredStroke = base.copy(strokePathJitter = .55f, strokePathLongitudinalJitter = .35f)
            val jittered = renderExport(jitteredStroke)
            val live = renderLive(jitteredStroke, jittered.height)
            try {
                assertNotEquals("Path jitter did not move the authored stamps", signature(plain), signature(jittered))
                assertTrue("Live/export path jitter diverged", maximumRedDifference(jittered, live) <= 3)
            } finally {
                plain.recycle(); jittered.recycle(); live.recycle()
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
                setDocumentStrokes(listOf(stroke), "path-jitter-render")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        }
        return maximum
    }
}
