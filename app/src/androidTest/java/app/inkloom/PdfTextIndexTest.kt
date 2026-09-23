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
import java.util.concurrent.CancellationException

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

    @Test fun longPdfReportsProgressAndCanCancelBeforePublishing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "long-${System.nanoTime()}.pdf")
        val pdf = PdfDocument()
        val pageCount = 512
        repeat(pageCount) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
            pdf.finishPage(page)
        }
        source.outputStream().use(pdf::writeTo)
        pdf.close()
        try {
            val service = DocumentAssetService(context)
            var completed = 0
            val failure = runCatching { service.importPdf(Uri.fromFile(source), "long.pdf", indexText = false,
                onProgress = { count, total -> assertEquals(pageCount, total); completed = count },
                checkCancelled = { if (completed >= 25) throw CancellationException("cancel test") }) }
            assertTrue(failure.exceptionOrNull() is CancellationException)
            assertEquals(25, completed)
            val imported = service.importPdf(Uri.fromFile(source), "long.pdf", indexText = false,
                onProgress = { count, total -> assertEquals(pageCount, total); completed = count })
            assertEquals(pageCount, imported.pages.size)
            assertEquals(pageCount, completed)
        } finally { source.delete() }
    }
}
