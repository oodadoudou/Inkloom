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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushBlurRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun lineBrushBlurProducesMatchingLiveAndExportHalos() {
        assertMatchingHalo(
            InkStroke(
                points = listOf(StrokePoint(.5f, .5f)),
                colorArgb = 0xFF111111,
                width = 160f,
                sizePressureEnabled = false,
                blur = 80f,
            ),
        )
    }

    @Test fun stampedBrushBlurProducesMatchingLiveAndExportHalos() {
        val tip = File(context.cacheDir, "brush-blur-tip.png")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLACK)
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            assertMatchingHalo(
                InkStroke(
                    points = listOf(StrokePoint(.5f, .5f)),
                    colorArgb = 0xFF111111,
                    width = 160f,
                    sizePressureEnabled = false,
                    blur = 80f,
                    tipAssetPath = tip.absolutePath,
                    tipAssetPaths = listOf(tip.absolutePath),
                ),
            )
        } finally {
            tip.delete()
        }
    }

    private fun assertMatchingHalo(stroke: InkStroke) {
        val crisp = DocumentAssetService(context).renderPageBitmap(
            InkPage(background = PageBackground.Paper(), strokes = listOf(stroke.copy(blur = 0f))),
            400,
        )
        val exported = DocumentAssetService(context).renderPageBitmap(
            InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
            400,
        )
        val live = renderLive(stroke)
        try {
            val x = 236
            val exportY = exported.height / 2
            val liveY = live.height / 2
            val crispRed = Color.red(crisp.getPixel(x, exportY))
            val exportRed = Color.red(exported.getPixel(x, exportY))
            val liveRed = Color.red(live.getPixel(x, liveY))
            assertTrue("Expected a blur halo beyond the crisp footprint: crisp=$crispRed blurred=$exportRed", crispRed > 250 && exportRed < 250)
            assertTrue("Live/export blur halos diverged: live=$liveRed export=$exportRed", kotlin.math.abs(liveRed - exportRed) <= 3)
        } finally {
            crisp.recycle()
            exported.recycle()
            live.recycle()
        }
    }

    private fun renderLive(stroke: InkStroke): Bitmap {
        lateinit var rendered: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "brush-blur-live")
                layout(0, 0, 400, 566)
            }
            rendered = Bitmap.createBitmap(400, 566, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return rendered
    }
}
