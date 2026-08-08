package app.inkloom

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.HandwritingIndex
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.PdfTextBounds
import app.inkloom.document.RecognizedTextBlock
import app.inkloom.document.RecognizedTextSource
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchablePdfExportTest {
    @Test
    fun recognizedTextRemainsSearchableAfterPdfExport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val page = InkPage(
            recognizedTextBlocks = listOf(
                RecognizedTextBlock(
                    text = "Inkloom searchable handwriting",
                    bounds = PdfTextBounds(.1f, .1f, .8f, .2f),
                    source = RecognizedTextSource.HANDWRITING,
                ),
            ),
            handwritingIndexFingerprint = HandwritingIndex.fingerprint(emptyList()),
        )
        val output = File(context.cacheDir, "searchable-export-${System.nanoTime()}.pdf")
        try {
            output.outputStream().use { DocumentAssetService(context).exportPdf(InkDocument(title = "Search test", pages = listOf(page)), it) }
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { exportedPage ->
                        val text = exportedPage.textContents.joinToString(" ") { it.text }
                        assertTrue("Exported PDF text was: '$text'", text.contains("Inkloom searchable handwriting", ignoreCase = true))
                    }
                }
            }
        } finally {
            output.delete()
        }
    }
}
