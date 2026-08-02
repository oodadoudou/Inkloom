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
