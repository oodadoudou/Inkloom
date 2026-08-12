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
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateRenderingModeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun sixModesRenderInOfficialLightestToStrongestOrderAndLiveMatchesExport() {
        val modes = ProcreateRenderingMode.entries
        val exports = modes.map(::renderExport)
        try {
            val reds = exports.map { Color.red(it.getPixel(200, it.height / 2)) }
            assertTrue("Rendering modes must darken monotonically: $reds", reds.zipWithNext().all { (lighter, stronger) -> lighter > stronger })

            val live = renderLive(ProcreateRenderingMode.INTENSE_BLENDING, exports.last().height)
            try {
                assertEquals(exports.last().getPixel(200, exports.last().height / 2), live.getPixel(200, live.height / 2))
            } finally {
                live.recycle()
            }
        } finally {
            exports.forEach(Bitmap::recycle)
        }
    }

    private fun stroke(mode: ProcreateRenderingMode) = InkStroke(
        points = listOf(StrokePoint(.5f, .5f)),
        colorArgb = 0xFF111111,
        width = 180f,
        flow = .22f,
        opacity = 1f,
        sizePressureEnabled = false,
        stampCount = 4,
        procreateRenderingMode = mode,
    )

    private fun renderExport(mode: ProcreateRenderingMode): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke(mode))),
        400,
    )

    private fun renderLive(mode: ProcreateRenderingMode, height: Int): Bitmap {
        lateinit var result: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke(mode)), "rendering-mode-live")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }
}
