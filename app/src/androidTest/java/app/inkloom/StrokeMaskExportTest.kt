package app.inkloom

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeMaskExportTest {
    @Test fun flattenedExportClearsOnlyThePersistedMaskArea() {
        val service = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val stroke = InkStroke(
            id = "masked",
            width = 100f,
            colorArgb = 0xFF000000,
            points = listOf(StrokePoint(.2f, .5f), StrokePoint(.8f, .5f)),
            masks = listOf(StrokeMaskDab(.5f, .5f, .08f, .08f)),
        )
        val page = InkPage(width = 1_000f, height = 1_000f, background = PageBackground.Paper(0xFFFFFFFF), strokes = listOf(stroke))

        val bitmap = service.renderPageBitmap(page, 1_000)
        try {
            assertEquals(Color.BLACK, bitmap.getPixel(300, 500))
            assertEquals(Color.WHITE, bitmap.getPixel(500, 500))
            assertEquals(Color.BLACK, bitmap.getPixel(700, 500))
        } finally {
            bitmap.recycle()
        }
    }
}
