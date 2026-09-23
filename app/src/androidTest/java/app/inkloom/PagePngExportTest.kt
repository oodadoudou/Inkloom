package app.inkloom

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageCrop
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PagePngExportTest {
    @Test fun exportedPngUsesTheVisibleCanvasPixelDimensions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "page-original-size-test.png")
        try {
            val page = InkPage(width = 2400f, height = 1800f, crop = PageCrop(.25f, .25f, .75f, .75f))
            DocumentAssetService(context).exportPagePng(page, Uri.fromFile(file))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                assertEquals(1200, bitmap.width)
                assertEquals(900, bitmap.height)
            } finally { bitmap.recycle() }
        } finally { file.delete() }
    }
}
