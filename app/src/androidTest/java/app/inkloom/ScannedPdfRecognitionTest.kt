package app.inkloom

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ScannedPdfRecognitionTest {
    @Test fun scannedPdfBackgroundProducesSearchableOcrText() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"scanned-recognition-test.pdf")
        val image=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(image); canvas.drawColor(Color.WHITE)
            canvas.drawText("SCANNED SEARCH 123",90f,280f,Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=68f; color=Color.BLACK; typeface=Typeface.DEFAULT_BOLD })
            val pdf=PdfDocument()
            try {
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(600,400,1).create())
                page.canvas.drawBitmap(image,null,Rect(0,0,600,400),Paint(Paint.FILTER_BITMAP_FLAG)); pdf.finishPage(page)
                file.outputStream().use(pdf::writeTo)
            } finally { pdf.close() }
            val assets=DocumentAssetService(context)
            val page=assets.importPdf(Uri.fromFile(file),"Scanned",indexText=false).pages.single()
            val decoded=assets.renderBackgroundForOcr(page)
            val blocks=try { withTimeout(45_000) { OnDeviceTextRecognitionService(context).recognizeBitmap(decoded,"background","en-US") } } finally { decoded.recycle() }
            assertTrue(blocks.any { it.text.contains("SCANNED",true) })
            val indexed=page.copy(recognizedTextBlocks=blocks,imageOcrFingerprint=ImageOcrIndex.fingerprint(page))
            assertEquals(PageSearchSource.IMAGE_OCR,PageSearch.find(indexed,"SEARCH")?.source)
        } finally { image.recycle(); file.delete() }
    }
    @Test fun utf8TextAppendPreservesEditableTextAndSequence() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"append-text-test.md")
        try {
            val body=(1..240).joinToString("\n") { "Line $it · 中文漫画笔记" }; file.writeText(body)
            val pages=DocumentAssetService(context).importAdditionalPages(Uri.fromFile(file),file.name)
            assertTrue(pages.size>1)
            assertEquals(body,pages.joinToString("") { (it.elements.single() as PageElement.Text).text })
            assertTrue(pages.all { it.background is PageBackground.Paper })
        } finally { file.delete() }
    }
}
