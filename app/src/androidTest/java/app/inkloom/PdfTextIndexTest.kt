package app.inkloom

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.PageSearch
import app.inkloom.document.PageSearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfTextIndexTest {
    @Test
    fun importAndLegacyReindexExtractNativePdfTextOnAndroid15() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "native-text-${System.nanoTime()}.pdf")
        createTextPdf(source, "Inkloom searchable transformer notes")
        try {
            val service = DocumentAssetService(context)
            val imported = service.importPdf(Uri.fromFile(source), "lecture.pdf")
            val importedPage = imported.pages.single()

            assertEquals(1, importedPage.pdfTextIndexVersion)
            assertTrue(importedPage.pdfTextBlocks.any { it.text.contains("searchable", ignoreCase = true) })
            val hit = PageSearch.find(importedPage, "transformer")
            assertEquals(PageSearchSource.PDF, hit?.source)
            assertNotNull(hit?.bounds)

            val legacy = imported.copy(pages = listOf(importedPage.copy(pdfTextBlocks = emptyList(), pdfTextIndexVersion = 0)))
            val reindexed = service.indexPdfText(legacy).pages.single()
            assertEquals(1, reindexed.pdfTextIndexVersion)
            assertTrue(reindexed.pdfTextBlocks.any { it.text.contains("Inkloom", ignoreCase = true) })
        } finally {
            source.delete()
        }
    }

    private fun createTextPdf(target: File, text: String) {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
        page.canvas.drawText(text, 80f, 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f })
        pdf.finishPage(page)
        target.outputStream().use(pdf::writeTo)
        pdf.close()
    }
}
