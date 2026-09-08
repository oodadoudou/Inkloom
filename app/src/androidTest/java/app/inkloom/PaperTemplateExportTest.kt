package app.inkloom

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageBackground
import app.inkloom.document.PaperPattern
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaperTemplateExportTest {
    private val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())

    @Test fun densityChangesSpacingAndExportMatchesLiveCanvasAtDifferentResolutions() {
        for (density in app.inkloom.document.PaperDensity.entries) {
            val page = InkPage(background = PageBackground.Paper(pattern = PaperPattern.RULED, density = density))
            val export = assets.renderPageBitmap(page, 400)
            val live = android.graphics.Bitmap.createBitmap(export.width, export.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                val view = app.inkloom.ui.InkCanvasView(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    view.layout(0, 0, live.width, live.height)
                    view.pagePaperColor = Color.WHITE
                    view.pagePaperPattern = PaperPattern.RULED
                    view.pagePaperDensity = density
                    val canvas = android.graphics.Canvas(live)
                    canvas.drawColor(Color.WHITE)
                    view.draw(canvas)
                }
                val expectedY = (12f * density.spacingScale).toInt()
                assertNotEquals(Color.WHITE, export.getPixel(200, expectedY))
                org.junit.Assert.assertEquals(export.getPixel(200, expectedY), live.getPixel(200, expectedY))
                val large = assets.renderPageBitmap(page, 800)
                try { assertNotEquals(Color.WHITE, large.getPixel(400, expectedY * 2)) } finally { large.recycle() }
            } finally { export.recycle(); live.recycle() }
        }
    }

    @Test
    fun ruledTemplateLinesAreIncludedInRenderedExport() {
        val bitmap = assets.renderPageBitmap(InkPage(background = PageBackground.Paper(pattern = PaperPattern.RULED)), 400)
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel(200, 36))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun cornellTemplateGuidesAreIncludedInRenderedExport() {
        val bitmap = assets.renderPageBitmap(InkPage(background = PageBackground.Paper(pattern = PaperPattern.CORNELL)), 400)
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel((bitmap.width * .28f).toInt(), 100))
            assertNotEquals(Color.WHITE, bitmap.getPixel(20, (bitmap.height * .78f).toInt()))
        } finally {
            bitmap.recycle()
        }
    }
}
