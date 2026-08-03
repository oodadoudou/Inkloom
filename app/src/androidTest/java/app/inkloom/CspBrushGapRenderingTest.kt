package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CspBrushGap
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CspBrushGapRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun automaticGapChangesStampDensityAndMatchesLiveExport() {
        val tip = File(context.cacheDir, "csp-gap-tip.png")
        writeTip(tip)
        try {
            val base = InkStroke(
                renderSeed = 225,
                points = listOf(StrokePoint(.2f, .5f, timeMs = 10), StrokePoint(.8f, .5f, timeMs = 100)),
                width = 130f,
                spacing = .92f,
                sizePressureEnabled = false,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
            )
            val wide = base.copy(spacingMode = CspBrushGap.WIDE)
            val narrow = base.copy(spacingMode = CspBrushGap.NARROW)
            val wideExport = renderExport(wide)
            val narrowExport = renderExport(narrow)
            val fixedExport = renderExport(base)
            val wideLive = renderLive(wide, wideExport.height)
            val narrowLive = renderLive(narrow, narrowExport.height)
            try {
                assertEquals(signature(wideExport), signature(wideLive))
                assertEquals(signature(narrowExport), signature(narrowLive))
                assertNotEquals(signature(wideExport), signature(narrowExport))
                assertNotEquals(signature(fixedExport), signature(narrowExport))
            } finally { wideExport.recycle(); narrowExport.recycle(); fixedExport.recycle(); wideLive.recycle(); narrowLive.recycle() }
        } finally { tip.delete() }
    }

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)), 400)

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { pagePaperColor = Color.WHITE; setDocumentStrokes(listOf(stroke), "gap-${stroke.spacingMode}"); layout(0, 0, 400, height) }
            bitmap = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return bitmap
    }

    private fun signature(bitmap: Bitmap): Int { val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); return pixels.contentHashCode() }

    private fun writeTip(file: File) {
        val pixels = IntArray(64) { Color.WHITE }.also { values -> for (y in 2..5) for (x in 2..5) values[y * 8 + x] = Color.BLACK }
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle() }
    }
}
