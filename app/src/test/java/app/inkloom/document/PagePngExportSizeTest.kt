package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PagePngExportSizeTest {
    @Test fun originalSizeFollowsTheVisiblePagePixels() {
        assertEquals(PagePngExportSize(1600, 2263), PagePngExportSize.from(InkPage()))
        assertEquals(PagePngExportSize(2400, 1800), PagePngExportSize.from(InkPage(width = 2400f, height = 1800f)))
        assertEquals(
            PagePngExportSize(800, 600),
            PagePngExportSize.from(InkPage(width = 1600f, height = 1200f, crop = PageCrop(.25f, .25f, .75f, .75f))),
        )
    }

    @Test fun native8kIsAcceptedAndLargerCanvasStillRejected() {
        assertEquals(PagePngExportSize(8192, 8192), PagePngExportSize.from(InkPage(width = 8192f, height = 8192f)))
        assertThrows(IllegalArgumentException::class.java) {
            PagePngExportSize.from(InkPage(width = 8193f, height = 8000f))
        }
    }
}
