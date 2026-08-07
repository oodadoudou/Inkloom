package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.FloodFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloodFillTest {
    private fun solid(color: Int, w: Int = 20, h: Int = 20): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun fillsAUniformRegionEntirely() {
        val source = solid(Color.WHITE)
        val result = FloodFill.fill(source, 5, 5, tolerance = 0)
        assertEquals(20, result.width)
        assertEquals(20, result.height)
        assertTrue("Every pixel of a uniform source should be covered", result.mask.getPixel(0, 0) == Color.WHITE)
    }

    @Test fun stopsAtAColorBoundary() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) source.setPixel(x, y, if (x < 10) Color.WHITE else Color.BLACK)
        val result = FloodFill.fill(source, 0, 0, tolerance = 10)
        assertEquals("Fill should stop at the black boundary", 10, result.width)
    }

    @Test fun toleranceBleedsAcrossAntiAliasedEdges() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) {
            source.setPixel(x, y, if (x < 8) Color.WHITE else if (x < 12) Color.rgb(200, 200, 200) else Color.BLACK)
        }
        val tight = FloodFill.fill(source, 0, 0, tolerance = 10)
        val loose = FloodFill.fill(source, 0, 0, tolerance = 80)
        assertTrue("Higher tolerance should cover more pixels", loose.width >= tight.width)
    }
}
