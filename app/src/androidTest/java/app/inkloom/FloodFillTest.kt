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

    @Test fun closeGapTreatsASmallBreakInLineArtAsClosed() {
        val source = solid(Color.WHITE)
        for (x in 5..14) { source.setPixel(x, 5, Color.BLACK); source.setPixel(x, 14, Color.BLACK) }
        for (y in 5..14) { source.setPixel(5, y, Color.BLACK); source.setPixel(14, y, Color.BLACK) }
        source.setPixel(10, 5, Color.WHITE)

        val open = FloodFill.fill(source, 10, 10, tolerance = 0)
        val closed = FloodFill.fill(source, 10, 10, tolerance = 0, closeGapPixels = 1)

        assertEquals(20, open.width)
        assertTrue("Closing the one-pixel break must keep fill inside the box", closed.width < 12)
    }

    @Test fun areaScalingCanOverfillOrUnderfillTheDetectedRegion() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) source.setPixel(x, y, if (x in 5..14 && y in 5..14) Color.WHITE else Color.BLACK)

        val normal = FloodFill.fill(source, 10, 10, tolerance = 0)
        val expanded = FloodFill.fill(source, 10, 10, tolerance = 0, areaScalingPixels = 2)
        val shrunk = FloodFill.fill(source, 10, 10, tolerance = 0, areaScalingPixels = -2)

        assertEquals(10, normal.width)
        assertTrue(expanded.width > normal.width)
        assertTrue(shrunk.width < normal.width)
    }

    @Test fun seedInsideAndOutsideAClosedBoundaryFillOppositeConnectedRegions() {
        val source = solid(Color.WHITE, 30, 30)
        for (x in 8..21) { source.setPixel(x, 8, Color.BLACK); source.setPixel(x, 21, Color.BLACK) }
        for (y in 8..21) { source.setPixel(8, y, Color.BLACK); source.setPixel(21, y, Color.BLACK) }

        val inside = FloodFill.fill(source, 14, 14, tolerance = 0)
        val outside = FloodFill.fill(source, 2, 2, tolerance = 0)

        assertTrue("Inside seed must stay within the outline", inside.left > 8 && inside.right < 21)
        assertEquals("Outside seed must cover the connected exterior bounds", 30, outside.width)
        assertEquals(0, Color.alpha(outside.mask.getPixel(14, 14)))
        assertTrue(Color.alpha(outside.mask.getPixel(2, 2)) > 0)
    }

    @Test fun toleranceComparesUnpremultipliedChannelsForTranslucentArtwork() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.argb(128, 200, 100, 50))
            setPixel(1, 0, Color.argb(128, 210, 105, 55))
            setPixel(2, 0, Color.argb(128, 230, 100, 50))
        }
        val result = FloodFill.fill(source, 0, 0, tolerance = 10)
        try {
            assertEquals(2, result.width)
            assertTrue(Color.alpha(result.mask.getPixel(1, 0)) > 0)
        } finally {
            result.mask.recycle()
            source.recycle()
        }
    }

    @Test fun repeatedViewportFillThresholdUpdatesStayInteractive() {
        val source = solid(Color.WHITE, 800, 600)
        for (y in 80 until 520) source.setPixel(400, y, Color.BLACK)
        repeat(2) { FloodFill.fill(source, 100, 300, tolerance = it * 4).mask.recycle() }
        val elapsed = ArrayList<Long>(12)
        repeat(12) { iteration ->
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            FloodFill.fill(source, 100, 300, tolerance = iteration * 2).mask.recycle()
            elapsed += android.os.SystemClock.elapsedRealtimeNanos() - started
        }
        val sorted = elapsed.sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "native_flood_fill_p95_ms=$p95Ms")
        assertTrue("Native fill threshold P95 was ${p95Ms}ms", p95Ms < 33.3f)
        source.recycle()
    }
}
