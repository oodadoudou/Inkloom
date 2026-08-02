package app.inkloom

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageElement
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkObjectExportTest {
    @Test fun flattenedPageExportRendersTheLinkCard() {
        val service = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val page = InkPage(
            width = 1_000f,
            height = 1_000f,
            elements = listOf(PageElement.Link(x = .1f, y = .1f, width = 400f, height = 180f, title = "Inkloom guide", url = "https://example.com/guide")),
        )

        val bitmap = service.renderPageBitmap(page, 1_000)

        assertNotEquals(Color.WHITE, bitmap.getPixel(120, 120))
        bitmap.recycle()
    }
}
