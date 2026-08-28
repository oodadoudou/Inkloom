package app.inkloom

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkLayer
import app.inkloom.document.LayerContentKind
import app.inkloom.document.LayerIds
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfPageThumbnailTest {
    @Test
    fun pageThumbnailCompositesOriginalPdfAndVisibleInk() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "thumbnail-${System.nanoTime()}.pdf")
        val pdf = PdfDocument()
        val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, 1).create())
        pdfPage.canvas.drawColor(Color.WHITE)
        pdfPage.canvas.drawRect(30f, 30f, 180f, 180f, Paint().apply { color = Color.RED })
        pdf.finishPage(pdfPage)
        source.outputStream().use(pdf::writeTo)
        pdf.close()

        val assets = DocumentAssetService(context)
        val ink = PixelTileSurface(400, 600).also { surface ->
            surface.beginWrite("thumbnail ink").use { transaction ->
                for (y in 468..492) for (x in 80..320) transaction.setPixel(x, y, Color.BLUE)
                transaction.commit().close()
            }
        }
        val reference = assets.saveRasterTileSurface(ink)
        val rendered = assets.renderPageBitmap(
            InkPage(
                width = 400f,
                height = 600f,
                background = PageBackground.Pdf(source.absolutePath, 0),
                layers = listOf(
                    InkLayer(LayerIds.BACKGROUND, "Background", LayerRole.BACKGROUND, locked = true),
                    InkLayer(
                        id = LayerIds.INK,
                        name = "Notes",
                        role = LayerRole.INK,
                        contentKind = LayerContentKind.RASTER,
                        rasterTileSurface = reference,
                    ),
                ),
            ),
            200,
        )
        try {
            val pdfPixel = rendered.getPixel(50, 50)
            assertTrue("PDF artwork must remain in the page preview", Color.red(pdfPixel) > 180 && Color.green(pdfPixel) < 100)
            assertEquals("Ink must be composited over the PDF preview", Color.BLUE, rendered.getPixel(100, 240))
        } finally {
            rendered.recycle()
            assets.releaseRasterTileReservation(reference)
            source.delete()
        }
    }
}
