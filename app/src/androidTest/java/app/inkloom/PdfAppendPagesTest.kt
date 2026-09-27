package app.inkloom

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.AppendImportedPages
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.PageBackground
import app.inkloom.document.PageSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfAppendPagesTest {
    @Test fun appendsPdfAndImageWithoutReplacingExistingAnnotationAndCanSearchNewPdf() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdfFile = File(context.cacheDir, "append-${System.nanoTime()}.pdf")
        val imageFile = File(context.cacheDir, "append-${System.nanoTime()}.png")
        val pdf = PdfDocument()
        repeat(2) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
            page.canvas.drawText("appendable PDF lesson ${index + 1}", 80f, 140f, Paint().apply { textSize = 24f })
            pdf.finishPage(page)
        }
        pdfFile.outputStream().use(pdf::writeTo)
        pdf.close()
        val image = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        try { imageFile.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
        try {
            val assets = DocumentAssetService(context)
            val original = InkPage(title = "Existing annotation", bookmarked = true)
            val source = InkDocument(title = "Reading", pages = listOf(original))
            val prepared = assets.importAdditionalPages(Uri.fromFile(pdfFile), "lesson.pdf") +
                assets.importAdditionalPages(Uri.fromFile(imageFile), "figure.png")
            val appended = AppendImportedPages.apply(source, prepared)
            assertEquals(4, appended.pages.size)
            assertEquals(original, appended.pages.first())
            assertEquals(listOf("lesson · 1", "lesson · 2", "figure"), appended.pages.drop(1).map(InkPage::title))
            assertTrue(appended.pages[1].background is PageBackground.Pdf)
            assertTrue(appended.pages.last().background is PageBackground.Image)
            assertNotNull(PageSearch.find(appended.pages[1], "appendable"))
        } finally {
            pdfFile.delete()
            imageFile.delete()
        }
    }

}
