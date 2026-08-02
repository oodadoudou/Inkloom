package app.inkloom

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageElement
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickyInkExportTest {
    @Test
    fun expandedStickyInkRendersAndCollapsedStickyHidesItsSubcanvas() {
        val service = DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)
        val stroke = InkStroke(
            id = "sticky-ink",
            points = listOf(StrokePoint(.2f, .5f), StrokePoint(.8f, .5f)),
            colorArgb = 0xFF111111,
            width = 120f,
            sizePressureEnabled = false,
        )
        val sticky = PageElement.Sticky(
            id = "sticky",
            x = .1f,
            y = .1f,
            width = 800f,
            height = 800f,
            text = "Ink",
            colorArgb = 0xFFFFFFFF,
            strokes = listOf(stroke),
        )
        val expandedPage = InkPage(width = 1000f, height = 1000f, elements = listOf(sticky))
        val collapsedPage = expandedPage.copy(elements = listOf(sticky.copy(collapsed = true)))

        val expanded = service.renderPageBitmap(expandedPage, 1000)
        val collapsed = service.renderPageBitmap(collapsedPage, 1000)
        try {
            val inkPixel = expanded.getPixel(500, 500)
            val hiddenPixel = collapsed.getPixel(500, 500)
            assertTrue(Color.red(inkPixel) < 80 && Color.green(inkPixel) < 80 && Color.blue(inkPixel) < 80)
            assertTrue(Color.red(hiddenPixel) > 220 && Color.green(hiddenPixel) > 220 && Color.blue(hiddenPixel) > 220)
        } finally {
            expanded.recycle()
            collapsed.recycle()
        }
    }
}
